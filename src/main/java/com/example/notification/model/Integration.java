package com.example.notification.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A configured webhook integration — a named destination with its own signing secret
 * and event-type subscriptions.
 */
@Entity
@Table(name = "integrations",
        indexes = {
            @Index(name = "idx_integrations_owner", columnList = "owner_id"),
            @Index(name = "idx_integrations_enabled", columnList = "enabled")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Integration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppUser owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    /**
     * HMAC-SHA256 signing secret.
     * Stored as plain text (base64-encoded random bytes) for now.
     * In production, consider encrypting at rest.
     */
    @Column(name = "signing_secret", nullable = false, length = 512)
    private String signingSecret;

    /** Comma-separated list of subscribed event types, e.g. "order.created,payment.processed" */
    @Column(name = "event_types", length = 1000)
    @Builder.Default
    private String eventTypes = "*";

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(length = 1000)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "integration", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Notification> events = new ArrayList<>();
}
