-- V3: Integrations table and foreign key updates

CREATE TABLE IF NOT EXISTS integrations (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    owner_id          BIGINT          NOT NULL,
    name              VARCHAR(100)    NOT NULL,
    destination_url   VARCHAR(2048)   NOT NULL,
    signing_secret    VARCHAR(512)    NOT NULL,
    event_types       VARCHAR(1000)   DEFAULT '*',
    enabled           BOOLEAN         NOT NULL DEFAULT TRUE,
    description       VARCHAR(1000),
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_integrations PRIMARY KEY (id),
    CONSTRAINT fk_integrations_owner
        FOREIGN KEY (owner_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_integrations_owner   ON integrations(owner_id);
CREATE INDEX IF NOT EXISTS idx_integrations_enabled ON integrations(enabled);

-- Add FK from notifications to app_users and integrations (if not already set)
ALTER TABLE notifications
    ADD CONSTRAINT IF NOT EXISTS fk_notifications_owner
        FOREIGN KEY (owner_id) REFERENCES app_users(id);

ALTER TABLE notifications
    ADD CONSTRAINT IF NOT EXISTS fk_notifications_integration
        FOREIGN KEY (integration_id) REFERENCES integrations(id);

-- Add idempotency unique constraint
ALTER TABLE notifications
    ADD CONSTRAINT IF NOT EXISTS uk_notifications_idempotency
        UNIQUE (owner_id, idempotency_key);
