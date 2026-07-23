package com.xianfupu.shop.user;

import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AfterSaleService {
    private final JdbcClient jdbc;

    public AfterSaleService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public AfterSaleView create(long userId, String orderNo, String reason, String description) {
        OrderForAfterSale order = jdbc.sql("""
                SELECT id, pay_amount, status FROM shop_orders WHERE order_no = :orderNo AND user_id = :userId
                """).param("orderNo", orderNo).param("userId", userId).query(OrderForAfterSale.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
        if (!List.of("SHIPPING", "COMPLETED").contains(order.status())) {
            throw BusinessException.conflict("AFTER_SALE_NOT_ALLOWED", "当前订单状态不能申请售后");
        }
        long existing = jdbc.sql("SELECT COUNT(*) FROM after_sales WHERE order_id = :orderId AND status IN ('PENDING','APPROVED')")
                .param("orderId", order.id()).query(Long.class).single();
        if (existing > 0) {
            throw BusinessException.conflict("AFTER_SALE_EXISTS", "该订单已有处理中售后申请");
        }
        String no = "AS" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        jdbc.sql("""
                INSERT INTO after_sales(after_sale_no, order_id, user_id, reason, description, refund_amount)
                VALUES (:no, :orderId, :userId, :reason, :description, :amount)
                """).param("no", no).param("orderId", order.id()).param("userId", userId)
                .param("reason", reason).param("description", description).param("amount", order.payAmount()).update();
        return get(userId, no);
    }

    public List<AfterSaleView> list(long userId) {
        return jdbc.sql(afterSaleSql() + " WHERE a.user_id = :userId ORDER BY a.created_at DESC")
                .param("userId", userId).query(AfterSaleView.class).list();
    }

    public AfterSaleView get(long userId, String afterSaleNo) {
        return jdbc.sql(afterSaleSql() + " WHERE a.user_id = :userId AND a.after_sale_no = :no")
                .param("userId", userId).param("no", afterSaleNo).query(AfterSaleView.class).optional()
                .orElseThrow(() -> BusinessException.notFound("AFTER_SALE_NOT_FOUND", "售后申请不存在"));
    }

    private String afterSaleSql() {
        return """
                SELECT a.after_sale_no, o.order_no, a.reason, a.description, a.status,
                       a.refund_amount, a.merchant_reply, a.created_at, a.updated_at
                FROM after_sales a JOIN shop_orders o ON o.id = a.order_id
                """;
    }

    private record OrderForAfterSale(long id, BigDecimal payAmount, String status) {
    }

    public record AfterSaleView(String afterSaleNo, String orderNo, String reason, String description,
                                String status, BigDecimal refundAmount, String merchantReply,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
