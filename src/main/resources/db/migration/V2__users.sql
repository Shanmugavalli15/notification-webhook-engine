-- V2: Users table

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255)    NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    full_name     VARCHAR(100),
    enabled       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_app_users    PRIMARY KEY (id),
    CONSTRAINT uk_users_email  UNIQUE (email)
);
