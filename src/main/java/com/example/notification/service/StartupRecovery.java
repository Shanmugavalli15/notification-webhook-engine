package com.example.notification.service;

import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * On application startup, finds events that were left in PENDING or PROCESSING
 * state (e.g., because the app crashed mid-delivery) and re-queues them.
 *
 * Only events older than 2 minutes are considered stuck, to avoid racing with
 * events that are legitimately in-progress immediately after startup.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StartupRecovery {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;

    @PostConstruct
    @Transactional
    public void recoverStuckEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);
        List<Notification> stuck = notificationRepository.findStuckEvents(cutoff);

        if (stuck.isEmpty()) {
            log.info("StartupRecovery: no stuck events found");
            return;
        }

        log.info("StartupRecovery: recovering {} stuck event(s)", stuck.size());

        for (Notification event : stuck) {
            try {
                // Reset to PENDING so the processor can claim it
                event.setStatus(NotificationStatus.PENDING);
                notificationRepository.save(java.util.Objects.requireNonNull(event));
                notificationProcessor.processAsync(event.getId());
                log.info("StartupRecovery: re-queued event id={}", event.getId());
            } catch (Exception e) {
                log.error("StartupRecovery: failed to recover event id={}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
