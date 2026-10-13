package com.example.notification.model;

/**
 * Lifecycle states for a webhook event delivery.
 *
 * Allowed transitions:
 *  PENDING     → PROCESSING
 *  PROCESSING  → SUCCESSFUL | RETRYING | FAILED
 *  RETRYING    → PROCESSING
 *  SUCCESSFUL  → (terminal)
 *  FAILED      → (terminal, unless manual replay creates a new event)
 */
public enum NotificationStatus {
    /** Accepted and waiting for a worker to pick it up. */
    PENDING,

    /** A worker has claimed it and is attempting delivery right now. */
    PROCESSING,

    /** Last attempt failed; waiting for the next scheduled retry. */
    RETRYING,

    /** Delivered successfully (HTTP 2xx received). */
    SUCCESSFUL,

    /** All retry attempts exhausted without success. */
    FAILED,

    // Legacy alias kept for backward compatibility with existing H2 data
    /** @deprecated Use {@link #SUCCESSFUL} */
    @Deprecated
    DELIVERED,

    /** @deprecated Use {@link #FAILED} */
    @Deprecated
    FAILED_DLQ
}
