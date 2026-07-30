package com.xianfupu.shop.catalog;

import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {
    private final JdbcClient jdbc;

    public CatalogService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryView> categories() {
        return jdbc.sql("""
                SELECT c.code, c.name, p.code AS parent_code, c.icon_url, c.sort_order
                FROM categories c LEFT JOIN categories p ON p.id = c.parent_id
                WHERE c.enabled = TRUE ORDER BY c.sort_order, c.id
                """).query(CategoryView.class).list();
    }

    public ProductPage products(String category, String keyword, String sort, boolean specialOnly,
                                boolean memberOnly, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        conditions.add("p.status = 'ON_SALE'");
        conditions.add("c.enabled = TRUE");
        if (category != null && !category.isBlank() && !"推荐".equals(category)) {
            conditions.add("(c.code = :category OR c.name = :category)");
            params.put("category", category);
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(p.name LIKE :keyword OR p.subtitle LIKE :keyword OR c.name LIKE :keyword)");
            params.put("keyword", "%" + keyword.trim() + "%");
        }
        if (specialOnly) {
            conditions.add("p.original_price - p.sale_price >= 2");
        }
        if (memberOnly) {
            conditions.add("p.member_price IS NOT NULL AND p.member_price < p.sale_price");
        }
        String where = String.join(" AND ", conditions);
        String orderBy = switch (sort == null ? "default" : sort) {
            case "price_asc", "asc" -> "p.sale_price ASC, p.id ASC";
            case "price_desc", "desc" -> "p.sale_price DESC, p.id ASC";
            case "sales" -> "p.sales DESC, p.id ASC";
            default -> "p.sort_order ASC, p.id ASC";
        };
        params.put("limit", safeSize);
        params.put("offset", safePage * safeSize);
        List<ProductView> items = jdbc.sql(productSql() + " WHERE " + where + " ORDER BY " + orderBy
                        + " LIMIT :limit OFFSET :offset")
                .params(params).query(ProductView.class).list();
        long total = jdbc.sql("SELECT COUNT(*) FROM products p JOIN categories c ON c.id = p.category_id WHERE " + where)
                .params(params).query(Long.class).single();
        return new ProductPage(items, safePage, safeSize, total, (total + safeSize - 1) / safeSize);
    }

    public ProductView product(String productCode) {
        return jdbc.sql(productSql() + " WHERE p.product_code = :code AND p.status = 'ON_SALE'")
                .param("code", productCode).query(ProductView.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在或已下架"));
    }

    public ProductDetail productDetail(String productCode) {
        ProductView product = product(productCode);
        List<SkuView> skus = jdbc.sql("""
                SELECT sku_code, spec_name, original_price, sale_price, member_price, stock
                FROM product_skus
                WHERE product_id = (SELECT id FROM products WHERE product_code = :code)
                  AND enabled = TRUE
                ORDER BY sort_order, id
                """).param("code", productCode).query(SkuView.class).list();
        return new ProductDetail(product, skus);
    }

    public DeliveryConfig deliveryConfig() {
        return jdbc.sql("""
                SELECT free_threshold, base_fee, max_distance_km, promise_minutes, announcement
                FROM delivery_configs WHERE active = TRUE ORDER BY updated_at DESC, id DESC LIMIT 1
                """).query(DeliveryConfig.class).optional()
                .orElseThrow(() -> BusinessException.notFound("DELIVERY_CONFIG_NOT_FOUND", "配送规则尚未配置"));
    }

    private String productSql() {
        return """
                SELECT p.product_code AS id, c.name AS category, c.code AS category_code,
                       p.name, p.subtitle, p.description, p.spec,
                       p.sale_price AS price, p.original_price AS original_price,
                       p.member_price AS member_price, p.sales, p.stock,
                       p.image_url AS image, p.badge
                FROM products p JOIN categories c ON c.id = p.category_id
                """;
    }

    public record CategoryView(String code, String name, String parentCode, String iconUrl, int sortOrder) {
    }

    public record ProductView(String id, String category, String categoryCode, String name, String subtitle,
                              String description, String spec, BigDecimal price, BigDecimal originalPrice,
                              BigDecimal memberPrice, int sales, int stock, String image, String badge) {
    }

    public record ProductPage(List<ProductView> items, int page, int size, long total, long totalPages) {
    }

    public record SkuView(String skuCode, String specName, BigDecimal originalPrice,
                          BigDecimal salePrice, BigDecimal memberPrice, int stock) {
    }

    public record ProductDetail(ProductView product, List<SkuView> skus) {
    }

    public record DeliveryConfig(BigDecimal freeThreshold, BigDecimal baseFee, BigDecimal maxDistanceKm,
                                 int promiseMinutes, String announcement) {
    }
}
