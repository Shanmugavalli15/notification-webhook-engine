package com.example.notification.service;

import com.example.notification.model.ChannelType;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Legacy service — kept for backward compatibility with the existing dashboard.
 * New code should use {@link EventService}.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;

    /**
     * Legacy entry point used by the existing dashboard's POST /api/v1/notifications.
     */
    @Transactional
    public Notification createNotification(Notification notification) {
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRetryCount(0);
        if (notification.getChannelType() == null) {
            notification.setChannelType(ChannelType.WEBHOOK);
        }
        if (notification.getEventType() == null) {
            notification.setEventType("generic.event");
        }
        notification.setMaxAttempts(4);

        Notification saved = notificationRepository.save(java.util.Objects.requireNonNull(notification));
        notificationProcessor.processAsync(saved.getId());
        return saved;
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }
}
