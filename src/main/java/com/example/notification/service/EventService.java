package com.example.notification.service;

import com.example.notification.dto.request.SubmitEventRequest;
import com.example.notification.dto.response.DashboardStatsResponse;
import com.example.notification.dto.response.EventResponse;
import com.example.notification.model.AppUser;
import com.example.notification.model.ChannelType;
import com.example.notification.model.Integration;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.AppUserRepository;
import com.example.notification.repository.DeliveryLogRepository;
import com.example.notification.repository.IntegrationRepository;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final NotificationRepository notificationRepository;
    private final IntegrationRepository integrationRepository;
    private final AppUserRepository userRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final NotificationProcessor notificationProcessor;

    /**
     * Submit a new webhook event.
     * Persists the event first, then hands off to async worker.
     * Returns HTTP 202 only after successful persistence.
     */
    @Transactional
    public EventResponse submit(SubmitEventRequest request, Long ownerId) {
        if (request == null) {
            throw new IllegalArgumentException("SubmitEventRequest must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID must not be null");
        }

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Notification> existing = notificationRepository
                    .findByOwner_IdAndIdempotencyKey(ownerId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("[owner={}] Idempotent resubmission of key='{}', returning existing event id={}",
                        ownerId, request.getIdempotencyKey(), existing.get().getId());
                return EventResponse.from(existing.get());
            }
        }

        AppUser owner = userRepository.getReferenceById(Objects.requireNonNull(ownerId));
        Integration integration = null;

        if (request.getIntegrationId() != null) {
            integration = integrationRepository.findByIdAndOwner_Id(request.getIntegrationId(), ownerId)
                    .orElseThrow(() -> new NoSuchElementException("Integration not found"));
            if (!integration.isEnabled()) {
                throw new IllegalStateException("Integration is disabled");
            }
        }

        Notification event = Notification.builder()
                .owner(owner)
                .integration(integration)
                .eventType(request.getEventType())
                .payload(request.getPayload())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .maxAttempts(4)
                .idempotencyKey(request.getIdempotencyKey())
                // Legacy fields
                .recipient(integration != null ? integration.getDestinationUrl() : "direct")
                .channelType(ChannelType.WEBHOOK)
                .build();

        event = notificationRepository.save(Objects.requireNonNull(event));
        final Long eventId = event.getId();
        log.info("[owner={}] Event {} (type='{}') persisted with id={}", ownerId, request.getEventType(),
                request.getEventType(), eventId);

        // Hand off to async worker AFTER successful save
        notificationProcessor.processAsync(eventId);

        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listForUser(Long ownerId, NotificationStatus statusFilter,
                                            Long integrationId, Pageable pageable) {
        Page<Notification> page;
        if (statusFilter != null && integrationId != null) {
            // Combined filter — fetch all for owner with status then filter by integration
            page = notificationRepository.findByOwner_IdAndStatusOrderByCreatedAtDesc(ownerId, statusFilter, pageable);
        } else if (statusFilter != null) {
            page = notificationRepository.findByOwner_IdAndStatusOrderByCreatedAtDesc(ownerId, statusFilter, pageable);
        } else if (integrationId != null) {
            page = notificationRepository.findByOwner_IdAndIntegration_IdOrderByCreatedAtDesc(ownerId, integrationId, pageable);
        } else {
            page = notificationRepository.findByOwner_IdOrderByCreatedAtDesc(ownerId, pageable);
        }
        return page.map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public EventResponse getDetail(Long eventId, Long ownerId) {
        if (eventId == null || ownerId == null) {
            throw new NoSuchElementException("Event not found");
        }
        Notification event = notificationRepository.findByIdAndOwner_Id(eventId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Event not found"));
        // Load delivery logs
        event.getDeliveryLogs().size(); // force load
        return EventResponse.fromWithLogs(event);
    }

    /**
     * Manual retry of a FAILED event.
     * Creates a new event as a replay (does not modify the original).
     */
    @Transactional
    public EventResponse manualRetry(Long eventId, Long ownerId) {
        if (eventId == null || ownerId == null) {
            throw new NoSuchElementException("Event not found");
        }
        Notification original = notificationRepository.findByIdAndOwner_Id(eventId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Event not found"));

        if (original.getStatus() != NotificationStatus.FAILED) {
            throw new IllegalStateException("Only FAILED events can be manually retried");
        }

        AppUser owner = userRepository.getReferenceById(ownerId);

        Notification replay = Notification.builder()
                .owner(owner)
                .integration(original.getIntegration())
                .eventType(original.getEventType())
                .payload(original.getPayload())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .maxAttempts(original.getMaxAttempts())
                .recipient(original.getRecipient())
                .channelType(original.getChannelType())
                .build();

        replay = notificationRepository.save(Objects.requireNonNull(replay));
        final Long replayId = replay.getId();
        log.info("[owner={}] Manual retry of event {} → new event {}", ownerId, eventId, replayId);

        notificationProcessor.processAsync(replayId);
        return EventResponse.from(replay);
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats(Long ownerId) {
        if (ownerId == null) {
            return DashboardStatsResponse.builder()
                    .totalEvents(0)
                    .successfulDeliveries(0)
                    .failedDeliveries(0)
                    .pendingEvents(0)
                    .retryingEvents(0)
                    .successRate(0.0)
                    .build();
        }
        long total = notificationRepository.countByOwnerId(ownerId);
        @SuppressWarnings("deprecation")
        long successful = notificationRepository.countByOwnerIdAndStatus(ownerId, NotificationStatus.SUCCESSFUL)
                + notificationRepository.countByOwnerIdAndStatus(ownerId, NotificationStatus.DELIVERED);
        @SuppressWarnings("deprecation")
        long failed = notificationRepository.countByOwnerIdAndStatus(ownerId, NotificationStatus.FAILED)
                + notificationRepository.countByOwnerIdAndStatus(ownerId, NotificationStatus.FAILED_DLQ);
        long pending = notificationRepository.countByOwnerIdAndStatus(ownerId, NotificationStatus.PENDING);
        long retrying = notificationRepository.countByOwnerIdAndStatus(ownerId, NotificationStatus.RETRYING);
        Double avgDuration = deliveryLogRepository.avgDeliveryDurationForOwner(ownerId);

        double successRate = total > 0 ? (double) successful / total * 100 : 0.0;

        return DashboardStatsResponse.builder()
                .totalEvents(total)
                .successfulDeliveries(successful)
                .failedDeliveries(failed)
                .pendingEvents(pending)
                .retryingEvents(retrying)
                .successRate(Math.round(successRate * 10.0) / 10.0)
                .avgDeliveryDurationMs(avgDuration)
                .build();
    }
}
