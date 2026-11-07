package com.shopsmart.notification.email;

/** A rendered e-mail with HTML and plain-text alternatives. */
public record EmailMessage(String to, String subject, String html, String text) {
}
