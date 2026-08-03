package com.xianfupu.shop.admin;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.xianfupu.shop.common.BusinessException;
import com.xianfupu.shop.order.OrderService;
import com.xianfupu.shop.payment.PaymentService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public AdminService(JdbcClient jdbc, ObjectMapper objectMapper, OrderService orderService,
                        PaymentService paymentService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    public List<AdminProduct> products(String status) {
        String condition = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? "" : " WHERE p.status = :status";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT p.product_code, c.code AS category_code, c.name AS category_name, p.name,
                       p.subtitle, p.description, p.spec, p.image_url, p.original_price, p.sale_price,
                       p.member_price, p.stock, p.sales, p.badge, p.status, p.sort_order, p.updated_at
                FROM products p JOIN categories c ON c.id = p.category_id
                """ + condition + " ORDER BY p.sort_order, p.id");
        if (!condition.isBlank()) {
            query = query.param("status", status.toUpperCase());
        }
        return query.query(AdminProduct.class).list();
    }

    @Transactional
    public AdminProduct saveProduct(long adminId, String productCode, ProductCommand command) {
        validateProduct(command);
        Long categoryId = jdbc.sql("SELECT id FROM categories WHERE code = :code")
                .param("code", command.categoryCode()).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("CATEGORY_NOT_FOUND", "商品分类不存在"));
        String code = productCode == null || productCode.isBlank()
                ? "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase()
                : productCode;
        long exists = jdbc.sql("SELECT COUNT(*) FROM products WHERE product_code = :code")
                .param("code", code).query(Long.class).single();
        if (exists == 0) {
            jdbc.sql("""
                    INSERT INTO products(product_code, category_id, name, subtitle, description, spec,
                        image_url, original_price, sale_price, member_price, stock, badge, status, sort_order)
                    VALUES (:code, :categoryId, :name, :subtitle, :description, :spec, :image,
                        :original, :sale, :member, :stock, :badge, :status, :sortOrder)
                    """).param("code", code).param("categoryId", categoryId).param("name", command.name())
                    .param("subtitle", command.subtitle()).param("description", command.description())
                    .param("spec", command.spec()).param("image", command.imageUrl())
                    .param("original", command.originalPrice()).param("sale", command.salePrice())
                    .param("member", command.memberPrice()).param("stock", command.stock())
                    .param("badge", command.badge()).param("status", command.status().toUpperCase())
                    .param("sortOrder", command.sortOrder()).update();
            audit(adminId, "CREATE_PRODUCT", "PRODUCT", code, command);
        } else {
            jdbc.sql("""
                    UPDATE products SET category_id = :categoryId, name = :name, subtitle = :subtitle,
                        description = :description, spec = :spec, image_url = :image,
                        original_price = :original, sale_price = :sale, member_price = :member,
                        stock = :stock, badge = :badge, status = :status, sort_order = :sortOrder,
                        version = version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE product_code = :code
                    """).param("categoryId", categoryId).param("name", command.name())
                    .param("subtitle", command.subtitle()).param("description", command.description())
                    .param("spec", command.spec()).param("image", command.imageUrl())
                    .param("original", command.originalPrice()).param("sale", command.salePrice())
                    .param("member", command.memberPrice()).param("stock", command.stock())
                    .param("badge", command.badge()).param("status", command.status().toUpperCase())
                    .param("sortOrder", command.sortOrder()).param("code", code).update();
            audit(adminId, "UPDATE_PRODUCT", "PRODUCT", code, command);
        }
        ensureDefaultSku(code, command);
        return products("ALL").stream().filter(item -> item.productCode().equals(code)).findFirst().orElseThrow();
    }

    private void validateProduct(ProductCommand command) {
        String status = command.status() == null ? "" : command.status().toUpperCase();
        if (!List.of("ON_SALE", "OFF_SHELF").contains(status)) {
            throw BusinessException.badRequest("INVALID_PRODUCT_STATUS", "商品状态只能是销售中或已下架");
        }
        if (command.originalPrice().compareTo(command.salePrice()) < 0) {
            throw BusinessException.badRequest("INVALID_PRODUCT_PRICE", "商品原价不能低于销售价");
        }
        if (command.memberPrice() != null && command.memberPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw BusinessException.badRequest("INVALID_PRODUCT_PRICE", "会员价不能小于零");
        }
    }

    @Transactional
    public void deleteProduct(long adminId, String productCode) {
        AdminProduct product = products("ALL").stream()
                .filter(item -> item.productCode().equals(productCode)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
        long orders = jdbc.sql("""
                SELECT COUNT(*) FROM order_items oi JOIN products p ON p.id = oi.product_id
                WHERE p.product_code = :code
                """).param("code", productCode).query(Long.class).single();
        if (orders > 0) {
            throw BusinessException.conflict("PRODUCT_IN_USE", "商品已有订单记录，不能删除，请改为下架");
        }
        Long productId = jdbc.sql("SELECT id FROM products WHERE product_code = :code")
                .param("code", productCode).query(Long.class).single();
        jdbc.sql("DELETE FROM cart_items WHERE product_id = :id").param("id", productId).update();
        jdbc.sql("DELETE FROM product_favorites WHERE product_id = :id").param("id", productId).update();
        jdbc.sql("DELETE FROM activity_products WHERE product_id = :id").param("id", productId).update();
        jdbc.sql("DELETE FROM product_skus WHERE product_id = :id").param("id", productId).update();
        jdbc.sql("DELETE FROM products WHERE id = :id").param("id", productId).update();
        audit(adminId, "DELETE_PRODUCT", "PRODUCT", productCode, product);
    }

    public List<AdminSku> productSkus(String productCode) {
        return jdbc.sql("""
                SELECT sku_code, spec_name, original_price, sale_price, member_price,
                       stock, enabled, sort_order
                FROM product_skus WHERE product_id = (
                    SELECT id FROM products WHERE product_code = :code
                ) ORDER BY sort_order, id
                """).param("code", productCode).query(AdminSku.class).list();
    }

    @Transactional
    public List<AdminSku> saveProductSkus(long adminId, String productCode, List<SkuCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_SKUS", "商品至少需要一个规格");
        }
        Long productId = jdbc.sql("SELECT id FROM products WHERE product_code = :code FOR UPDATE")
                .param("code", productCode).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
        jdbc.sql("UPDATE product_skus SET enabled = FALSE WHERE product_id = :productId")
                .param("productId", productId).update();
        int sort = 0;
        for (SkuCommand command : commands) {
            if (command.salePrice().signum() < 0 || command.originalPrice().compareTo(command.salePrice()) < 0
                    || command.stock() < 0) {
                throw BusinessException.badRequest("INVALID_SKU", "规格价格或库存不正确");
            }
            String skuCode = command.skuCode() == null || command.skuCode().isBlank()
                    ? productCode + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase()
                    : command.skuCode();
            int updated = jdbc.sql("""
                    UPDATE product_skus SET spec_name = :spec, original_price = :original,
                        sale_price = :sale, member_price = :member, stock = :stock,
                        enabled = TRUE, sort_order = :sort, updated_at = CURRENT_TIMESTAMP
                    WHERE product_id = :productId AND sku_code = :skuCode
                    """).param("spec", command.specName()).param("original", command.originalPrice())
                    .param("sale", command.salePrice()).param("member", command.memberPrice())
                    .param("stock", command.stock()).param("sort", sort++)
                    .param("productId", productId).param("skuCode", skuCode).update();
            if (updated == 0) {
                jdbc.sql("""
                        INSERT INTO product_skus(product_id, sku_code, spec_name, original_price,
                            sale_price, member_price, stock, enabled, sort_order)
                        VALUES (:productId, :skuCode, :spec, :original, :sale, :member, :stock, TRUE, :sort)
                        """).param("productId", productId).param("skuCode", skuCode)
                        .param("spec", command.specName()).param("original", command.originalPrice())
                        .param("sale", command.salePrice()).param("member", command.memberPrice())
                        .param("stock", command.stock()).param("sort", sort - 1).update();
            }
        }
        AdminSku first = productSkus(productCode).stream().filter(AdminSku::enabled).findFirst().orElseThrow();
        jdbc.sql("""
                UPDATE products SET spec = :spec, original_price = :original, sale_price = :sale,
                    member_price = :member, stock = (
                        SELECT COALESCE(SUM(stock), 0) FROM product_skus
                        WHERE product_id = :productId AND enabled = TRUE
                    ), updated_at = CURRENT_TIMESTAMP WHERE id = :productId
                """).param("spec", first.specName()).param("original", first.originalPrice())
                .param("sale", first.salePrice()).param("member", first.memberPrice())
                .param("productId", productId).update();
        audit(adminId, "UPDATE_PRODUCT_SKUS", "PRODUCT", productCode, commands);
        return productSkus(productCode);
    }

    private void ensureDefaultSku(String productCode, ProductCommand command) {
        Long productId = jdbc.sql("SELECT id FROM products WHERE product_code = :code")
                .param("code", productCode).query(Long.class).single();
        long skuCount = jdbc.sql("SELECT COUNT(*) FROM product_skus WHERE product_id = :productId AND enabled = TRUE")
                .param("productId", productId).query(Long.class).single();
        int defaultStock = skuCount > 1
                ? jdbc.sql("""
                        SELECT stock FROM product_skus WHERE product_id = :productId
                          AND sku_code = :skuCode
                        """).param("productId", productId).param("skuCode", productCode + "-DEFAULT")
                        .query(Integer.class).optional().orElse(command.stock())
                : command.stock();
        int updated = jdbc.sql("""
                UPDATE product_skus SET spec_name = :spec, original_price = :original,
                    sale_price = :sale, member_price = :member, stock = :stock,
                    enabled = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE product_id = :productId AND sku_code = :skuCode
                """).param("spec", command.spec()).param("original", command.originalPrice())
                .param("sale", command.salePrice()).param("member", command.memberPrice())
                .param("stock", defaultStock).param("productId", productId)
                .param("skuCode", productCode + "-DEFAULT").update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO product_skus(product_id, sku_code, spec_name, original_price,
                        sale_price, member_price, stock, enabled, sort_order)
                    VALUES (:productId, :skuCode, :spec, :original, :sale, :member, :stock, TRUE, 0)
                    """).param("productId", productId).param("skuCode", productCode + "-DEFAULT")
                    .param("spec", command.spec()).param("original", command.originalPrice())
                    .param("sale", command.salePrice()).param("member", command.memberPrice())
                    .param("stock", defaultStock).update();
        }
        jdbc.sql("""
                UPDATE products SET stock = (
                    SELECT COALESCE(SUM(stock), 0) FROM product_skus
                    WHERE product_id = :productId AND enabled = TRUE
                ) WHERE id = :productId
                """).param("productId", productId).update();
    }

    public List<AdminOrder> orders(String status, int page, int size) {
        int safeSize = Math.min(100, Math.max(1, size));
        int safePage = Math.max(0, page);
        String condition = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? "" : " WHERE o.status = :status";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT o.order_no, o.status, u.nickname, u.mobile, o.pay_amount, o.delivery_fee,
                       o.receiver_name, o.receiver_mobile, o.receiver_address, o.created_at, o.paid_at
                FROM shop_orders o JOIN users u ON u.id = o.user_id
                """ + condition + " ORDER BY o.created_at DESC LIMIT :limit OFFSET :offset")
                .param("limit", safeSize).param("offset", safePage * safeSize);
        if (!condition.isBlank()) {
            query = query.param("status", status.toUpperCase());
        }
        return query.query(AdminOrder.class).list();
    }

    public OrderService.OrderDetail order(String orderNo) {
        return orderService.adminDetail(orderNo);
    }

    @Transactional
    public OrderService.OrderDetail updateOrderStatus(long adminId, String orderNo, String status) {
        String target = status.toUpperCase();
        if (!List.of("ACCEPTED", "PREPARING", "SHIPPING", "DELIVERED", "COMPLETED", "CANCELLED").contains(target)) {
            throw BusinessException.badRequest("INVALID_ORDER_STATUS", "不支持的订单状态");
        }
        OrderStatusRow order = jdbc.sql("""
                SELECT user_id, status FROM shop_orders WHERE order_no = :orderNo FOR UPDATE
                """).param("orderNo", orderNo).query(OrderStatusRow.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
        if ("COMPLETED".equals(target)) {
            OrderService.OrderDetail result = orderService.confirm(order.userId(), orderNo);
            acceptOrderNotification(orderNo);
            audit(adminId, "UPDATE_ORDER_STATUS", "ORDER", orderNo, status);
            return result;
        }
        if ("CANCELLED".equals(target)) {
            OrderService.OrderDetail result = orderService.cancel(order.userId(), orderNo, "商家取消订单");
            acceptOrderNotification(orderNo);
            audit(adminId, "UPDATE_ORDER_STATUS", "ORDER", orderNo, status);
            return result;
        }
        String expected = switch (target) {
            case "ACCEPTED" -> "PAID";
            case "PREPARING" -> "ACCEPTED";
            case "SHIPPING" -> "PREPARING";
            case "DELIVERED" -> "SHIPPING";
            default -> "";
        };
        if (!expected.equals(order.status())) {
            throw BusinessException.conflict("ORDER_STATUS_INVALID",
                    "订单需按 已支付→已接单→备货中→配送中→已送达→已完成 顺序流转");
        }
        int updated = jdbc.sql("""
                UPDATE shop_orders SET status = :status,
                    accepted_at = CASE WHEN :status = 'ACCEPTED' THEN CURRENT_TIMESTAMP ELSE accepted_at END,
                    preparing_at = CASE WHEN :status = 'PREPARING' THEN CURRENT_TIMESTAMP ELSE preparing_at END,
                    shipped_at = CASE WHEN :status = 'SHIPPING' THEN CURRENT_TIMESTAMP ELSE shipped_at END,
                    delivered_at = CASE WHEN :status = 'DELIVERED' THEN CURRENT_TIMESTAMP ELSE delivered_at END,
                    updated_at = CURRENT_TIMESTAMP WHERE order_no = :orderNo AND status = :expected
                """).param("status", target).param("orderNo", orderNo).param("expected", expected).update();
        if (updated == 0) {
            throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        }
        acceptOrderNotification(orderNo);
        audit(adminId, "UPDATE_ORDER_STATUS", "ORDER", orderNo, status);
        return orderService.adminDetail(orderNo);
    }

    @Transactional
    public DeliveryConfig updateDelivery(long adminId, DeliveryCommand command) {
        jdbc.sql("UPDATE delivery_configs SET active = FALSE WHERE active = TRUE").update();
        jdbc.sql("""
                INSERT INTO delivery_configs(name, free_threshold, base_fee, max_distance_km,
                    promise_minutes, announcement, active)
                VALUES (:name, :threshold, :fee, :distance, :minutes, :announcement, TRUE)
                """).param("name", command.name()).param("threshold", command.freeThreshold())
                .param("fee", command.baseFee()).param("distance", command.maxDistanceKm())
                .param("minutes", command.promiseMinutes()).param("announcement", command.announcement()).update();
        audit(adminId, "UPDATE_DELIVERY_CONFIG", "DELIVERY_CONFIG", null, command);
        return delivery();
    }

    public DeliveryConfig delivery() {
        return jdbc.sql("""
                SELECT id, name, free_threshold, base_fee, max_distance_km, promise_minutes,
                       announcement, updated_at FROM delivery_configs WHERE active = TRUE
                ORDER BY id DESC LIMIT 1
                """).query(DeliveryConfig.class).single();
    }

    public Dashboard dashboard() {
        long todayOrders = jdbc.sql("SELECT COUNT(*) FROM shop_orders WHERE created_at >= CURRENT_DATE")
                .query(Long.class).single();
        BigDecimal todaySales = jdbc.sql("""
                SELECT COALESCE(SUM(pay_amount), 0) FROM shop_orders
                WHERE paid_at >= CURRENT_DATE AND status IN
                    ('PAID','ACCEPTED','PREPARING','SHIPPING','DELIVERED','COMPLETED','REFUNDED')
                """).query(BigDecimal.class).single();
        long newUsers = jdbc.sql("SELECT COUNT(*) FROM users WHERE created_at >= CURRENT_DATE AND role = 'USER'")
                .query(Long.class).single();
        List<HotProduct> hotProducts = jdbc.sql("""
                SELECT product_code, name, sales, stock FROM products ORDER BY sales DESC LIMIT 10
                """).query(HotProduct.class).list();
        return new Dashboard(todayOrders, todaySales, newUsers, hotProducts);
    }

    public List<AdminUser> users(String keyword) {
        String condition = keyword == null || keyword.isBlank() ? "" :
                " AND (nickname LIKE :keyword OR mobile LIKE :keyword)";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT u.id, u.nickname, u.mobile, u.status, u.points, u.total_spent,
                       ml.name AS member_level_name, u.created_at
                FROM users u JOIN member_levels ml ON ml.id = u.member_level_id
                WHERE u.role = 'USER'
                """ + condition + " ORDER BY u.id DESC LIMIT 200");
        if (!condition.isBlank()) {
            query = query.param("keyword", "%" + keyword.trim() + "%");
        }
        return query.query(AdminUser.class).list();
    }

    public List<AdminCoupon> coupons() {
        return jdbc.sql("""
                SELECT id, code, name, amount, minimum_amount, total_stock, claimed_count,
                       per_user_limit, starts_at, ends_at, enabled, created_at
                FROM coupons ORDER BY id DESC
                """).query(CouponRow.class).list().stream().map(this::toAdminCoupon).toList();
    }

    @Transactional
    public AdminCoupon createCoupon(long adminId, CouponCommand command) {
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw BusinessException.badRequest("INVALID_COUPON_PERIOD", "优惠券结束时间必须晚于开始时间");
        }
        String code = "CP" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        jdbc.sql("""
                INSERT INTO coupons(code, name, coupon_type, amount, minimum_amount, total_stock,
                    claimed_count, per_user_limit, new_user_only, starts_at, ends_at, enabled)
                VALUES (:code, :name, 'FIXED', :amount, :minimumAmount, :totalStock,
                    0, 1, FALSE, :startsAt, :endsAt, :enabled)
                """).param("code", code).param("name", command.name().trim())
                .param("amount", command.amount()).param("minimumAmount", command.minimumAmount())
                .param("totalStock", command.totalStock()).param("startsAt", command.startsAt())
                .param("endsAt", command.endsAt()).param("enabled", command.enabled()).update();
        audit(adminId, "CREATE_COUPON", "COUPON", code, command);
        return coupons().stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    @Transactional
    public AdminCoupon updateCoupon(long adminId, String couponCode, CouponCommand command) {
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw BusinessException.badRequest("INVALID_COUPON_PERIOD", "优惠券结束时间必须晚于开始时间");
        }
        int claimed = jdbc.sql("SELECT claimed_count FROM coupons WHERE code = :code FOR UPDATE")
                .param("code", couponCode).query(Integer.class).optional()
                .orElseThrow(() -> BusinessException.notFound("COUPON_NOT_FOUND", "优惠券不存在"));
        if (command.totalStock() < claimed) {
            throw BusinessException.badRequest("INVALID_COUPON_STOCK", "发行数量不能小于已发放数量");
        }
        jdbc.sql("""
                UPDATE coupons SET name = :name, amount = :amount, minimum_amount = :minimumAmount,
                    total_stock = :totalStock, starts_at = :startsAt, ends_at = :endsAt,
                    enabled = :enabled WHERE code = :code
                """).param("name", command.name().trim()).param("amount", command.amount())
                .param("minimumAmount", command.minimumAmount()).param("totalStock", command.totalStock())
                .param("startsAt", command.startsAt()).param("endsAt", command.endsAt())
                .param("enabled", command.enabled()).param("code", couponCode).update();
        audit(adminId, "UPDATE_COUPON", "COUPON", couponCode, command);
        return coupons().stream().filter(item -> item.code().equals(couponCode)).findFirst().orElseThrow();
    }

    @Transactional
    public AdminCoupon setCouponEnabled(long adminId, String couponCode, boolean enabled) {
        int updated = jdbc.sql("UPDATE coupons SET enabled = :enabled WHERE code = :code")
                .param("enabled", enabled).param("code", couponCode).update();
        if (updated == 0) throw BusinessException.notFound("COUPON_NOT_FOUND", "优惠券不存在");
        audit(adminId, "UPDATE_COUPON_STATUS", "COUPON", couponCode, enabled);
        return coupons().stream().filter(item -> item.code().equals(couponCode)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteCoupon(long adminId, String couponCode) {
        CouponRow coupon = jdbc.sql("""
                SELECT id, code, name, amount, minimum_amount, total_stock, claimed_count,
                       per_user_limit, starts_at, ends_at, enabled, created_at
                FROM coupons WHERE code = :code
                """).param("code", couponCode).query(CouponRow.class).optional()
                .orElseThrow(() -> BusinessException.notFound("COUPON_NOT_FOUND", "优惠券不存在"));
        long records = jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM user_coupons WHERE coupon_id = :id)
                     + (SELECT COUNT(*) FROM coupon_issue_records WHERE coupon_id = :id)
                """).param("id", coupon.id()).query(Long.class).single();
        if (records > 0) {
            throw BusinessException.conflict("COUPON_IN_USE", "优惠券已有领取或发放记录，不能删除，请改为停用");
        }
        jdbc.sql("DELETE FROM coupon_issue_batches WHERE coupon_id = :id")
                .param("id", coupon.id()).update();
        jdbc.sql("DELETE FROM coupons WHERE id = :id").param("id", coupon.id()).update();
        audit(adminId, "DELETE_COUPON", "COUPON", couponCode, coupon);
    }

    @Transactional
    public CouponIssueResult issueCoupon(long adminId, String couponCode, String mobile) {
        String normalizedMobile = mobile == null ? "" : mobile.trim();
        String normalizedCode = couponCode == null ? "" : couponCode.trim().toUpperCase();
        CouponIssueDefinition coupon = jdbc.sql("""
                SELECT id, code, name, total_stock, claimed_count, per_user_limit,
                       starts_at, ends_at, enabled
                FROM coupons WHERE code = :code FOR UPDATE
                """).param("code", normalizedCode).query(CouponIssueDefinition.class).optional()
                .orElseThrow(() -> BusinessException.notFound("COUPON_NOT_FOUND", "优惠券不存在"));
        if (!coupon.enabled() || !coupon.endsAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.conflict("COUPON_NOT_ISSUABLE", "优惠券已停用或已过期，不能继续发放");
        }
        if (coupon.claimedCount() >= coupon.totalStock()) {
            throw BusinessException.conflict("COUPON_OUT_OF_STOCK", "优惠券库存已发完");
        }
        CouponUser user = jdbc.sql("""
                SELECT id, nickname, mobile FROM users
                WHERE mobile = :mobile AND role = 'USER' AND status = 'ACTIVE'
                ORDER BY id LIMIT 1
                """).param("mobile", normalizedMobile).query(CouponUser.class).optional()
                .orElseThrow(() -> BusinessException.notFound(
                        "USER_MOBILE_NOT_FOUND", "未找到该手机号对应的可用用户账户"));
        long issuedCount = jdbc.sql("""
                SELECT COUNT(*) FROM user_coupons WHERE user_id = :userId AND coupon_id = :couponId
                """).param("userId", user.id()).param("couponId", coupon.id()).query(Long.class).single();
        if (issuedCount >= coupon.perUserLimit()) {
            throw BusinessException.conflict("COUPON_LIMIT_REACHED", "该用户已拥有这张优惠券");
        }
        jdbc.sql("INSERT INTO user_coupons(user_id, coupon_id) VALUES (:userId, :couponId)")
                .param("userId", user.id()).param("couponId", coupon.id()).update();
        jdbc.sql("UPDATE coupons SET claimed_count = claimed_count + 1 WHERE id = :id")
                .param("id", coupon.id()).update();
        Long userCouponId = jdbc.sql("""
                SELECT id FROM user_coupons WHERE user_id = :userId AND coupon_id = :couponId
                ORDER BY id DESC LIMIT 1
                """).param("userId", user.id()).param("couponId", coupon.id()).query(Long.class).single();
        String issuedStatus = LocalDateTime.now().isBefore(coupon.startsAt())
                ? "NOT_STARTED" : "AVAILABLE";
        CouponIssueResult result = new CouponIssueResult(
                userCouponId, coupon.code(), coupon.name(), user.id(), user.nickname(),
                user.mobile(), issuedStatus, LocalDateTime.now());
        audit(adminId, "ISSUE_COUPON", "USER_COUPON", userCouponId.toString(), result);
        Long batchId = createIssueBatch(adminId, coupon.id(), 1);
        jdbc.sql("""
                INSERT INTO coupon_issue_records(batch_id, coupon_id, user_id, user_coupon_id, mobile, status)
                VALUES (:batchId, :couponId, :userId, :userCouponId, :mobile, 'SUCCESS')
                """).param("batchId", batchId).param("couponId", coupon.id()).param("userId", user.id())
                .param("userCouponId", userCouponId).param("mobile", user.mobile()).update();
        completeIssueBatch(batchId, 1, 0);
        return result;
    }

    @Transactional
    public CouponBatchResult batchIssueCoupon(long adminId, String couponCode, List<String> mobiles) {
        List<String> normalized = mobiles == null ? List.of() : mobiles.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().limit(500).toList();
        if (normalized.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_MOBILE_LIST", "请至少填写一个手机号");
        }
        CouponIssueDefinition coupon = jdbc.sql("""
                SELECT id, code, name, total_stock, claimed_count, per_user_limit,
                       starts_at, ends_at, enabled
                FROM coupons WHERE code = :code FOR UPDATE
                """).param("code", couponCode).query(CouponIssueDefinition.class).optional()
                .orElseThrow(() -> BusinessException.notFound("COUPON_NOT_FOUND", "优惠券不存在"));
        if (!coupon.enabled() || !coupon.endsAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.conflict("COUPON_NOT_ISSUABLE", "优惠券已停用或已过期，不能继续发放");
        }
        Long batchId = createIssueBatch(adminId, coupon.id(), normalized.size());
        String batchNo = jdbc.sql("SELECT batch_no FROM coupon_issue_batches WHERE id = :id")
                .param("id", batchId).query(String.class).single();
        int success = 0;
        int failed = 0;
        for (String mobile : normalized) {
            try {
                CouponIssueTarget issued = issueOne(coupon, mobile);
                jdbc.sql("""
                        INSERT INTO coupon_issue_records(batch_id, coupon_id, user_id, user_coupon_id, mobile, status)
                        VALUES (:batchId, :couponId, :userId, :userCouponId, :mobile, 'SUCCESS')
                        """).param("batchId", batchId).param("couponId", coupon.id())
                        .param("userId", issued.userId()).param("userCouponId", issued.userCouponId())
                        .param("mobile", mobile).update();
                success++;
            } catch (BusinessException ex) {
                jdbc.sql("""
                        INSERT INTO coupon_issue_records(batch_id, coupon_id, mobile, status, failure_reason)
                        VALUES (:batchId, :couponId, :mobile, 'FAILED', :reason)
                        """).param("batchId", batchId).param("couponId", coupon.id())
                        .param("mobile", mobile).param("reason", ex.getMessage()).update();
                failed++;
            }
        }
        completeIssueBatch(batchId, success, failed);
        CouponBatchResult result = new CouponBatchResult(batchNo, normalized.size(), success, failed);
        audit(adminId, "BATCH_ISSUE_COUPON", "COUPON", couponCode, result);
        return result;
    }

    public List<CouponIssueRecordView> couponIssueRecords(String couponCode) {
        String condition = couponCode == null || couponCode.isBlank() ? "" : " WHERE c.code = :code";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT b.batch_no, c.code AS coupon_code, c.name AS coupon_name, r.mobile,
                       u.nickname, r.status, r.failure_reason, r.created_at
                FROM coupon_issue_records r
                JOIN coupon_issue_batches b ON b.id = r.batch_id
                JOIN coupons c ON c.id = r.coupon_id
                LEFT JOIN users u ON u.id = r.user_id
                """ + condition + " ORDER BY r.id DESC LIMIT 1000");
        if (!condition.isBlank()) query = query.param("code", couponCode);
        return query.query(CouponIssueRecordView.class).list();
    }

    private CouponIssueTarget issueOne(CouponIssueDefinition coupon, String mobile) {
        if (!mobile.matches("^1[3-9]\\d{9}$")) {
            throw BusinessException.badRequest("INVALID_MOBILE", "手机号格式不正确");
        }
        int claimed = jdbc.sql("SELECT claimed_count FROM coupons WHERE id = :id")
                .param("id", coupon.id()).query(Integer.class).single();
        if (claimed >= coupon.totalStock()) throw BusinessException.conflict("COUPON_OUT_OF_STOCK", "优惠券库存已发完");
        CouponUser user = jdbc.sql("""
                SELECT id, nickname, mobile FROM users
                WHERE mobile = :mobile AND role = 'USER' AND status = 'ACTIVE'
                """).param("mobile", mobile).query(CouponUser.class).optional()
                .orElseThrow(() -> BusinessException.notFound("USER_MOBILE_NOT_FOUND", "手机号未绑定可用用户"));
        long issuedCount = jdbc.sql("""
                SELECT COUNT(*) FROM user_coupons WHERE user_id = :userId AND coupon_id = :couponId
                """).param("userId", user.id()).param("couponId", coupon.id()).query(Long.class).single();
        if (issuedCount >= coupon.perUserLimit()) {
            throw BusinessException.conflict("COUPON_LIMIT_REACHED", "该用户已拥有这张优惠券");
        }
        jdbc.sql("INSERT INTO user_coupons(user_id, coupon_id) VALUES (:userId, :couponId)")
                .param("userId", user.id()).param("couponId", coupon.id()).update();
        jdbc.sql("UPDATE coupons SET claimed_count = claimed_count + 1 WHERE id = :id")
                .param("id", coupon.id()).update();
        Long userCouponId = jdbc.sql("""
                SELECT id FROM user_coupons WHERE user_id = :userId AND coupon_id = :couponId
                ORDER BY id DESC LIMIT 1
                """).param("userId", user.id()).param("couponId", coupon.id()).query(Long.class).single();
        return new CouponIssueTarget(user.id(), userCouponId);
    }

    private Long createIssueBatch(long adminId, long couponId, int total) {
        String batchNo = "CB" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        jdbc.sql("""
                INSERT INTO coupon_issue_batches(batch_no, coupon_id, admin_user_id, total_count)
                VALUES (:batchNo, :couponId, :adminId, :total)
                """).param("batchNo", batchNo).param("couponId", couponId)
                .param("adminId", adminId).param("total", total).update();
        return jdbc.sql("SELECT id FROM coupon_issue_batches WHERE batch_no = :batchNo")
                .param("batchNo", batchNo).query(Long.class).single();
    }

    private void completeIssueBatch(long batchId, int success, int failed) {
        jdbc.sql("""
                UPDATE coupon_issue_batches SET success_count = :success, failed_count = :failed,
                    completed_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("success", success).param("failed", failed).param("id", batchId).update();
    }

    @Transactional
    public void setUserStatus(long adminId, long userId, String status) {
        String target = status.toUpperCase();
        if (!List.of("ACTIVE", "BLOCKED").contains(target)) {
            throw BusinessException.badRequest("INVALID_USER_STATUS", "不支持的用户状态");
        }
        int updated = jdbc.sql("UPDATE users SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id AND role = 'USER'")
                .param("status", target).param("id", userId).update();
        if (updated == 0) {
            throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        }
        audit(adminId, "UPDATE_USER_STATUS", "USER", Long.toString(userId), status);
    }

    @Transactional
    public PaymentService.PaymentView refund(long adminId, String orderNo, BigDecimal amount, String reason) {
        PaymentService.PaymentView result = paymentService.refund(orderNo, amount, reason);
        audit(adminId, "REFUND_ORDER", "ORDER", orderNo, new RefundCommand(amount, reason));
        return result;
    }

    public List<AfterSaleAdminView> afterSales(String status) {
        String condition = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? "" : " WHERE a.status = :status";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT a.after_sale_no, o.order_no, u.nickname, a.reason, a.description,
                       a.status, a.refund_amount, a.refund_status, a.refunded_amount,
                       a.refund_error, a.merchant_reply,
                       (SELECT COUNT(*) FROM after_sale_images ai WHERE ai.after_sale_id = a.id) AS image_count,
                       a.created_at
                FROM after_sales a JOIN shop_orders o ON o.id = a.order_id JOIN users u ON u.id = a.user_id
                """ + condition + " ORDER BY a.id DESC");
        if (!condition.isBlank()) {
            query = query.param("status", status.toUpperCase());
        }
        return query.query(AfterSaleAdminView.class).list();
    }

    public List<AdminCategory> categories() {
        return jdbc.sql("""
                SELECT c.code, c.name, p.code AS parent_code, c.icon_url, c.sort_order, c.enabled
                FROM categories c LEFT JOIN categories p ON p.id = c.parent_id
                ORDER BY c.sort_order, c.id
                """).query(AdminCategory.class).list();
    }

    @Transactional
    public List<AdminCategory> saveCategory(long adminId, String code, CategoryCommand command) {
        if (code == null || code.isBlank()) {
            throw BusinessException.badRequest("INVALID_CATEGORY_CODE", "分类编码不能为空");
        }
        if (code.equals(command.parentCode())) {
            throw BusinessException.badRequest("INVALID_CATEGORY_PARENT", "分类不能选择自己作为上级");
        }
        Long parentId = null;
        if (command.parentCode() != null && !command.parentCode().isBlank()) {
            parentId = jdbc.sql("SELECT id FROM categories WHERE code = :code")
                    .param("code", command.parentCode()).query(Long.class).optional()
                    .orElseThrow(() -> BusinessException.notFound("PARENT_CATEGORY_NOT_FOUND", "上级分类不存在"));
        }
        long exists = jdbc.sql("SELECT COUNT(*) FROM categories WHERE code = :code")
                .param("code", code).query(Long.class).single();
        if (exists == 0) {
            jdbc.sql("""
                    INSERT INTO categories(parent_id, code, name, icon_url, sort_order, enabled)
                    VALUES (:parentId, :code, :name, :icon, :sortOrder, :enabled)
                    """).param("parentId", parentId).param("code", code).param("name", command.name())
                    .param("icon", command.iconUrl()).param("sortOrder", command.sortOrder())
                    .param("enabled", command.enabled()).update();
        } else {
            jdbc.sql("""
                    UPDATE categories SET parent_id = :parentId, name = :name, icon_url = :icon,
                        sort_order = :sortOrder, enabled = :enabled WHERE code = :code
                    """).param("parentId", parentId).param("name", command.name()).param("icon", command.iconUrl())
                    .param("sortOrder", command.sortOrder()).param("enabled", command.enabled())
                    .param("code", code).update();
        }
        audit(adminId, "SAVE_CATEGORY", "CATEGORY", code, command);
        return categories();
    }

    @Transactional
    public List<AdminCategory> createCategory(long adminId, CategoryCommand command) {
        String code = "CAT" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase();
        return saveCategory(adminId, code, command);
    }

    @Transactional
    public void deleteCategory(long adminId, String code) {
        AdminCategory category = categories().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("CATEGORY_NOT_FOUND", "商品分类不存在"));
        Long categoryId = jdbc.sql("SELECT id FROM categories WHERE code = :code")
                .param("code", code).query(Long.class).single();
        long references = jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM products WHERE category_id = :id)
                     + (SELECT COUNT(*) FROM categories WHERE parent_id = :id)
                """).param("id", categoryId).query(Long.class).single();
        if (references > 0) {
            throw BusinessException.conflict("CATEGORY_IN_USE", "分类下还有商品或子分类，不能删除，请先处理关联数据");
        }
        jdbc.sql("DELETE FROM categories WHERE id = :id").param("id", categoryId).update();
        audit(adminId, "DELETE_CATEGORY", "CATEGORY", code, category);
    }

    public List<AdminHomeModule> homeModules() {
        return jdbc.sql("""
                SELECT id, module_type, title, content_json, sort_order, enabled, published_at, updated_at
                FROM home_modules ORDER BY sort_order, id
                """).query(AdminHomeModule.class).list();
    }

    @Transactional
    public List<AdminHomeModule> saveHomeModule(long adminId, Long id, HomeModuleCommand command) {
        validateJson(command.contentJson());
        if (id == null) {
            jdbc.sql("""
                    INSERT INTO home_modules(module_type, title, content_json, sort_order, enabled)
                    VALUES (:type, :title, :content, :sortOrder, :enabled)
                    """).param("type", command.moduleType()).param("title", command.title())
                    .param("content", command.contentJson()).param("sortOrder", command.sortOrder())
                    .param("enabled", command.enabled()).update();
        } else {
            int updated = jdbc.sql("""
                    UPDATE home_modules SET module_type = :type, title = :title, content_json = :content,
                        sort_order = :sortOrder, enabled = :enabled, published_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP WHERE id = :id
                    """).param("type", command.moduleType()).param("title", command.title())
                    .param("content", command.contentJson()).param("sortOrder", command.sortOrder())
                    .param("enabled", command.enabled()).param("id", id).update();
            if (updated == 0) {
                throw BusinessException.notFound("HOME_MODULE_NOT_FOUND", "首页组件不存在");
            }
        }
        audit(adminId, "SAVE_HOME_MODULE", "HOME_MODULE", id == null ? null : id.toString(), command);
        return homeModules();
    }

    @Transactional
    public void deleteHomeModule(long adminId, long id) {
        AdminHomeModule module = jdbc.sql("""
                SELECT id, module_type, title, content_json, sort_order, enabled, published_at, updated_at
                FROM home_modules WHERE id = :id
                """).param("id", id).query(AdminHomeModule.class).optional()
                .orElseThrow(() -> BusinessException.notFound("HOME_MODULE_NOT_FOUND", "首页组件不存在"));
        jdbc.sql("DELETE FROM home_modules WHERE id = :id").param("id", id).update();
        audit(adminId, "DELETE_HOME_MODULE", "HOME_MODULE", Long.toString(id), module);
    }

    public List<AdminActivity> activities() {
        return jdbc.sql("""
                SELECT code, name, activity_type, description, rules_json, starts_at, ends_at, enabled
                FROM activities ORDER BY id
                """).query(AdminActivity.class).list();
    }

    @Transactional
    public AdminActivity createActivity(long adminId, ActivityCommand command) {
        validateActivity(command);
        String code = "ACT" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase();
        jdbc.sql("""
                INSERT INTO activities(code, name, activity_type, description, rules_json,
                    starts_at, ends_at, enabled)
                VALUES (:code, :name, :type, :description, :rules, :startsAt, :endsAt, :enabled)
                """).param("code", code).param("name", command.name()).param("type", command.activityType())
                .param("description", command.description()).param("rules", command.rulesJson())
                .param("startsAt", command.startsAt()).param("endsAt", command.endsAt())
                .param("enabled", command.enabled()).update();
        audit(adminId, "CREATE_ACTIVITY", "ACTIVITY", code, command);
        return activities().stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    @Transactional
    public AdminActivity updateActivity(long adminId, String code, ActivityCommand command) {
        validateActivity(command);
        int updated = jdbc.sql("""
                UPDATE activities SET name = :name, activity_type = :type, description = :description,
                    rules_json = :rules, starts_at = :startsAt, ends_at = :endsAt, enabled = :enabled
                WHERE code = :code
                """).param("name", command.name()).param("type", command.activityType())
                .param("description", command.description()).param("rules", command.rulesJson())
                .param("startsAt", command.startsAt()).param("endsAt", command.endsAt())
                .param("enabled", command.enabled()).param("code", code).update();
        if (updated == 0) {
            throw BusinessException.notFound("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        audit(adminId, "UPDATE_ACTIVITY", "ACTIVITY", code, command);
        return activities().stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteActivity(long adminId, String code) {
        AdminActivity activity = activities().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("ACTIVITY_NOT_FOUND", "活动不存在"));
        Long activityId = jdbc.sql("SELECT id FROM activities WHERE code = :code")
                .param("code", code).query(Long.class).single();
        long participations = jdbc.sql("SELECT COUNT(*) FROM activity_participations WHERE activity_id = :id")
                .param("id", activityId).query(Long.class).single();
        if (participations > 0) {
            throw BusinessException.conflict("ACTIVITY_IN_USE", "活动已有用户参与记录，不能删除，请改为停用");
        }
        if (activeActivityReservations(activityId, null) > 0) {
            throw BusinessException.conflict(
                    "ACTIVITY_STOCK_RESERVED", "活动还有待支付订单占用库存，请等待订单支付或取消后再删除");
        }
        jdbc.sql("UPDATE cart_items SET activity_id = NULL, price_override = NULL WHERE activity_id = :id")
                .param("id", activityId).update();
        jdbc.sql("DELETE FROM activity_products WHERE activity_id = :id")
                .param("id", activityId).update();
        jdbc.sql("DELETE FROM activities WHERE id = :id").param("id", activityId).update();
        audit(adminId, "DELETE_ACTIVITY", "ACTIVITY", code, activity);
    }

    public List<AdminActivityProduct> activityProducts(String activityCode) {
        requireActivityId(activityCode);
        return jdbc.sql("""
                SELECT p.product_code, p.name, p.spec, p.image_url, p.sale_price,
                       p.stock AS product_stock, p.status AS product_status,
                       ap.activity_price, ap.activity_stock, ap.per_user_limit, ap.sort_order
                FROM activity_products ap
                JOIN activities a ON a.id = ap.activity_id
                JOIN products p ON p.id = ap.product_id
                WHERE a.code = :activityCode
                ORDER BY ap.sort_order, ap.id
                """).param("activityCode", activityCode).query(AdminActivityProduct.class).list();
    }

    @Transactional
    public AdminActivityProduct saveActivityProduct(long adminId, String activityCode,
                                                    String productCode,
                                                    ActivityProductCommand command) {
        if (command.activityStock() == null || command.activityStock() < 0) {
            throw BusinessException.badRequest("INVALID_ACTIVITY_STOCK", "活动剩余库存不能小于零");
        }
        if (command.perUserLimit() != null && command.perUserLimit() < 1) {
            throw BusinessException.badRequest("INVALID_ACTIVITY_LIMIT", "每人限购数量必须大于零");
        }
        Long activityId = requireActivityId(activityCode);
        Long productId = jdbc.sql("SELECT id FROM products WHERE product_code = :code")
                .param("code", productCode).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
        long exists = jdbc.sql("""
                SELECT COUNT(*) FROM activity_products
                WHERE activity_id = :activityId AND product_id = :productId
                """).param("activityId", activityId).param("productId", productId)
                .query(Long.class).single();
        if (exists == 0) {
            jdbc.sql("""
                    INSERT INTO activity_products(activity_id, product_id, activity_price,
                        activity_stock, per_user_limit, sort_order)
                    VALUES (:activityId, :productId, :price, :stock, :limit, :sortOrder)
                    """).param("activityId", activityId).param("productId", productId)
                    .param("price", command.activityPrice()).param("stock", command.activityStock())
                    .param("limit", command.perUserLimit()).param("sortOrder", command.sortOrder()).update();
        } else {
            jdbc.sql("""
                    UPDATE activity_products SET activity_price = :price,
                        activity_stock = :stock, per_user_limit = :limit, sort_order = :sortOrder
                    WHERE activity_id = :activityId AND product_id = :productId
                    """).param("price", command.activityPrice()).param("stock", command.activityStock())
                    .param("limit", command.perUserLimit()).param("sortOrder", command.sortOrder())
                    .param("activityId", activityId).param("productId", productId).update();
        }
        audit(adminId, exists == 0 ? "ADD_ACTIVITY_PRODUCT" : "UPDATE_ACTIVITY_PRODUCT",
                "ACTIVITY_PRODUCT", activityCode + ":" + productCode, command);
        return activityProducts(activityCode).stream()
                .filter(item -> item.productCode().equals(productCode)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteActivityProduct(long adminId, String activityCode, String productCode) {
        Long activityId = requireActivityId(activityCode);
        Long productId = jdbc.sql("SELECT id FROM products WHERE product_code = :code")
                .param("code", productCode).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
        if (activeActivityReservations(activityId, productId) > 0) {
            throw BusinessException.conflict(
                    "ACTIVITY_STOCK_RESERVED", "该商品还有待支付订单占用活动库存，请等待订单支付或取消后再移除");
        }
        jdbc.sql("""
                UPDATE cart_items SET activity_id = NULL, price_override = NULL
                WHERE activity_id = :activityId AND product_id = :productId
                """).param("activityId", activityId).param("productId", productId).update();
        int deleted = jdbc.sql("""
                DELETE FROM activity_products
                WHERE activity_id = :activityId AND product_id = :productId
                """).param("activityId", activityId).param("productId", productId).update();
        if (deleted == 0) {
            throw BusinessException.notFound("ACTIVITY_PRODUCT_NOT_FOUND", "活动中没有该商品");
        }
        audit(adminId, "DELETE_ACTIVITY_PRODUCT", "ACTIVITY_PRODUCT",
                activityCode + ":" + productCode, "管理员移除活动商品");
    }

    private long activeActivityReservations(long activityId, Long productId) {
        String productCondition = productId == null ? "" : " AND oi.product_id = :productId";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT COUNT(*) FROM order_items oi JOIN shop_orders o ON o.id = oi.order_id
                WHERE oi.activity_id = :activityId AND o.status = 'WAIT_PAY'
                """ + productCondition).param("activityId", activityId);
        if (productId != null) query = query.param("productId", productId);
        return query.query(Long.class).single();
    }

    private Long requireActivityId(String activityCode) {
        return jdbc.sql("SELECT id FROM activities WHERE code = :code")
                .param("code", activityCode).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ACTIVITY_NOT_FOUND", "活动不存在"));
    }

    private void validateActivity(ActivityCommand command) {
        validateJson(command.rulesJson());
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw BusinessException.badRequest("INVALID_ACTIVITY_PERIOD", "活动结束时间必须晚于开始时间");
        }
    }

    public void updateAfterSale(long adminId, String afterSaleNo, String status, String reply,
                                BigDecimal requestedAmount) {
        AfterSaleProcessRow row = jdbc.sql("""
                SELECT a.id, o.order_no, a.status, a.refund_amount
                FROM after_sales a JOIN shop_orders o ON o.id = a.order_id
                WHERE a.after_sale_no = :no
                """).param("no", afterSaleNo).query(AfterSaleProcessRow.class).optional().orElse(null);
        if (row == null) {
            throw BusinessException.notFound("AFTER_SALE_NOT_FOUND", "售后申请不存在");
        }
        String target = status.toUpperCase();
        if (!List.of("PROCESSING", "APPROVED", "REJECTED").contains(target)) {
            throw BusinessException.badRequest("INVALID_AFTER_SALE_STATUS", "不支持的售后状态");
        }
        if ("APPROVED".equals(target)) {
            BigDecimal amount = requestedAmount == null ? row.refundAmount() : requestedAmount;
            try {
                paymentService.refund(row.orderNo(), amount,
                        reply == null || reply.isBlank() ? "售后退款" : reply);
                jdbc.sql("""
                        UPDATE after_sales SET status = 'APPROVED', refund_amount = :amount,
                            refunded_amount = :amount, refund_status = 'SUCCEEDED', refund_error = NULL,
                            merchant_reply = :reply, processed_at = CURRENT_TIMESTAMP,
                            refunded_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """).param("amount", amount).param("reply", reply).param("id", row.id()).update();
                addAfterSaleHistory(row.id(), "APPROVED", "SUCCEEDED",
                        "商家同意售后，退款 " + amount + " 元已提交成功", adminId);
            } catch (RuntimeException ex) {
                jdbc.sql("""
                        UPDATE after_sales SET status = 'PROCESSING', refund_amount = :amount,
                            refund_status = 'FAILED', refund_error = :error, merchant_reply = :reply,
                            processed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """).param("amount", amount).param("error", ex.getMessage())
                        .param("reply", reply).param("id", row.id()).update();
                addAfterSaleHistory(row.id(), "PROCESSING", "FAILED",
                        "退款失败，等待管理员重试：" + ex.getMessage(), adminId);
            }
        } else {
            jdbc.sql("""
                    UPDATE after_sales SET status = :status, merchant_reply = :reply,
                        refund_status = CASE WHEN :status = 'REJECTED' THEN 'NOT_REQUIRED' ELSE refund_status END,
                        processed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = :id
                    """).param("status", target).param("reply", reply).param("id", row.id()).update();
            addAfterSaleHistory(row.id(), target,
                    "REJECTED".equals(target) ? "NOT_REQUIRED" : "NOT_STARTED",
                    reply == null || reply.isBlank() ? "售后状态已更新" : reply, adminId);
        }
        audit(adminId, "UPDATE_AFTER_SALE", "AFTER_SALE", afterSaleNo,
                new AfterSaleUpdateAudit(target, requestedAmount, reply));
    }

    private void addAfterSaleHistory(long afterSaleId, String status, String refundStatus,
                                     String message, long operatorId) {
        jdbc.sql("""
                INSERT INTO after_sale_status_history(after_sale_id, status, refund_status, message, operator_user_id)
                VALUES (:id, :status, :refundStatus, :message, :operatorId)
                """).param("id", afterSaleId).param("status", status).param("refundStatus", refundStatus)
                .param("message", message).param("operatorId", operatorId).update();
    }

    private void audit(long adminId, String operation, String targetType, String targetId, Object detail) {
        jdbc.sql("""
                INSERT INTO admin_audit_logs(admin_user_id, operation, target_type, target_id, detail_json)
                VALUES (:adminId, :operation, :targetType, :targetId, :detail)
                """).param("adminId", adminId).param("operation", operation).param("targetType", targetType)
                .param("targetId", targetId).param("detail", json(detail)).update();
    }

    private void acceptOrderNotification(String orderNo) {
        jdbc.sql("""
                UPDATE admin_order_notifications SET status = 'ACCEPTED',
                    read_at = COALESCE(read_at, CURRENT_TIMESTAMP),
                    accepted_at = COALESCE(accepted_at, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                WHERE order_id = (SELECT id FROM shop_orders WHERE order_no = :orderNo)
                  AND status IN ('UNREAD', 'READ')
                """).param("orderNo", orderNo).update();
    }

    private void validateJson(String value) {
        try {
            objectMapper.readTree(value);
        } catch (JacksonException ex) {
            throw BusinessException.badRequest("INVALID_JSON_CONTENT", "组件或活动配置不是有效 JSON");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to serialize audit detail", ex);
        }
    }

    private AdminCoupon toAdminCoupon(CouponRow coupon) {
        LocalDateTime now = LocalDateTime.now();
        String status;
        if (!coupon.enabled()) {
            status = "DISABLED";
        } else if (!now.isBefore(coupon.endsAt())) {
            status = "EXPIRED";
        } else if (now.isBefore(coupon.startsAt())) {
            status = "NOT_STARTED";
        } else if (coupon.claimedCount() >= coupon.totalStock()) {
            status = "OUT_OF_STOCK";
        } else {
            status = "ACTIVE";
        }
        return new AdminCoupon(coupon.id(), coupon.code(), coupon.name(), coupon.amount(),
                coupon.minimumAmount(), coupon.totalStock(), coupon.claimedCount(),
                Math.max(0, coupon.totalStock() - coupon.claimedCount()), coupon.perUserLimit(),
                coupon.startsAt(), coupon.endsAt(), coupon.enabled(), status, coupon.createdAt());
    }

    public record ProductCommand(String categoryCode, String name, String subtitle, String description,
                                 String spec, String imageUrl, BigDecimal originalPrice, BigDecimal salePrice,
                                 BigDecimal memberPrice, int stock, String badge, String status, int sortOrder) {
    }

    public record AdminProduct(String productCode, String categoryCode, String categoryName, String name,
                               String subtitle, String description, String spec, String imageUrl, BigDecimal originalPrice,
                               BigDecimal salePrice, BigDecimal memberPrice, int stock, int sales,
                               String badge, String status, int sortOrder, LocalDateTime updatedAt) {
    }

    public record SkuCommand(String skuCode, String specName, BigDecimal originalPrice,
                             BigDecimal salePrice, BigDecimal memberPrice, int stock) {
    }

    public record AdminSku(String skuCode, String specName, BigDecimal originalPrice,
                           BigDecimal salePrice, BigDecimal memberPrice, int stock,
                           boolean enabled, int sortOrder) {
    }

    public record AdminOrder(String orderNo, String status, String nickname, String mobile,
                             BigDecimal payAmount, BigDecimal deliveryFee, String receiverName,
                             String receiverMobile, String receiverAddress, LocalDateTime createdAt,
                             LocalDateTime paidAt) {
    }

    public record DeliveryCommand(String name, BigDecimal freeThreshold, BigDecimal baseFee,
                                  BigDecimal maxDistanceKm, int promiseMinutes, String announcement) {
    }

    public record DeliveryConfig(long id, String name, BigDecimal freeThreshold, BigDecimal baseFee,
                                 BigDecimal maxDistanceKm, int promiseMinutes, String announcement,
                                 LocalDateTime updatedAt) {
    }

    public record Dashboard(long todayOrders, BigDecimal todaySales, long newUsers,
                            List<HotProduct> hotProducts) {
    }

    public record HotProduct(String productCode, String name, int sales, int stock) {
    }

    public record AdminUser(long id, String nickname, String mobile, String status, int points,
                            BigDecimal totalSpent, String memberLevelName, LocalDateTime createdAt) {
    }

    public record CouponCommand(String name, BigDecimal amount, BigDecimal minimumAmount,
                                int totalStock, LocalDateTime startsAt, LocalDateTime endsAt,
                                boolean enabled) {
    }

    private record CouponRow(long id, String code, String name, BigDecimal amount,
                             BigDecimal minimumAmount, int totalStock, int claimedCount,
                             int perUserLimit, LocalDateTime startsAt, LocalDateTime endsAt,
                             boolean enabled, LocalDateTime createdAt) {
    }

    private record CouponIssueDefinition(long id, String code, String name, int totalStock,
                                         int claimedCount, int perUserLimit, LocalDateTime startsAt,
                                         LocalDateTime endsAt, boolean enabled) {
    }

    private record OrderStatusRow(long userId, String status) {
    }

    private record CouponUser(long id, String nickname, String mobile) {
    }

    public record AdminCoupon(long id, String code, String name, BigDecimal amount,
                              BigDecimal minimumAmount, int totalStock, int claimedCount,
                              int remainingStock, int perUserLimit, LocalDateTime startsAt,
                              LocalDateTime endsAt, boolean enabled, String status,
                              LocalDateTime createdAt) {
    }

    public record CouponIssueResult(long userCouponId, String couponCode, String couponName,
                                    long userId, String nickname, String mobile, String status,
                                    LocalDateTime issuedAt) {
    }

    private record CouponIssueTarget(long userId, long userCouponId) {
    }

    public record CouponBatchResult(String batchNo, int totalCount, int successCount, int failedCount) {
    }

    public record CouponIssueRecordView(String batchNo, String couponCode, String couponName,
                                        String mobile, String nickname, String status,
                                        String failureReason, LocalDateTime createdAt) {
    }

    public record RefundCommand(BigDecimal amount, String reason) {
    }

    public record AfterSaleAdminView(String afterSaleNo, String orderNo, String nickname, String reason,
                                     String description, String status, BigDecimal refundAmount,
                                     String refundStatus, BigDecimal refundedAmount, String refundError,
                                     String merchantReply, long imageCount, LocalDateTime createdAt) {
    }

    private record AfterSaleProcessRow(long id, String orderNo, String status, BigDecimal refundAmount) {
    }

    private record AfterSaleUpdateAudit(String status, BigDecimal refundAmount, String reply) {
    }

    public record CategoryCommand(String parentCode, String name, String iconUrl, int sortOrder,
                                  boolean enabled) {
    }

    public record AdminCategory(String code, String name, String parentCode, String iconUrl,
                                int sortOrder, boolean enabled) {
    }

    public record HomeModuleCommand(String moduleType, String title, String contentJson,
                                    int sortOrder, boolean enabled) {
    }

    public record AdminHomeModule(long id, String moduleType, String title, String contentJson,
                                  int sortOrder, boolean enabled, LocalDateTime publishedAt,
                                  LocalDateTime updatedAt) {
    }

    public record ActivityCommand(String name, String activityType, String description, String rulesJson,
                                  LocalDateTime startsAt, LocalDateTime endsAt, boolean enabled) {
    }

    public record AdminActivity(String code, String name, String activityType, String description,
                                String rulesJson, LocalDateTime startsAt, LocalDateTime endsAt,
                                boolean enabled) {
    }

    public record ActivityProductCommand(BigDecimal activityPrice, Integer activityStock,
                                         Integer perUserLimit, int sortOrder) {
    }

    public record AdminActivityProduct(String productCode, String name, String spec,
                                       String imageUrl, BigDecimal salePrice, int productStock,
                                       String productStatus, BigDecimal activityPrice,
                                       Integer activityStock, Integer perUserLimit, int sortOrder) {
    }
}
