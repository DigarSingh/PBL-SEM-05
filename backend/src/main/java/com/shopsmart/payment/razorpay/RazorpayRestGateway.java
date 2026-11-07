package com.shopsmart.payment.razorpay;

import com.shopsmart.config.AppProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay REST API client (https://razorpay.com/docs/api/) using HTTP basic auth with the
 * key id / key secret. The secret never leaves the server.
 */
@Component
public class RazorpayRestGateway implements RazorpayGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayRestGateway.class);

    private final AppProperties.Razorpay config;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public RazorpayRestGateway(AppProperties properties, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.config = properties.razorpay();
        this.jsonMapper = jsonMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.readTimeout());
        this.restClient = builder.clone()
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public RazorpayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode node = post("/orders", body, "create the payment order");
        return new RazorpayOrder(node.path("id").asString(), node.path("amount").asLong(),
                node.path("currency").asString(), node.path("status").asString());
    }

    @Override
    public RazorpayPayment fetchPayment(String paymentId) {
        return toPayment(get("/payments/" + paymentId, "fetch the payment"));
    }

    @Override
    public List<RazorpayPayment> fetchOrderPayments(String razorpayOrderId) {
        JsonNode node = get("/orders/" + razorpayOrderId + "/payments", "fetch the order's payments");
        List<RazorpayPayment> payments = new ArrayList<>();
        for (JsonNode item : node.path("items")) {
            payments.add(toPayment(item));
        }
        return payments;
    }

    @Override
    public RazorpayPayment capture(String paymentId, long amountPaise, String currency) {
        return toPayment(post("/payments/" + paymentId + "/capture",
                Map.of("amount", amountPaise, "currency", currency), "capture the payment"));
    }

    @Override
    public RazorpayRefund refund(String paymentId, long amountPaise, Map<String, String> notes) {
        JsonNode node = post("/payments/" + paymentId + "/refund",
                Map.of("amount", amountPaise, "speed", "normal", "notes", notes), "refund the payment");
        return new RazorpayRefund(node.path("id").asString(), node.path("payment_id").asString(),
                node.path("amount").asLong(), node.path("status").asString());
    }

    private JsonNode get(String path, String action) {
        requireConfigured();
        try {
            String body = restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .retrieve().body(String.class);
            return jsonMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException e) {
            throw translate(e, action);
        } catch (ResourceAccessException e) {
            log.warn("Razorpay unreachable while trying to {}: {}", action, e.getMessage());
            throw new PaymentGatewayException("The payment service is not reachable right now. Please try again.");
        }
    }

    private JsonNode post(String path, Object payload, String action) {
        requireConfigured();
        try {
            String body = restClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_JSON).body(payload)
                    .retrieve().body(String.class);
            return jsonMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException e) {
            throw translate(e, action);
        } catch (ResourceAccessException e) {
            log.warn("Razorpay unreachable while trying to {}: {}", action, e.getMessage());
            throw new PaymentGatewayException("The payment service is not reachable right now. Please try again.");
        }
    }

    private PaymentGatewayException translate(RestClientResponseException e, String action) {
        String description = null;
        try {
            description = jsonMapper.readTree(e.getResponseBodyAsString()).path("error").path("description")
                    .asString(null);
        } catch (RuntimeException ignored) {
            // Non-JSON error body; fall back to the status code below.
        }
        log.warn("Razorpay returned {} while trying to {}: {}", e.getStatusCode().value(), action, description);
        if (e.getStatusCode().value() == 401) {
            return new PaymentGatewayException(HttpStatus.UNAUTHORIZED,
                    "Online payments are misconfigured. Please contact support.");
        }
        return new PaymentGatewayException("Could not " + action + (description == null ? "" : ": " + description));
    }

    private void requireConfigured() {
        if (!config.configured()) {
            throw new PaymentGatewayException("Online payments are not configured");
        }
    }

    private String basicAuth() {
        String credentials = config.keyId() + ":" + config.keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static RazorpayPayment toPayment(JsonNode node) {
        return new RazorpayPayment(node.path("id").asString(), node.path("order_id").asString(null),
                node.path("status").asString(), node.path("amount").asLong(), node.path("currency").asString(),
                node.path("method").asString(null), node.path("error_code").asString(null),
                node.path("error_description").asString(null));
    }
}
