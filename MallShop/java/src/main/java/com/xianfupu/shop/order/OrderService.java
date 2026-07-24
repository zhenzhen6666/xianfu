package com.xianfupu.shop.order;

import com.xianfupu.shop.catalog.CatalogService;
import com.xianfupu.shop.common.BusinessException;
import com.xianfupu.shop.user.AddressService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {
    private static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final AddressService addressService;
    private final CatalogService catalogService;
    private final int paymentTimeoutMinutes;

    public OrderService(JdbcClient jdbc, JdbcTemplate jdbcTemplate, AddressService addressService,
                        CatalogService catalogService,
                        @Value("${app.order.payment-timeout-minutes:15}") int paymentTimeoutMinutes) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.addressService = addressService;
        this.catalogService = catalogService;
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
    }

    public OrderPreview preview(long userId, OrderCommand command) {
        return calculate(userId, command, false);
    }

    @Transactional
    public OrderDetail create(long userId, OrderCommand command) {
        OrderPreview preview = calculate(userId, command, true);
        String orderNo = nextOrderNo();
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(paymentTimeoutMinutes);
        AddressService.AddressView address = addressService.getOwned(userId, command.addressId());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO shop_orders(order_no, user_id, address_id, status, subtotal, delivery_fee,
                        coupon_discount, member_discount, points_used, points_discount, pay_amount,
                        receiver_name, receiver_mobile, receiver_address, note, pay_deadline)
                    VALUES (?, ?, ?, 'WAIT_PAY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            int i = 1;
            ps.setString(i++, orderNo);
            ps.setLong(i++, userId);
            ps.setLong(i++, command.addressId());
            ps.setBigDecimal(i++, preview.subtotal());
            ps.setBigDecimal(i++, preview.deliveryFee());
            ps.setBigDecimal(i++, preview.couponDiscount());
            ps.setBigDecimal(i++, preview.memberDiscount());
            ps.setInt(i++, preview.pointsUsed());
            ps.setBigDecimal(i++, preview.pointsDiscount());
            ps.setBigDecimal(i++, preview.payAmount());
            ps.setString(i++, address.name());
            ps.setString(i++, address.phone());
            ps.setString(i++, address.detail());
            ps.setString(i++, command.note());
            ps.setObject(i, deadline);
            return ps;
        }, keyHolder);
        long orderId = keyHolder.getKey().longValue();
        for (CheckoutItem item : preview.items()) {
            int changed = jdbc.sql("""
                    UPDATE products SET stock = stock - :quantity, version = version + 1,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :productId AND stock >= :quantity AND status = 'ON_SALE'
                    """).param("quantity", item.count()).param("productId", item.productId()).update();
            if (changed == 0) {
                throw BusinessException.conflict("INSUFFICIENT_STOCK", item.name() + " 库存不足");
            }
            jdbc.sql("""
                    INSERT INTO order_items(order_id, product_id, product_code, product_name, spec,
                        image_url, unit_price, quantity, line_amount)
                    VALUES (:orderId, :productId, :code, :name, :spec, :image, :price, :quantity, :amount)
                    """).param("orderId", orderId).param("productId", item.productId())
                    .param("code", item.id()).param("name", item.name()).param("spec", item.spec())
                    .param("image", item.image()).param("price", item.price()).param("quantity", item.count())
                    .param("amount", item.lineAmount()).update();
            jdbc.sql("DELETE FROM cart_items WHERE user_id = :userId AND product_id = :productId")
                    .param("userId", userId).param("productId", item.productId()).update();
        }
        if (command.userCouponId() != null) {
            int changed = jdbc.sql("""
                    UPDATE user_coupons SET status = 'USED', order_id = :orderId, used_at = CURRENT_TIMESTAMP
                    WHERE id = :couponId AND user_id = :userId AND status = 'AVAILABLE'
                    """).param("orderId", orderId).param("couponId", command.userCouponId())
                    .param("userId", userId).update();
            if (changed == 0) {
                throw BusinessException.conflict("COUPON_UNAVAILABLE", "优惠券已被使用或不可用");
            }
        }
        if (preview.pointsUsed() > 0) {
            int changed = jdbc.sql("""
                    UPDATE users SET points = points - :points, updated_at = CURRENT_TIMESTAMP
                    WHERE id = :userId AND points >= :points
                    """).param("points", preview.pointsUsed()).param("userId", userId).update();
            if (changed == 0) {
                throw BusinessException.conflict("POINTS_CHANGED", "积分余额发生变化，请重新结算");
            }
            int balance = jdbc.sql("SELECT points FROM users WHERE id = :userId")
                    .param("userId", userId).query(Integer.class).single();
            insertPointTransaction(userId, orderId, -preview.pointsUsed(), balance, "ORDER_DEDUCTION", "订单积分抵扣");
        }
        return detail(userId, orderNo);
    }

    public List<OrderSummary> list(long userId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        String condition = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? "" : " AND status = :status";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT order_no, status, subtotal, delivery_fee, coupon_discount, points_discount,
                       pay_amount, created_at, pay_deadline, paid_at, completed_at
                FROM shop_orders WHERE user_id = :userId
                """ + condition + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
                .param("userId", userId).param("limit", safeSize).param("offset", safePage * safeSize);
        if (!condition.isBlank()) {
            query = query.param("status", normalizeStatus(status));
        }
        return query.query(OrderSummary.class).list();
    }

    public OrderDetail detail(long userId, String orderNo) {
        OrderRow order = jdbc.sql("""
                SELECT id, order_no, status, subtotal, delivery_fee, coupon_discount, member_discount,
                       points_used, points_discount, pay_amount, receiver_name, receiver_mobile,
                       receiver_address, note, pay_deadline, paid_at, shipped_at, completed_at,
                       cancelled_at, cancel_reason, created_at
                FROM shop_orders WHERE order_no = :orderNo AND user_id = :userId
                """).param("orderNo", orderNo).param("userId", userId).query(OrderRow.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
        return toDetail(order);
    }

    public OrderDetail adminDetail(String orderNo) {
        OrderRow order = jdbc.sql("""
                SELECT id, order_no, status, subtotal, delivery_fee, coupon_discount, member_discount,
                       points_used, points_discount, pay_amount, receiver_name, receiver_mobile,
                       receiver_address, note, pay_deadline, paid_at, shipped_at, completed_at,
                       cancelled_at, cancel_reason, created_at
                FROM shop_orders WHERE order_no = :orderNo
                """).param("orderNo", orderNo).query(OrderRow.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
        return toDetail(order);
    }

    @Transactional
    public OrderDetail cancel(long userId, String orderNo, String reason) {
        OrderIdentity order = ownedOrderForUpdate(userId, orderNo);
        cancelLocked(order, reason == null || reason.isBlank() ? "用户取消" : reason);
        return detail(userId, orderNo);
    }

    @Transactional
    public OrderDetail confirm(long userId, String orderNo) {
        OrderIdentity order = ownedOrderForUpdate(userId, orderNo);
        if (!"SHIPPING".equals(order.status())) {
            throw BusinessException.conflict("ORDER_STATUS_INVALID", "当前订单不能确认收货");
        }
        int updated = jdbc.sql("""
                UPDATE shop_orders SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id AND status = 'SHIPPING'
                """).param("id", order.id()).update();
        if (updated == 1) {
            BigDecimal multiplier = jdbc.sql("""
                    SELECT ml.points_multiplier FROM users u JOIN member_levels ml ON ml.id = u.member_level_id
                    WHERE u.id = :userId
                    """).param("userId", userId).query(BigDecimal.class).single();
            int earned = order.payAmount().multiply(multiplier).setScale(0, RoundingMode.DOWN).intValue();
            jdbc.sql("""
                    UPDATE users SET points = points + :earned, total_spent = total_spent + :paid,
                        updated_at = CURRENT_TIMESTAMP WHERE id = :userId
                    """).param("earned", earned).param("paid", order.payAmount()).param("userId", userId).update();
            int balance = jdbc.sql("SELECT points FROM users WHERE id = :userId")
                    .param("userId", userId).query(Integer.class).single();
            insertPointTransaction(userId, order.id(), earned, balance, "ORDER_REWARD", "确认收货赠送积分");
            upgradeMember(userId);
        }
        return detail(userId, orderNo);
    }

    @Transactional
    public void cancelExpired(long orderId) {
        OrderIdentity order = jdbc.sql("""
                SELECT id, order_no, user_id, status, pay_amount FROM shop_orders WHERE id = :id FOR UPDATE
                """).param("id", orderId).query(OrderIdentity.class).optional().orElse(null);
        if (order != null && "WAIT_PAY".equals(order.status())) {
            cancelLocked(order, "超过 15 分钟未支付，系统自动取消");
        }
    }

    public List<Long> expiredOrderIds() {
        return jdbc.sql("""
                SELECT id FROM shop_orders WHERE status = 'WAIT_PAY' AND pay_deadline < CURRENT_TIMESTAMP
                ORDER BY id LIMIT 200
                """).query(Long.class).list();
    }

    @Transactional
    public void markPaid(long orderId) {
        int updated = jdbc.sql("""
                UPDATE shop_orders SET status = 'SHIPPING', paid_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id AND status = 'WAIT_PAY'
                """).param("id", orderId).update();
        if (updated != 1) {
            throw BusinessException.conflict("ORDER_STATUS_INVALID", "订单已取消或当前状态不能支付");
        }
        jdbc.sql("""
                UPDATE products p SET sales = sales + (
                    SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = :orderId AND oi.product_id = p.id
                ) WHERE EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = :orderId AND oi.product_id = p.id)
                """).param("orderId", orderId).update();
    }

    private OrderPreview calculate(long userId, OrderCommand command, boolean lockProducts) {
        AddressService.AddressView address = addressService.getOwned(userId, command.addressId());
        CatalogService.DeliveryConfig delivery = catalogService.deliveryConfig();
        if (address.distanceKm() != null && address.distanceKm().compareTo(delivery.maxDistanceKm()) > 0) {
            throw BusinessException.badRequest("OUT_OF_DELIVERY_RANGE", "收货地址超出 " + delivery.maxDistanceKm() + " 公里配送范围");
        }
        String lock = lockProducts ? " FOR UPDATE" : "";
        List<CheckoutItem> items = jdbc.sql("""
                SELECT p.id AS product_id, p.product_code AS id, p.name, p.spec, p.image_url AS image,
                       COALESCE(ci.price_override,
                           CASE WHEN ml.code <> 'NORMAL' AND p.member_price IS NOT NULL
                                THEN p.member_price ELSE p.sale_price END) AS price,
                       p.sale_price, p.stock, ci.quantity AS count,
                       COALESCE(ci.price_override,
                           CASE WHEN ml.code <> 'NORMAL' AND p.member_price IS NOT NULL
                                THEN p.member_price ELSE p.sale_price END) * ci.quantity AS line_amount
                FROM cart_items ci JOIN products p ON p.id = ci.product_id
                JOIN users u ON u.id = ci.user_id JOIN member_levels ml ON ml.id = u.member_level_id
                WHERE ci.user_id = :userId AND ci.selected = TRUE AND p.status = 'ON_SALE'
                ORDER BY ci.id
                """ + lock).param("userId", userId).query(CheckoutItem.class).list();
        if (items.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_CHECKOUT", "请先选择要结算的商品");
        }
        for (CheckoutItem item : items) {
            if (item.count() > item.stock()) {
                throw BusinessException.conflict("INSUFFICIENT_STOCK", item.name() + " 库存不足");
            }
        }
        BigDecimal subtotal = money(items.stream().map(CheckoutItem::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal rawSaleTotal = items.stream()
                .map(item -> item.salePrice().multiply(BigDecimal.valueOf(item.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal memberDiscount = money(rawSaleTotal.subtract(subtotal).max(BigDecimal.ZERO));
        BigDecimal deliveryFee = subtotal.compareTo(delivery.freeThreshold()) < 0 ? delivery.baseFee() : BigDecimal.ZERO;
        BigDecimal couponDiscount = couponDiscount(userId, command.userCouponId(), subtotal);
        int balance = jdbc.sql("SELECT points FROM users WHERE id = :userId")
                .param("userId", userId).query(Integer.class).single();
        int requestedPoints = Math.max(0, Optional.ofNullable(command.pointsToUse()).orElse(0));
        int maxByAmount = subtotal.add(deliveryFee).subtract(couponDiscount)
                .max(BigDecimal.ZERO).multiply(POINTS_PER_YUAN).setScale(0, RoundingMode.DOWN).intValue();
        int pointsUsed = Math.min(Math.min(requestedPoints, balance), maxByAmount);
        BigDecimal pointsDiscount = money(BigDecimal.valueOf(pointsUsed).divide(POINTS_PER_YUAN, 2, RoundingMode.DOWN));
        BigDecimal payAmount = money(subtotal.add(deliveryFee).subtract(couponDiscount).subtract(pointsDiscount).max(BigDecimal.ZERO));
        return new OrderPreview(items, subtotal, deliveryFee, couponDiscount, memberDiscount,
                pointsUsed, pointsDiscount, payAmount, delivery.freeThreshold(), delivery.maxDistanceKm(),
                delivery.promiseMinutes());
    }

    private BigDecimal couponDiscount(long userId, Long userCouponId, BigDecimal subtotal) {
        if (userCouponId == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        CouponForOrder coupon = jdbc.sql("""
                SELECT uc.id, c.amount, c.minimum_amount
                FROM user_coupons uc JOIN coupons c ON c.id = uc.coupon_id
                WHERE uc.id = :id AND uc.user_id = :userId AND uc.status = 'AVAILABLE'
                  AND c.enabled = TRUE AND CURRENT_TIMESTAMP BETWEEN c.starts_at AND c.ends_at
                """).param("id", userCouponId).param("userId", userId).query(CouponForOrder.class).optional()
                .orElseThrow(() -> BusinessException.badRequest("COUPON_UNAVAILABLE", "优惠券不可用"));
        if (subtotal.compareTo(coupon.minimumAmount()) < 0) {
            throw BusinessException.badRequest("COUPON_THRESHOLD_NOT_MET", "商品金额未达到优惠券使用门槛");
        }
        return money(coupon.amount().min(subtotal));
    }

    private void cancelLocked(OrderIdentity order, String reason) {
        if (!"WAIT_PAY".equals(order.status())) {
            throw BusinessException.conflict("ORDER_STATUS_INVALID", "只有待支付订单可以取消");
        }
        jdbc.sql("""
                UPDATE shop_orders SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP,
                    cancel_reason = :reason, updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("reason", reason).param("id", order.id()).update();
        List<RestoreItem> items = jdbc.sql("SELECT product_id, quantity FROM order_items WHERE order_id = :orderId")
                .param("orderId", order.id()).query(RestoreItem.class).list();
        for (RestoreItem item : items) {
            jdbc.sql("UPDATE products SET stock = stock + :quantity, version = version + 1 WHERE id = :productId")
                    .param("quantity", item.quantity()).param("productId", item.productId()).update();
        }
        jdbc.sql("""
                UPDATE user_coupons SET status = 'AVAILABLE', order_id = NULL, used_at = NULL
                WHERE order_id = :orderId AND status = 'USED'
                """).param("orderId", order.id()).update();
        Integer points = jdbc.sql("SELECT points_used FROM shop_orders WHERE id = :orderId")
                .param("orderId", order.id()).query(Integer.class).single();
        if (points > 0) {
            jdbc.sql("UPDATE users SET points = points + :points WHERE id = :userId")
                    .param("points", points).param("userId", order.userId()).update();
            int balance = jdbc.sql("SELECT points FROM users WHERE id = :userId")
                    .param("userId", order.userId()).query(Integer.class).single();
            insertPointTransaction(order.userId(), order.id(), points, balance, "ORDER_CANCEL_RETURN", "取消订单返还积分");
        }
    }

    private OrderIdentity ownedOrderForUpdate(long userId, String orderNo) {
        return jdbc.sql("""
                SELECT id, order_no, user_id, status, pay_amount FROM shop_orders
                WHERE order_no = :orderNo AND user_id = :userId FOR UPDATE
                """).param("orderNo", orderNo).param("userId", userId).query(OrderIdentity.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
    }

    private OrderDetail toDetail(OrderRow order) {
        List<OrderItemView> items = jdbc.sql("""
                SELECT product_code AS id, product_name AS name, spec, image_url AS image,
                       unit_price AS price, quantity AS count, line_amount
                FROM order_items WHERE order_id = :orderId ORDER BY id
                """).param("orderId", order.id()).query(OrderItemView.class).list();
        return new OrderDetail(order.orderNo(), order.status(), statusText(order.status()), items,
                order.subtotal(), order.deliveryFee(), order.couponDiscount(), order.memberDiscount(),
                order.pointsUsed(), order.pointsDiscount(), order.payAmount(), order.receiverName(),
                order.receiverMobile(), order.receiverAddress(), order.note(), order.payDeadline(), order.paidAt(),
                order.shippedAt(), order.completedAt(), order.cancelledAt(), order.cancelReason(), order.createdAt());
    }

    private void upgradeMember(long userId) {
        jdbc.sql("""
        UPDATE users SET member_level_id = (
            SELECT id FROM member_levels WHERE min_spend <=
                (SELECT t.total_spent FROM (SELECT total_spent FROM users WHERE id = :userId) t)
            ORDER BY min_spend DESC LIMIT 1
        ), updated_at = CURRENT_TIMESTAMP WHERE id = :userId
        """)
                .param("userId", userId)
                .update();
    }

    private void insertPointTransaction(long userId, long orderId, int change, int balance,
                                        String type, String remark) {
        jdbc.sql("""
                INSERT INTO point_transactions(user_id, order_id, change_amount, balance_after, transaction_type, remark)
                VALUES (:userId, :orderId, :change, :balance, :type, :remark)
                """).param("userId", userId).param("orderId", orderId).param("change", change)
                .param("balance", balance).param("type", type).param("remark", remark).update();
    }

    private String nextOrderNo() {
        return "XF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String normalizeStatus(String status) {
        return switch (status.toLowerCase()) {
            case "wait_pay" -> "WAIT_PAY";
            case "shipping" -> "SHIPPING";
            case "complete", "completed" -> "COMPLETED";
            case "cancel", "cancelled" -> "CANCELLED";
            default -> status.toUpperCase();
        };
    }

    private String statusText(String status) {
        return switch (status) {
            case "WAIT_PAY" -> "待支付";
            case "SHIPPING" -> "待配送";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "REFUNDED" -> "已退款";
            default -> status;
        };
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record OrderCommand(long addressId, Long userCouponId, Integer pointsToUse, String note) {
    }

    public record CheckoutItem(long productId, String id, String name, String spec, String image,
                               BigDecimal price, BigDecimal salePrice, int stock, int count,
                               BigDecimal lineAmount) {
    }

    public record OrderPreview(List<CheckoutItem> items, BigDecimal subtotal, BigDecimal deliveryFee,
                               BigDecimal couponDiscount, BigDecimal memberDiscount, int pointsUsed,
                               BigDecimal pointsDiscount, BigDecimal payAmount, BigDecimal freeDeliveryThreshold,
                               BigDecimal maxDistanceKm, int promiseMinutes) {
    }

    public record OrderSummary(String orderNo, String status, BigDecimal subtotal, BigDecimal deliveryFee,
                               BigDecimal couponDiscount, BigDecimal pointsDiscount, BigDecimal payAmount,
                               LocalDateTime createdAt, LocalDateTime payDeadline, LocalDateTime paidAt,
                               LocalDateTime completedAt) {
    }

    public record OrderItemView(String id, String name, String spec, String image, BigDecimal price,
                                int count, BigDecimal lineAmount) {
    }

    public record OrderDetail(String orderNo, String status, String statusText, List<OrderItemView> items,
                              BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal couponDiscount,
                              BigDecimal memberDiscount, int pointsUsed, BigDecimal pointsDiscount,
                              BigDecimal payAmount, String receiverName, String receiverMobile,
                              String receiverAddress, String note, LocalDateTime payDeadline,
                              LocalDateTime paidAt, LocalDateTime shippedAt, LocalDateTime completedAt,
                              LocalDateTime cancelledAt, String cancelReason, LocalDateTime createdAt) {
    }

    private record CouponForOrder(long id, BigDecimal amount, BigDecimal minimumAmount) {
    }

    private record OrderIdentity(long id, String orderNo, long userId, String status, BigDecimal payAmount) {
    }

    private record RestoreItem(long productId, int quantity) {
    }

    private record OrderRow(long id, String orderNo, String status, BigDecimal subtotal,
                            BigDecimal deliveryFee, BigDecimal couponDiscount, BigDecimal memberDiscount,
                            int pointsUsed, BigDecimal pointsDiscount, BigDecimal payAmount,
                            String receiverName, String receiverMobile, String receiverAddress, String note,
                            LocalDateTime payDeadline, LocalDateTime paidAt, LocalDateTime shippedAt,
                            LocalDateTime completedAt, LocalDateTime cancelledAt, String cancelReason,
                            LocalDateTime createdAt) {
    }
}
