package com.shopsmart.notification.dto;

import com.shopsmart.notification.NotificationType;
import java.time.Instant;

public record NotificationResponse(Long id, NotificationType type, String title, String message, String link,
                                   boolean read, Instant createdAt) {
}
