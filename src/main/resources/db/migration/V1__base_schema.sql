-- V1: Base schema — notifications and delivery_logs tables
-- Compatible with H2 and PostgreSQL

CREATE TABLE IF NOT EXISTS notifications (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    owner_id          BIGINT,
    integration_id    BIGINT,
    recipient         VARCHAR(2048)   NOT NULL,
    channel_type      VARCHAR(20)     NOT NULL DEFAULT 'WEBHOOK',
    idempotency_key   VARCHAR(255),
    event_type        VARCHAR(100)    NOT NULL DEFAULT 'generic.event',
    payload           TEXT            NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count       INT             NOT NULL DEFAULT 0,
    max_attempts      INT             NOT NULL DEFAULT 4,
    next_retry_at     TIMESTAMP,
    last_attempt_at   TIMESTAMP,
    error_message     TEXT,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS delivery_logs (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    notification_id   BIGINT          NOT NULL,
    attempt_number    INT             NOT NULL,
    http_status_code  INT,
    duration_ms       BIGINT,
    response_body     TEXT,
    error_message     TEXT,
    success           BOOLEAN         NOT NULL DEFAULT FALSE,
    timestamp         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_delivery_logs PRIMARY KEY (id),
    CONSTRAINT fk_delivery_logs_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_notifications_status      ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_next_retry  ON notifications(next_retry_at);
CREATE INDEX IF NOT EXISTS idx_notifications_owner       ON notifications(owner_id);
CREATE INDEX IF NOT EXISTS idx_notifications_integration ON notifications(integration_id);
CREATE INDEX IF NOT EXISTS idx_delivery_logs_notification ON delivery_logs(notification_id);
CREATE INDEX IF NOT EXISTS idx_delivery_logs_timestamp    ON delivery_logs(timestamp);
