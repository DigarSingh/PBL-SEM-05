package com.shopsmart.notification.email;

/** Transport abstraction so SMTP can be swapped (e.g. for an e-mail API) without touching callers. */
public interface EmailSender {

    void send(EmailMessage message) throws Exception;
}
