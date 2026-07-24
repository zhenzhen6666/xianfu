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
                       p.subtitle, p.spec, p.image_url, p.original_price, p.sale_price,
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
        return products("ALL").stream().filter(item -> item.productCode().equals(code)).findFirst().orElseThrow();
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
        if (!List.of("SHIPPING", "COMPLETED", "CANCELLED").contains(target)) {
            throw BusinessException.badRequest("INVALID_ORDER_STATUS", "不支持的订单状态");
        }
        Long userId = jdbc.sql("SELECT user_id FROM shop_orders WHERE order_no = :orderNo")
                .param("orderNo", orderNo).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
        if ("COMPLETED".equals(target)) {
            OrderService.OrderDetail result = orderService.confirm(userId, orderNo);
            audit(adminId, "UPDATE_ORDER_STATUS", "ORDER", orderNo, status);
            return result;
        }
        if ("CANCELLED".equals(target)) {
            OrderService.OrderDetail result = orderService.cancel(userId, orderNo, "商家取消订单");
            audit(adminId, "UPDATE_ORDER_STATUS", "ORDER", orderNo, status);
            return result;
        }
        int updated = jdbc.sql("""
                UPDATE shop_orders SET status = :status,
                    shipped_at = CASE WHEN :status = 'SHIPPING' THEN CURRENT_TIMESTAMP ELSE shipped_at END,
                    updated_at = CURRENT_TIMESTAMP WHERE order_no = :orderNo
                """).param("status", target).param("orderNo", orderNo).update();
        if (updated == 0) {
            throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        }
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
                WHERE paid_at >= CURRENT_DATE AND status IN ('SHIPPING','COMPLETED')
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
                       a.status, a.refund_amount, a.merchant_reply, a.created_at
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

    public List<AdminActivity> activities() {
        return jdbc.sql("""
                SELECT code, name, activity_type, description, rules_json, starts_at, ends_at, enabled
                FROM activities ORDER BY id
                """).query(AdminActivity.class).list();
    }

    @Transactional
    public AdminActivity updateActivity(long adminId, String code, ActivityCommand command) {
        validateJson(command.rulesJson());
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
    public void updateAfterSale(long adminId, String afterSaleNo, String status, String reply) {
        int updated = jdbc.sql("""
                UPDATE after_sales SET status = :status, merchant_reply = :reply,
                    updated_at = CURRENT_TIMESTAMP WHERE after_sale_no = :no
                """).param("status", status.toUpperCase()).param("reply", reply).param("no", afterSaleNo).update();
        if (updated == 0) {
            throw BusinessException.notFound("AFTER_SALE_NOT_FOUND", "售后申请不存在");
        }
        audit(adminId, "UPDATE_AFTER_SALE", "AFTER_SALE", afterSaleNo, status);
    }

    private void audit(long adminId, String operation, String targetType, String targetId, Object detail) {
        jdbc.sql("""
                INSERT INTO admin_audit_logs(admin_user_id, operation, target_type, target_id, detail_json)
                VALUES (:adminId, :operation, :targetType, :targetId, :detail)
                """).param("adminId", adminId).param("operation", operation).param("targetType", targetType)
                .param("targetId", targetId).param("detail", json(detail)).update();
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

    public record ProductCommand(String categoryCode, String name, String subtitle, String description,
                                 String spec, String imageUrl, BigDecimal originalPrice, BigDecimal salePrice,
                                 BigDecimal memberPrice, int stock, String badge, String status, int sortOrder) {
    }

    public record AdminProduct(String productCode, String categoryCode, String categoryName, String name,
                               String subtitle, String spec, String imageUrl, BigDecimal originalPrice,
                               BigDecimal salePrice, BigDecimal memberPrice, int stock, int sales,
                               String badge, String status, int sortOrder, LocalDateTime updatedAt) {
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

    public record RefundCommand(BigDecimal amount, String reason) {
    }

    public record AfterSaleAdminView(String afterSaleNo, String orderNo, String nickname, String reason,
                                     String description, String status, BigDecimal refundAmount,
                                     String merchantReply, LocalDateTime createdAt) {
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
}
