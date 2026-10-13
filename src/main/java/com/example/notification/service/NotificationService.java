package com.example.notification.service;

import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;

    @Transactional
    public Notification createNotification(Notification notification) {
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRetryCount(0);
        Notification saved = notificationRepository.save(notification);
        
        // Hand off to the async processor
        notificationProcessor.processAsync(saved);
        
        return saved;
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }
}
