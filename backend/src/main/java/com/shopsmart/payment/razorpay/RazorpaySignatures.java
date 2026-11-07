package com.shopsmart.payment.razorpay;

import com.shopsmart.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Razorpay signature checks (HMAC-SHA256, hex encoded), compared in constant time:
 * checkout: HMAC(order_id + "|" + payment_id, key_secret); webhooks: HMAC(raw body, webhook_secret).
 */
@Component
public class RazorpaySignatures {

    private final AppProperties.Razorpay config;

    public RazorpaySignatures(AppProperties properties) {
        this.config = properties.razorpay();
    }

    public boolean isValidPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String signature) {
        return StringUtils.hasText(razorpayOrderId) && StringUtils.hasText(razorpayPaymentId)
                && matches(razorpayOrderId + "|" + razorpayPaymentId, signature, config.keySecret());
    }

    public boolean isValidWebhookSignature(String rawBody, String signature) {
        return rawBody != null && matches(rawBody, signature, config.webhookSecret());
    }

    public static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static boolean matches(String payload, String signature, String secret) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(secret)) {
            return false;
        }
        byte[] expected = hmacSha256Hex(payload, secret).getBytes(StandardCharsets.US_ASCII);
        byte[] provided = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, provided);
    }
}
