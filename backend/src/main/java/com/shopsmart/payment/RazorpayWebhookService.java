package com.shopsmart.payment;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.Money;
import com.shopsmart.common.util.SecureTokens;
import com.shopsmart.config.AppProperties;
import com.shopsmart.payment.razorpay.RazorpaySignatures;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Processes Razorpay webhooks (https://razorpay.com/docs/webhooks/). Deliveries are
 * authenticated by signature and de-duplicated by event id, so redeliveries are harmless.
 */
@Service
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

    private final RazorpaySignatures signatures;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository eventRepository;
    private final PaymentProcessor processor;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate tx;
    private final AppProperties properties;
    private final Clock clock;

    public RazorpayWebhookService(RazorpaySignatures signatures, PaymentRepository paymentRepository,
                                  PaymentEventRepository eventRepository, PaymentProcessor processor,
                                  JsonMapper jsonMapper, TransactionTemplate tx, AppProperties properties,
                                  Clock clock) {
        this.signatures = signatures;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.processor = processor;
        this.jsonMapper = jsonMapper;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    public String handle(String rawBody, String signature, String eventIdHeader) {
        if (!properties.razorpay().webhookConfigured()) {
            log.error("Razorpay webhook received but RAZORPAY_WEBHOOK_SECRET is not configured");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PAYMENT_UNAVAILABLE,
                    "Webhooks are not configured");
        }
        if (!signatures.isValidWebhookSignature(rawBody, signature)) {
            log.warn("Rejected Razorpay webhook with an invalid signature");
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_VERIFICATION_FAILED,
                    "Invalid webhook signature");
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(rawBody);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Malformed webhook payload");
        }
        String eventType = root.path("event").asString("");
        String eventId = StringUtils.hasText(eventIdHeader) ? eventIdHeader : "sha256:" + SecureTokens.sha256(rawBody);
        if (eventRepository.existsByProviderAndEventId(PaymentEvent.PROVIDER_RAZORPAY, eventId)) {
            return "DUPLICATE";
        }
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String razorpayPaymentId = paymentEntity.path("id").asString(null);
        String razorpayOrderId = paymentEntity.path("order_id").asString(
                root.path("payload").path("order").path("entity").path("id").asString(null));
        try {
            return tx.execute(status -> {
                String outcome = process(eventType, root, paymentEntity, razorpayOrderId, razorpayPaymentId);
                PaymentEvent event = new PaymentEvent();
                event.setProvider(PaymentEvent.PROVIDER_RAZORPAY);
                event.setEventId(eventId);
                event.setEventType(eventType.length() > 60 ? eventType.substring(0, 60) : eventType);
                event.setRazorpayOrderId(razorpayOrderId);
                event.setRazorpayPaymentId(razorpayPaymentId);
                event.setOutcome(outcome);
                event.setReceivedAt(clock.instant());
                eventRepository.saveAndFlush(event);
                return outcome;
            });
        } catch (DataIntegrityViolationException e) {
            // A concurrent delivery of the same event won the race; its processing stands.
            return "DUPLICATE";
        }
    }

    private String process(String eventType, JsonNode root, JsonNode paymentEntity, String razorpayOrderId,
                           String razorpayPaymentId) {
        switch (eventType) {
            case "payment.captured", "order.paid", "payment.authorized", "payment.failed" -> {
                Optional<Payment> payment = razorpayOrderId == null ? Optional.empty()
                        : paymentRepository.lockByRazorpayOrderId(razorpayOrderId);
                if (payment.isEmpty()) {
                    log.info("Webhook {} for unknown Razorpay order {}", eventType, razorpayOrderId);
                    return "UNKNOWN_ORDER";
                }
                long amount = paymentEntity.path("amount").asLong(-1);
                if (!"payment.failed".equals(eventType) && amount >= 0
                        && amount != Money.toMinorUnits(payment.get().getAmount())) {
                    log.error("Webhook amount {} does not match payment {} amount", amount, payment.get().getId());
                    return "AMOUNT_MISMATCH";
                }
                PaymentStatus status = switch (eventType) {
                    case "payment.failed" -> PaymentStatus.FAILED;
                    case "payment.authorized" -> PaymentStatus.AUTHORIZED;
                    default -> PaymentStatus.PAID;
                };
                return processor.apply(payment.get(), status, razorpayPaymentId,
                        paymentEntity.path("method").asString(null),
                        paymentEntity.path("error_description").asString(null)).name();
            }
            case "refund.processed" -> {
                String paymentId = root.path("payload").path("refund").path("entity").path("payment_id").asString(null);
                log.info("Refund processed for Razorpay payment {}", paymentId);
                return "ACKNOWLEDGED";
            }
            case "refund.failed" -> {
                String paymentId = root.path("payload").path("refund").path("entity").path("payment_id").asString(null);
                log.error("Razorpay reported a failed refund for payment {}", paymentId);
                return "ACKNOWLEDGED";
            }
            default -> {
                return "IGNORED";
            }
        }
    }
}
