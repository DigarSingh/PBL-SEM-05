package com.shopsmart.notification.email;

import com.shopsmart.common.util.Emails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends e-mail best-effort. Delivery problems are logged and never propagated: an SMTP outage
 * must not fail registration, checkout or any other business operation.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSender sender;

    public EmailService(EmailSender sender) {
        this.sender = sender;
    }

    public void send(EmailMessage message) {
        try {
            sender.send(message);
            log.debug("Sent e-mail '{}' to {}", message.subject(), Emails.mask(message.to()));
        } catch (Exception e) {
            log.error("Failed to send e-mail '{}' to {}: {}", message.subject(), Emails.mask(message.to()),
                    e.getMessage());
        }
    }
}
