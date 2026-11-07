package com.shopsmart.notification;

import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.notification.dto.NotificationResponse;
import com.shopsmart.realtime.NotificationMessage;
import com.shopsmart.realtime.RealtimePublisher;
import com.shopsmart.user.Role;
import com.shopsmart.user.UserRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Persistent in-app notifications, pushed over WebSocket once stored. */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final RealtimePublisher realtime;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, UserRepository userRepository,
                               RealtimePublisher realtime, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.realtime = realtime;
        this.clock = clock;
    }

    @Transactional
    public void notifyUser(Long userId, NotificationType type, String title, String message, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(truncate(title, 160));
        notification.setMessage(truncate(message, 500));
        notification.setLink(link);
        repository.saveAndFlush(notification);
        NotificationMessage push = new NotificationMessage(notification.getId(), type.name(), notification.getTitle(),
                notification.getMessage(), link, notification.getCreatedAt());
        afterCommit(() -> realtime.notification(userId, push));
    }

    @Transactional
    public void notifyAdmins(NotificationType type, String title, String message, String link) {
        List<Long> adminIds = userRepository.findActiveUserIdsWithRole(Role.ADMIN);
        adminIds.forEach(adminId -> notifyUser(adminId, type, title, message, link));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        return PageResponse.from(repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size)),
                NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, clock.instant());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
