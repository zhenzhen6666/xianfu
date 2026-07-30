CREATE TABLE product_skus (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL UNIQUE,
    spec_name VARCHAR(100) NOT NULL,
    original_price DECIMAL(12,2) NOT NULL,
    sale_price DECIMAL(12,2) NOT NULL,
    member_price DECIMAL(12,2),
    stock INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_product_sku_price CHECK (sale_price >= 0 AND original_price >= sale_price),
    CONSTRAINT ck_product_sku_stock CHECK (stock >= 0)
);
CREATE INDEX idx_product_sku_product ON product_skus(product_id, enabled, sort_order);

INSERT INTO product_skus(product_id, sku_code, spec_name, original_price, sale_price, member_price, stock, sort_order)
SELECT id, CONCAT(product_code, '-DEFAULT'), spec, original_price, sale_price, member_price, stock, 0
FROM products;

INSERT INTO product_skus(product_id, sku_code, spec_name, original_price, sale_price, member_price, stock, sort_order)
SELECT id, 'p1-LARGE', '约 800g/份', 10.80, 7.50, 6.90, 40, 10 FROM products WHERE product_code = 'p1';
INSERT INTO product_skus(product_id, sku_code, spec_name, original_price, sale_price, member_price, stock, sort_order)
SELECT id, 'p3-LARGE', '500g/盒', 42.90, 31.90, 29.90, 18, 10 FROM products WHERE product_code = 'p3';
INSERT INTO product_skus(product_id, sku_code, spec_name, original_price, sale_price, member_price, stock, sort_order)
SELECT id, 'p4-SMALL', '约 500g/份', 9.80, 7.20, 6.60, 35, 10 FROM products WHERE product_code = 'p4';

UPDATE products p SET stock = (
    SELECT COALESCE(SUM(s.stock), 0) FROM product_skus s WHERE s.product_id = p.id AND s.enabled = TRUE
);

ALTER TABLE cart_items ADD COLUMN sku_id BIGINT;
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_sku FOREIGN KEY (sku_id) REFERENCES product_skus(id);
ALTER TABLE order_items ADD COLUMN sku_code VARCHAR(64);
ALTER TABLE order_items ADD COLUMN sku_id BIGINT;
ALTER TABLE order_items ADD CONSTRAINT fk_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_skus(id);

CREATE TABLE after_sale_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    after_sale_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_after_sale_image_after_sale FOREIGN KEY (after_sale_id) REFERENCES after_sales(id)
);
CREATE INDEX idx_after_sale_image ON after_sale_images(after_sale_id, sort_order);

CREATE TABLE coupon_issue_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(40) NOT NULL UNIQUE,
    coupon_id BIGINT NOT NULL,
    admin_user_id BIGINT,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_coupon_batch_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    CONSTRAINT fk_coupon_batch_admin FOREIGN KEY (admin_user_id) REFERENCES users(id)
);

CREATE TABLE coupon_issue_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT,
    user_coupon_id BIGINT,
    mobile VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_coupon_record_batch FOREIGN KEY (batch_id) REFERENCES coupon_issue_batches(id),
    CONSTRAINT fk_coupon_record_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    CONSTRAINT fk_coupon_record_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_coupon_record_user_coupon FOREIGN KEY (user_coupon_id) REFERENCES user_coupons(id)
);
CREATE INDEX idx_coupon_issue_record_batch ON coupon_issue_records(batch_id, id);
CREATE INDEX idx_coupon_issue_record_mobile ON coupon_issue_records(mobile, created_at);

INSERT INTO coupon_issue_batches(batch_no, coupon_id, admin_user_id, total_count,
    success_count, failed_count, created_at, completed_at)
SELECT CONCAT('HIST-', c.id), c.id, NULL, COUNT(uc.id), COUNT(uc.id), 0,
       MIN(uc.claimed_at), MAX(uc.claimed_at)
FROM coupons c JOIN user_coupons uc ON uc.coupon_id = c.id
GROUP BY c.id;

INSERT INTO coupon_issue_records(batch_id, coupon_id, user_id, user_coupon_id,
    mobile, status, created_at)
SELECT b.id, uc.coupon_id, uc.user_id, uc.id, COALESCE(u.mobile, '未绑定手机号'),
       'SUCCESS', uc.claimed_at
FROM user_coupons uc
JOIN users u ON u.id = uc.user_id
JOIN coupon_issue_batches b ON b.batch_no = CONCAT('HIST-', uc.coupon_id);
