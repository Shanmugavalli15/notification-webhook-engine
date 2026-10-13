package com.example.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Immutable record of a single delivery attempt.
 */
@Entity
@Table(name = "delivery_logs",
        indexes = {
            @Index(name = "idx_delivery_logs_notification", columnList = "notification_id"),
            @Index(name = "idx_delivery_logs_timestamp", columnList = "timestamp")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"notification"})
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Notification notification;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    /** Duration of the HTTP call in milliseconds. */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** Truncated response body (max ~2000 chars). */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** Sanitized error message when an exception occurred. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Whether this attempt resulted in a successful delivery. */
    @Column(name = "success", nullable = false)
    @Builder.Default
    private boolean success = false;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
