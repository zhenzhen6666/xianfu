CREATE TABLE admin_order_notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    read_at TIMESTAMP,
    accepted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_order_notification_order FOREIGN KEY (order_id) REFERENCES shop_orders(id)
);

CREATE INDEX idx_admin_order_notification_status
    ON admin_order_notifications(status, created_at);

INSERT INTO admin_order_notifications(order_id, status, created_at, updated_at)
SELECT id, 'UNREAD', COALESCE(paid_at, created_at), CURRENT_TIMESTAMP
FROM shop_orders
WHERE status = 'SHIPPING' AND paid_at IS NOT NULL;
