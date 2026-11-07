package com.shopsmart.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Best-effort push of domain changes to connected clients (clients also poll as a fallback). */
@Component
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate messaging;

    public RealtimePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void orderUpdated(Long customerId, OrderUpdateMessage message) {
        send(() -> {
            messaging.convertAndSend("/topic/orders/" + message.orderId(), message);
            messaging.convertAndSendToUser(String.valueOf(customerId), "/queue/orders", message);
            messaging.convertAndSend("/topic/admin/orders", message);
        });
    }

    public void notification(Long userId, NotificationMessage message) {
        send(() -> messaging.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", message));
    }

    public void inventoryAlert(InventoryAlertMessage message) {
        send(() -> messaging.convertAndSend("/topic/admin/inventory", message));
    }

    private static void send(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Real-time push failed: {}", e.getMessage());
        }
    }
}
