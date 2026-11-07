package com.shopsmart.notification;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.notification.dto.NotificationResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "In-app notifications (also pushed over WebSocket)")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "My notifications (newest first)")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(@CurrentUser AuthUser user,
                                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(service.list(user.id(), page, size));
    }

    @Operation(summary = "Number of unread notifications")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@CurrentUser AuthUser user) {
        return ApiResponse.ok(Map.of("count", service.unreadCount(user.id())));
    }

    @Operation(summary = "Mark a notification as read")
    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@CurrentUser AuthUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.markRead(user.id(), id));
    }

    @Operation(summary = "Mark all notifications as read")
    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(@CurrentUser AuthUser user) {
        return ApiResponse.ok(Map.of("updated", service.markAllRead(user.id())));
    }
}
