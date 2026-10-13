package com.example.notification.service;

import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled worker that picks up RETRYING events whose nextRetryAt time has passed
 * and dispatches them for delivery.
 *
 * This replaces the previous Thread.sleep() approach — worker threads are never
 * occupied waiting for a delay.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RetryScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;

    /**
     * Runs every 10 seconds to pick up events due for retry.
     * Fixed-delay ensures the previous run completes before the next starts.
     */
    @Scheduled(fixedDelayString = "${hookflow.retry.scheduler-interval-ms:10000}")
    public void processRetries() {
        List<Long> dueEventIds = findDueEventIds();
        if (dueEventIds.isEmpty()) return;

        log.info("RetryScheduler: {} event(s) due for retry", dueEventIds.size());
        for (Long eventId : dueEventIds) {
            try {
                notificationProcessor.processAsync(eventId);
            } catch (Exception e) {
                log.error("RetryScheduler: failed to dispatch event {}: {}", eventId, e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    protected List<Long> findDueEventIds() {
        return notificationRepository
                .findDueForRetry(NotificationStatus.RETRYING, LocalDateTime.now())
                .stream()
                .map(n -> n.getId())
                .toList();
    }
}
