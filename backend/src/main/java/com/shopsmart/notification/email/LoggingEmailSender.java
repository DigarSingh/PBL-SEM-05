package com.shopsmart.notification.email;

import com.shopsmart.common.util.Emails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no SMTP server is configured. Records that an e-mail was suppressed without
 * logging its body, because bodies contain single-use security links.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.warn("SMTP is not configured (MAIL_HOST); e-mail '{}' to {} was not sent", message.subject(),
                Emails.mask(message.to()));
    }
}
