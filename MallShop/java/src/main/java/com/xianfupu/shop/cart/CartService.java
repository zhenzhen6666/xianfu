package com.xianfupu.shop.cart;

import com.xianfupu.shop.catalog.CatalogService;
import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CartService {
    private final JdbcClient jdbc;
    private final CatalogService catalogService;

    public CartService(JdbcClient jdbc, CatalogService catalogService) {
        this.jdbc = jdbc;
        this.catalogService = catalogService;
    }

    public CartView get(long userId) {
        List<CartItem> items = jdbc.sql("""
                SELECT p.product_code AS id, p.name, p.subtitle,
                       COALESCE(sku.spec_name, p.spec) AS spec, p.image_url AS image,
                       COALESCE(ci.price_override,
                           CASE WHEN ml.code <> 'NORMAL' AND COALESCE(sku.member_price, p.member_price) IS NOT NULL
                                THEN COALESCE(sku.member_price, p.member_price)
                                ELSE COALESCE(sku.sale_price, p.sale_price) END) AS price,
                       COALESCE(sku.original_price, p.original_price) AS original_price,
                       COALESCE(sku.member_price, p.member_price) AS member_price,
                       COALESCE(sku.stock, p.stock) AS stock, ci.quantity AS count, ci.selected,
                       ci.price_override, sku.sku_code
                FROM cart_items ci JOIN products p ON p.id = ci.product_id
                LEFT JOIN product_skus sku ON sku.id = ci.sku_id
                JOIN users u ON u.id = ci.user_id JOIN member_levels ml ON ml.id = u.member_level_id
                WHERE ci.user_id = :userId AND p.status = 'ON_SALE'
                  AND (ci.sku_id IS NULL OR sku.enabled = TRUE)
                ORDER BY ci.updated_at DESC, ci.id DESC
                """).param("userId", userId).query(CartItem.class).list();
        BigDecimal subtotal = items.stream().filter(CartItem::selected)
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        CatalogService.DeliveryConfig config = catalogService.deliveryConfig();
        BigDecimal deliveryFee = subtotal.signum() > 0 && subtotal.compareTo(config.freeThreshold()) < 0
                ? config.baseFee() : BigDecimal.ZERO.setScale(2);
        BigDecimal remaining = config.freeThreshold().subtract(subtotal).max(BigDecimal.ZERO).setScale(2);
        int selectedCount = items.stream().filter(CartItem::selected).mapToInt(CartItem::count).sum();
        return new CartView(items, selectedCount, subtotal, deliveryFee, remaining,
                subtotal.add(deliveryFee).setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional
    public CartView add(long userId, String productCode, int quantity, String activityCode) {
        return add(userId, productCode, quantity, activityCode, null);
    }

    @Transactional
    public CartView add(long userId, String productCode, int quantity, String activityCode, String skuCode) {
        ProductStock product = productStock(productCode, skuCode);
        if (quantity < 1 || quantity > 99) {
            throw BusinessException.badRequest("INVALID_QUANTITY", "购买数量必须在 1 到 99 之间");
        }
        BigDecimal override = activityCode == null || activityCode.isBlank()
                ? null : activityPrice(userId, product.id(), productCode, activityCode, quantity);
        CartSelection current = jdbc.sql("""
                SELECT quantity, sku_id FROM cart_items WHERE user_id = :userId AND product_id = :productId
                """).param("userId", userId).param("productId", product.id())
                .query(CartSelection.class).optional().orElse(null);
        boolean sameSku = current != null && java.util.Objects.equals(current.skuId(), product.skuId());
        int target = override == null && sameSku ? current.quantity() + quantity : quantity;
        if (target > product.stock() || target > 99) {
            throw BusinessException.conflict("INSUFFICIENT_STOCK", "商品库存不足，当前可购 " + product.stock() + " 件");
        }
        if (current == null) {
            jdbc.sql("""
                    INSERT INTO cart_items(user_id, product_id, sku_id, quantity, selected, price_override)
                    VALUES (:userId, :productId, :skuId, :quantity, TRUE, :override)
                    """).param("userId", userId).param("productId", product.id())
                    .param("skuId", product.skuId()).param("quantity", target).param("override", override).update();
        } else {
            jdbc.sql("""
                    UPDATE cart_items SET quantity = :quantity, selected = TRUE, sku_id = :skuId,
                           price_override = :override, updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = :userId AND product_id = :productId
                    """).param("quantity", target).param("skuId", product.skuId()).param("override", override)
                    .param("userId", userId).param("productId", product.id()).update();
        }
        return get(userId);
    }

    @Transactional
    public CartView update(long userId, String productCode, Integer quantity, Boolean selected) {
        ProductStock product = jdbc.sql("""
                SELECT p.id, sku.id AS sku_id, COALESCE(sku.stock, p.stock) AS stock
                FROM cart_items ci JOIN products p ON p.id = ci.product_id
                LEFT JOIN product_skus sku ON sku.id = ci.sku_id
                WHERE ci.user_id = :userId AND p.product_code = :code AND p.status = 'ON_SALE'
                """).param("userId", userId).param("code", productCode)
                .query(ProductStock.class).optional()
                .orElseThrow(() -> BusinessException.notFound("CART_ITEM_NOT_FOUND", "购物车中没有该商品"));
        if (quantity != null && (quantity < 1 || quantity > Math.min(99, product.stock()))) {
            throw BusinessException.conflict("INVALID_QUANTITY", "购买数量超过商品库存或限制");
        }
        int updated = jdbc.sql("""
                UPDATE cart_items SET
                    quantity = COALESCE(:quantity, quantity),
                    selected = COALESCE(:selected, selected),
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId AND product_id = :productId
                """).param("quantity", quantity).param("selected", selected)
                .param("userId", userId).param("productId", product.id()).update();
        if (updated == 0) {
            throw BusinessException.notFound("CART_ITEM_NOT_FOUND", "购物车中没有该商品");
        }
        return get(userId);
    }

    @Transactional
    public CartView selectAll(long userId, boolean selected) {
        jdbc.sql("UPDATE cart_items SET selected = :selected, updated_at = CURRENT_TIMESTAMP WHERE user_id = :userId")
                .param("selected", selected).param("userId", userId).update();
        return get(userId);
    }

    @Transactional
    public CartView remove(long userId, String productCode) {
        jdbc.sql("""
                DELETE FROM cart_items WHERE user_id = :userId
                AND product_id = (SELECT id FROM products WHERE product_code = :code)
                """).param("userId", userId).param("code", productCode).update();
        return get(userId);
    }

    @Transactional
    public CartView removeSelected(long userId) {
        jdbc.sql("DELETE FROM cart_items WHERE user_id = :userId AND selected = TRUE")
                .param("userId", userId).update();
        return get(userId);
    }

    private ProductStock productStock(String productCode, String skuCode) {
        String skuCondition = skuCode == null || skuCode.isBlank()
                ? " AND sku.sort_order = (SELECT MIN(s2.sort_order) FROM product_skus s2 WHERE s2.product_id = p.id AND s2.enabled = TRUE)"
                : " AND sku.sku_code = :skuCode";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT p.id, sku.id AS sku_id, COALESCE(sku.stock, p.stock) AS stock
                FROM products p LEFT JOIN product_skus sku ON sku.product_id = p.id AND sku.enabled = TRUE
                WHERE p.product_code = :code AND p.status = 'ON_SALE'
                """ + skuCondition).param("code", productCode);
        if (skuCode != null && !skuCode.isBlank()) query = query.param("skuCode", skuCode);
        return query.query(ProductStock.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "商品或规格不存在或已下架"));
    }

    private BigDecimal activityPrice(long userId, long productId, String productCode,
                                     String activityCode, int quantity) {
        ActivityPrice activity = jdbc.sql("""
                SELECT a.activity_type, ap.activity_price, ap.activity_stock, ap.per_user_limit
                FROM activities a JOIN activity_products ap ON ap.activity_id = a.id
                WHERE a.code = :code AND ap.product_id = :productId AND a.enabled = TRUE
                  AND CURRENT_TIMESTAMP BETWEEN a.starts_at AND a.ends_at
                """).param("code", activityCode).param("productId", productId)
                .query(ActivityPrice.class).optional()
                .orElseThrow(() -> BusinessException.badRequest("ACTIVITY_UNAVAILABLE", "活动不可用或商品不在活动中"));
        int limit = activity.perUserLimit() == null ? 99 : activity.perUserLimit();
        if (quantity > limit) {
            throw BusinessException.badRequest("ACTIVITY_LIMIT", "该活动每人限购 " + limit + " 件");
        }
        if ("penny".equals(activityCode)) {
            long prior = jdbc.sql("""
                    SELECT COUNT(*) FROM order_items oi JOIN shop_orders o ON o.id = oi.order_id
                    WHERE o.user_id = :userId AND oi.product_code = :productCode
                      AND oi.unit_price = 0.01 AND o.status <> 'CANCELLED'
                    """).param("userId", userId).param("productCode", productCode).query(Long.class).single();
            if (prior > 0) {
                throw BusinessException.conflict("NEW_USER_OFFER_USED", "一分钱专享每人仅可购买一次");
            }
        }
        return activity.activityPrice();
    }

    private record ProductStock(long id, Long skuId, int stock) {
    }

    private record CartSelection(int quantity, Long skuId) {
    }

    private record ActivityPrice(String activityType, BigDecimal activityPrice, Integer activityStock,
                                 Integer perUserLimit) {
    }

    public record CartItem(String id, String name, String subtitle, String spec, String image,
                           BigDecimal price, BigDecimal originalPrice, BigDecimal memberPrice,
                           int stock, int count, boolean selected, BigDecimal priceOverride,
                           String skuCode) {
    }

    public record CartView(List<CartItem> items, int selectedCount, BigDecimal subtotal,
                           BigDecimal deliveryFee, BigDecimal remainingForFreeDelivery, BigDecimal payable) {
    }
}
