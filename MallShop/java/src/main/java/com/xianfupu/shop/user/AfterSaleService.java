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
        if (!List.of("SHIPPING", "DELIVERED", "COMPLETED").contains(order.status())) {
            throw BusinessException.conflict("AFTER_SALE_NOT_ALLOWED", "当前订单状态不能申请售后");
        }
        long existing = jdbc.sql("""
                SELECT COUNT(*) FROM after_sales
                WHERE order_id = :orderId AND status IN ('PENDING','PROCESSING','APPROVED')
                """).param("orderId", order.id()).query(Long.class).single();
        if (existing > 0) {
            throw BusinessException.conflict("AFTER_SALE_EXISTS", "该订单已有处理中售后申请");
        }
        String no = "AS" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        jdbc.sql("""
                INSERT INTO after_sales(after_sale_no, order_id, user_id, reason, description, refund_amount)
                VALUES (:no, :orderId, :userId, :reason, :description, :amount)
                """).param("no", no).param("orderId", order.id()).param("userId", userId)
                .param("reason", reason).param("description", description).param("amount", order.payAmount()).update();
        Long afterSaleId = jdbc.sql("SELECT id FROM after_sales WHERE after_sale_no = :no")
                .param("no", no).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO after_sale_status_history(after_sale_id, status, refund_status, message, operator_user_id)
                VALUES (:id, 'PENDING', 'NOT_STARTED', '用户提交售后申请', :userId)
                """).param("id", afterSaleId).param("userId", userId).update();
        return get(userId, no);
    }

    public List<AfterSaleView> list(long userId) {
        return jdbc.sql(afterSaleSql() + " WHERE a.user_id = :userId ORDER BY a.created_at DESC")
                .param("userId", userId).query(AfterSaleRow.class).list().stream()
                .map(row -> row.withHistory(history(row.id()))).toList();
    }

    public AfterSaleView get(long userId, String afterSaleNo) {
        AfterSaleRow row = jdbc.sql(afterSaleSql() + " WHERE a.user_id = :userId AND a.after_sale_no = :no")
                .param("userId", userId).param("no", afterSaleNo).query(AfterSaleRow.class).optional()
                .orElseThrow(() -> BusinessException.notFound("AFTER_SALE_NOT_FOUND", "售后申请不存在"));
        return row.withHistory(history(row.id()));
    }

    private List<AfterSaleHistoryView> history(long afterSaleId) {
        return jdbc.sql("""
                SELECT status, refund_status, message, created_at
                FROM after_sale_status_history WHERE after_sale_id = :id ORDER BY id
                """).param("id", afterSaleId).query(AfterSaleHistoryView.class).list();
    }

    private String afterSaleSql() {
        return """
                SELECT a.id, a.after_sale_no, o.order_no, a.reason, a.description, a.status,
                       a.refund_amount, a.refund_status, a.refunded_amount, a.refund_error,
                       a.merchant_reply, a.processed_at, a.refunded_at, a.created_at, a.updated_at
                FROM after_sales a JOIN shop_orders o ON o.id = a.order_id
                """;
    }

    private record OrderForAfterSale(long id, BigDecimal payAmount, String status) {
    }

    private record AfterSaleRow(long id, String afterSaleNo, String orderNo, String reason,
                                String description, String status, BigDecimal refundAmount,
                                String refundStatus, BigDecimal refundedAmount, String refundError,
                                String merchantReply, LocalDateTime processedAt, LocalDateTime refundedAt,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        AfterSaleView withHistory(List<AfterSaleHistoryView> history) {
            return new AfterSaleView(afterSaleNo, orderNo, reason, description, status, refundAmount,
                    refundStatus, refundedAmount, refundError, merchantReply, processedAt,
                    refundedAt, createdAt, updatedAt, history);
        }
    }

    public record AfterSaleView(String afterSaleNo, String orderNo, String reason, String description,
                                String status, BigDecimal refundAmount, String refundStatus,
                                BigDecimal refundedAmount, String refundError, String merchantReply,
                                LocalDateTime processedAt, LocalDateTime refundedAt,
                                LocalDateTime createdAt, LocalDateTime updatedAt,
                                List<AfterSaleHistoryView> history) {
    }

    public record AfterSaleHistoryView(String status, String refundStatus, String message,
                                       LocalDateTime createdAt) {
    }
}
