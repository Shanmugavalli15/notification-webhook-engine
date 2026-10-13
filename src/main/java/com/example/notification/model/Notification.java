package com.example.notification.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A webhook delivery event — represents one event submission to one integration.
 * Previously named "Notification"; kept for backward compatibility.
 */
@Entity
@Table(name = "notifications",
        indexes = {
            @Index(name = "idx_notifications_status", columnList = "status"),
            @Index(name = "idx_notifications_next_retry", columnList = "next_retry_at"),
            @Index(name = "idx_notifications_integration", columnList = "integration_id"),
            @Index(name = "idx_notifications_owner", columnList = "owner_id")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_notifications_idempotency",
                    columnNames = {"owner_id", "idempotency_key"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Ownership / routing ────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Integration integration;

    // ── Legacy fields (kept for backward compat with existing dashboard) ──

    /** Webhook URL, email address, or phone number — legacy field. */
    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    @Builder.Default
    private ChannelType channelType = ChannelType.WEBHOOK;

    // ── Event fields ───────────────────────────────────────────────────────

    /**
     * Caller-supplied idempotency key.
     * Unique per (owner, key) pair — duplicate submissions with the same key
     * are rejected and the original event ID is returned.
     */
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    /** The event type string, e.g. "order.created". */
    @Column(name = "event_type", length = 100)
    @Builder.Default
    private String eventType = "generic.event";

    /** JSON payload to deliver. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    // ── Delivery state ─────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** Maximum delivery attempts configured at submission time. */
    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 4;

    /** Scheduled time for the next delivery attempt (null when PENDING or terminal). */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Timestamp of the most recent delivery attempt. */
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    /** Last error message (sanitized — no secrets). */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ── Timestamps ─────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Associations ───────────────────────────────────────────────────────

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp DESC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DeliveryLog> deliveryLogs = new ArrayList<>();
}
