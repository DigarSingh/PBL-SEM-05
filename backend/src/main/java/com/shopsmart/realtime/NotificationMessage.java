package com.shopsmart.realtime;

import java.time.Instant;

public record NotificationMessage(Long id, String type, String title, String message, String link, Instant createdAt) {
}
