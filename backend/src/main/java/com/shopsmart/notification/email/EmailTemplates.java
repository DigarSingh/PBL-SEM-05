package com.shopsmart.notification.email;

import com.shopsmart.config.AppProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders transactional e-mails. All interpolated values are HTML-escaped; layout uses inline
 * styles because most e-mail clients ignore stylesheets.
 */
@Component
public class EmailTemplates {

    private final String frontendUrl;
    private final String storeName;

    public EmailTemplates(AppProperties properties) {
        this.frontendUrl = properties.frontendUrl().replaceAll("/+$", "");
        this.storeName = properties.mail().fromName();
    }

    public EmailMessage emailVerification(String to, String name, String token) {
        String link = frontendUrl + "/verify-email?token=" + encode(token);
        return build(to, "Verify your " + storeName + " account",
                "Hi " + name + ",",
                List.of("Thanks for creating a " + storeName + " account. Please confirm your email address to "
                        + "start shopping. This link expires in 24 hours."),
                "Verify email", link,
                "If you did not create an account you can ignore this email.");
    }

    public EmailMessage welcome(String to, String name) {
        return build(to, "Welcome to " + storeName,
                "Welcome, " + name + "!",
                List.of("Your email address is verified and your account is ready.",
                        "Browse the latest gadgets, save favourites to your wishlist and track orders in real time."),
                "Start shopping", frontendUrl + "/shop", null);
    }

    public EmailMessage passwordReset(String to, String name, String token) {
        String link = frontendUrl + "/reset-password?token=" + encode(token);
        return build(to, "Reset your " + storeName + " password",
                "Hi " + name + ",",
                List.of("We received a request to reset your password. This link expires in 30 minutes and can be "
                        + "used once."),
                "Reset password", link,
                "If you did not request a password reset, you can safely ignore this email. Your password will not "
                        + "change.");
    }

    public EmailMessage orderEmail(String to, String subject, String heading, List<String> paragraphs,
                                   String orderId) {
        return build(to, subject, heading, paragraphs, "View order", frontendUrl + "/orders/" + orderId, null);
    }

    private EmailMessage build(String to, String subject, String heading, List<String> paragraphs,
                               String actionLabel, String actionUrl, String footnote) {
        StringBuilder html = new StringBuilder()
                .append("<!doctype html><html><body style=\"margin:0;background:#f4f5f7;font-family:Segoe UI,"
                        + "Helvetica,Arial,sans-serif;color:#1f2937\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                        + "<tr><td align=\"center\" style=\"padding:32px 16px\">")
                .append("<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" "
                        + "style=\"max-width:560px;background:#ffffff;border-radius:12px;padding:32px\">")
                .append("<tr><td style=\"font-size:20px;font-weight:700;color:#4f46e5;padding-bottom:24px\">")
                .append(escape(storeName)).append("</td></tr>")
                .append("<tr><td style=\"font-size:18px;font-weight:600;padding-bottom:12px\">")
                .append(escape(heading)).append("</td></tr>");
        StringBuilder text = new StringBuilder(heading).append("\n\n");
        for (String paragraph : paragraphs) {
            html.append("<tr><td style=\"font-size:15px;line-height:1.6;padding-bottom:12px\">")
                    .append(escape(paragraph)).append("</td></tr>");
            text.append(paragraph).append("\n\n");
        }
        if (actionUrl != null) {
            html.append("<tr><td style=\"padding:12px 0 20px\"><a href=\"").append(escape(actionUrl))
                    .append("\" style=\"background:#4f46e5;color:#ffffff;text-decoration:none;padding:12px 22px;"
                            + "border-radius:8px;font-weight:600;display:inline-block\">")
                    .append(escape(actionLabel)).append("</a></td></tr>");
            text.append(actionLabel).append(": ").append(actionUrl).append("\n\n");
        }
        if (footnote != null) {
            html.append("<tr><td style=\"font-size:13px;color:#6b7280;line-height:1.5\">")
                    .append(escape(footnote)).append("</td></tr>");
            text.append(footnote).append("\n");
        }
        html.append("</table></td></tr></table></body></html>");
        return new EmailMessage(to, subject, html.toString(), text.toString());
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
