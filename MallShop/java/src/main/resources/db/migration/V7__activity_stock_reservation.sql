ALTER TABLE cart_items ADD COLUMN activity_id BIGINT;
CREATE INDEX idx_cart_activity ON cart_items(activity_id);

ALTER TABLE order_items ADD COLUMN activity_id BIGINT;
ALTER TABLE order_items ADD COLUMN activity_code VARCHAR(40);
CREATE INDEX idx_order_item_activity ON order_items(activity_id, product_id);

UPDATE cart_items SET price_override = NULL;

UPDATE activity_products ap
SET activity_stock = (
    SELECT p.stock FROM products p WHERE p.id = ap.product_id
)
WHERE ap.activity_stock IS NULL;
