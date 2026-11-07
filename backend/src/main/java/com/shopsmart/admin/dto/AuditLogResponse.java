package com.shopsmart.admin.dto;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorEmail,
        String action,
        String entityType,
        String entityId,
        String details,
        String ipAddress,
        Instant createdAt) {
}
