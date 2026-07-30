CREATE UNIQUE INDEX uk_users_mobile ON users(mobile);

CREATE TABLE auth_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_auth_session_user ON auth_sessions(user_id, revoked, expires_at);

ALTER TABLE shop_orders ADD COLUMN idempotency_key VARCHAR(80);
ALTER TABLE shop_orders ADD COLUMN accepted_at TIMESTAMP;
ALTER TABLE shop_orders ADD COLUMN preparing_at TIMESTAMP;
ALTER TABLE shop_orders ADD COLUMN delivered_at TIMESTAMP;
CREATE UNIQUE INDEX uk_order_user_idempotency ON shop_orders(user_id, idempotency_key);

ALTER TABLE after_sales ADD COLUMN refund_status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED';
ALTER TABLE after_sales ADD COLUMN refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE after_sales ADD COLUMN refund_error VARCHAR(1000);
ALTER TABLE after_sales ADD COLUMN processed_at TIMESTAMP;
ALTER TABLE after_sales ADD COLUMN refunded_at TIMESTAMP;

CREATE TABLE after_sale_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    after_sale_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    refund_status VARCHAR(24) NOT NULL,
    message VARCHAR(1000),
    operator_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_after_sale_history_after_sale FOREIGN KEY (after_sale_id) REFERENCES after_sales(id),
    CONSTRAINT fk_after_sale_history_operator FOREIGN KEY (operator_user_id) REFERENCES users(id)
);
CREATE INDEX idx_after_sale_history ON after_sale_status_history(after_sale_id, created_at);

CREATE TABLE system_error_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64),
    level VARCHAR(16) NOT NULL,
    error_code VARCHAR(80),
    message VARCHAR(1000) NOT NULL,
    request_method VARCHAR(12),
    request_path VARCHAR(500),
    user_id BIGINT,
    stack_trace TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_system_error_created ON system_error_logs(created_at);

CREATE TABLE business_alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_type VARCHAR(40) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    target_type VARCHAR(40),
    target_id VARCHAR(80),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_alert_admin FOREIGN KEY (acknowledged_by) REFERENCES users(id)
);
CREATE INDEX idx_business_alert_status ON business_alerts(status, created_at);

CREATE TABLE payment_callback_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no VARCHAR(40),
    channel VARCHAR(24),
    success BOOLEAN NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    provider_trade_no VARCHAR(100),
    result_code VARCHAR(80),
    message VARCHAR(1000),
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payment_callback_created ON payment_callback_logs(created_at);
