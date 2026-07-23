CREATE TABLE member_levels (
    id BIGINT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    min_spend DECIMAL(12,2) NOT NULL DEFAULT 0,
    points_multiplier DECIMAL(6,2) NOT NULL DEFAULT 1,
    discount_rate DECIMAL(6,4) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    openid VARCHAR(128) UNIQUE,
    mobile VARCHAR(32),
    nickname VARCHAR(80) NOT NULL,
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    member_level_id BIGINT NOT NULL DEFAULT 1,
    points INT NOT NULL DEFAULT 0,
    total_spent DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_member_level FOREIGN KEY (member_level_id) REFERENCES member_levels(id)
);

CREATE TABLE addresses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(80) NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    longitude DECIMAL(10,7),
    latitude DECIMAL(10,7),
    distance_km DECIMAL(7,2),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_address_user ON addresses(user_id);

CREATE TABLE categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    icon_url VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
);

CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code VARCHAR(40) NOT NULL UNIQUE,
    category_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    subtitle VARCHAR(255),
    description TEXT,
    spec VARCHAR(100) NOT NULL,
    image_url VARCHAR(500),
    original_price DECIMAL(12,2) NOT NULL,
    sale_price DECIMAL(12,2) NOT NULL,
    member_price DECIMAL(12,2),
    stock INT NOT NULL DEFAULT 0,
    sales INT NOT NULL DEFAULT 0,
    badge VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT ck_product_price CHECK (sale_price >= 0 AND original_price >= sale_price),
    CONSTRAINT ck_product_stock CHECK (stock >= 0)
);
CREATE INDEX idx_product_category_status ON products(category_id, status);
CREATE INDEX idx_product_sales ON products(sales);

CREATE TABLE product_favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_favorite_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_favorite_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE cart_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    price_override DECIMAL(12,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_cart_quantity CHECK (quantity > 0)
);

CREATE TABLE delivery_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    free_threshold DECIMAL(12,2) NOT NULL,
    base_fee DECIMAL(12,2) NOT NULL,
    max_distance_km DECIMAL(7,2) NOT NULL,
    promise_minutes INT NOT NULL,
    announcement VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    coupon_type VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    amount DECIMAL(12,2) NOT NULL,
    minimum_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_stock INT NOT NULL DEFAULT 0,
    claimed_count INT NOT NULL DEFAULT 0,
    per_user_limit INT NOT NULL DEFAULT 1,
    new_user_only BOOLEAN NOT NULL DEFAULT FALSE,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    order_id BIGINT,
    claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP,
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);
CREATE INDEX idx_user_coupon_status ON user_coupons(user_id, status);

CREATE TABLE activities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    activity_type VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    rules_json TEXT,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activity_products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    activity_price DECIMAL(12,2),
    activity_stock INT,
    per_user_limit INT,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_activity_product UNIQUE (activity_id, product_id),
    CONSTRAINT fk_activity_product_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
    CONSTRAINT fk_activity_product_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE activity_participations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    points_cost INT NOT NULL DEFAULT 0,
    reward_type VARCHAR(30),
    reward_value VARCHAR(100),
    participated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_participation_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
    CONSTRAINT fk_participation_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_participation_user_date ON activity_participations(user_id, participated_at);

CREATE TABLE home_modules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_type VARCHAR(30) NOT NULL,
    title VARCHAR(100),
    content_json TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shop_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    address_id BIGINT,
    status VARCHAR(24) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    delivery_fee DECIMAL(12,2) NOT NULL,
    coupon_discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    member_discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    points_used INT NOT NULL DEFAULT 0,
    points_discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    pay_amount DECIMAL(12,2) NOT NULL,
    receiver_name VARCHAR(80) NOT NULL,
    receiver_mobile VARCHAR(32) NOT NULL,
    receiver_address VARCHAR(255) NOT NULL,
    note VARCHAR(500),
    pay_deadline TIMESTAMP NOT NULL,
    paid_at TIMESTAMP,
    shipped_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_order_address FOREIGN KEY (address_id) REFERENCES addresses(id)
);
CREATE INDEX idx_order_user_status ON shop_orders(user_id, status, created_at);
CREATE INDEX idx_order_expire ON shop_orders(status, pay_deadline);

ALTER TABLE user_coupons ADD CONSTRAINT fk_user_coupon_order FOREIGN KEY (order_id) REFERENCES shop_orders(id);

CREATE TABLE order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_code VARCHAR(40) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    spec VARCHAR(100) NOT NULL,
    image_url VARCHAR(500),
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES shop_orders(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE INDEX idx_order_item_order ON order_items(order_id);

CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    channel VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    provider_trade_no VARCHAR(100),
    request_payload TEXT,
    callback_payload TEXT,
    paid_at TIMESTAMP,
    refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES shop_orders(id)
);
CREATE INDEX idx_payment_order ON payments(order_id);

CREATE TABLE point_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_id BIGINT,
    change_amount INT NOT NULL,
    balance_after INT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_point_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_point_order FOREIGN KEY (order_id) REFERENCES shop_orders(id)
);
CREATE INDEX idx_point_user ON point_transactions(user_id, created_at);

CREATE TABLE after_sales (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    after_sale_no VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    refund_amount DECIMAL(12,2),
    merchant_reply VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_after_sale_order FOREIGN KEY (order_id) REFERENCES shop_orders(id),
    CONSTRAINT fk_after_sale_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_after_sale_user ON after_sales(user_id, status);

CREATE TABLE admin_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    operation VARCHAR(100) NOT NULL,
    target_type VARCHAR(40),
    target_id VARCHAR(80),
    detail_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_user_id) REFERENCES users(id)
);
