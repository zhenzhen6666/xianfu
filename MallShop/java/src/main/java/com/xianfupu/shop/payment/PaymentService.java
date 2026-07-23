package com.xianfupu.shop.payment;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.xianfupu.shop.common.BusinessException;
import com.xianfupu.shop.order.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final Map<String, PaymentProvider> providers;
    private final String mockCallbackSecret;

    public PaymentService(JdbcClient jdbc, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                          OrderService orderService, List<PaymentProvider> providers,
                          @Value("${app.payment.mock-callback-secret:local-mock-callback}") String mockCallbackSecret) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.providers = providers.stream().collect(Collectors.toMap(PaymentProvider::channel, Function.identity()));
        this.mockCallbackSecret = mockCallbackSecret;
    }

    @Transactional
    public PaymentView create(long userId, String orderNo, String requestedChannel) {
        String channel = requestedChannel == null || requestedChannel.isBlank()
                ? "MOCK" : requestedChannel.toUpperCase();
        PaymentProvider provider = providers.get(channel);
        if (provider == null) {
            throw BusinessException.badRequest("PAYMENT_CHANNEL_UNAVAILABLE", "支付渠道尚未配置：" + channel);
        }
        PayableOrder order = jdbc.sql("""
                SELECT id, order_no, user_id, status, pay_amount, pay_deadline
                FROM shop_orders WHERE order_no = :orderNo AND user_id = :userId FOR UPDATE
                """).param("orderNo", orderNo).param("userId", userId).query(PayableOrder.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
        if (!"WAIT_PAY".equals(order.status())) {
            throw BusinessException.conflict("ORDER_STATUS_INVALID", "当前订单不能发起支付");
        }
        if (order.payDeadline().isBefore(LocalDateTime.now())) {
            throw BusinessException.conflict("ORDER_PAYMENT_EXPIRED", "订单已超过支付时限");
        }
        PaymentRow existing = jdbc.sql("""
                SELECT id, payment_no, order_id, channel, status, amount, provider_trade_no, created_at
                FROM payments WHERE order_id = :orderId AND channel = :channel AND status = 'PENDING'
                ORDER BY id DESC LIMIT 1
                """).param("orderId", order.id()).param("channel", channel)
                .query(PaymentRow.class).optional().orElse(null);
        if (existing != null) {
            PaymentProvider.PaymentInstruction instruction = provider.create(
                    existing.paymentNo(), order.orderNo(), order.payAmount(), "鲜福铺订单 " + order.orderNo());
            return view(existing, instruction.payParams());
        }
        String paymentNo = "PAY" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        PaymentProvider.PaymentInstruction instruction = provider.create(
                paymentNo, order.orderNo(), order.payAmount(), "鲜福铺订单 " + order.orderNo());
        String requestJson = json(instruction.payParams());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO payments(payment_no, order_id, channel, status, amount, provider_trade_no, request_payload)
                    VALUES (?, ?, ?, 'PENDING', ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, paymentNo);
            ps.setLong(2, order.id());
            ps.setString(3, channel);
            ps.setBigDecimal(4, order.payAmount());
            ps.setString(5, instruction.providerTradeNo());
            ps.setString(6, requestJson);
            return ps;
        }, keyHolder);
        PaymentRow created = jdbc.sql("""
                SELECT id, payment_no, order_id, channel, status, amount, provider_trade_no, created_at
                FROM payments WHERE id = :id
                """).param("id", keyHolder.getKey().longValue()).query(PaymentRow.class).single();
        return view(created, instruction.payParams());
    }

    @Transactional
    public PaymentView mockCallback(String paymentNo, boolean success, String providerTradeNo,
                                    String callbackSecret) {
        if (!MessageDigest.isEqual(mockCallbackSecret.getBytes(StandardCharsets.UTF_8),
                callbackSecret.getBytes(StandardCharsets.UTF_8))) {
            throw BusinessException.forbidden("INVALID_CALLBACK_SIGNATURE", "支付回调签名无效");
        }
        PaymentRow payment = jdbc.sql("""
                SELECT id, payment_no, order_id, channel, status, amount, provider_trade_no, created_at
                FROM payments WHERE payment_no = :paymentNo FOR UPDATE
                """).param("paymentNo", paymentNo).query(PaymentRow.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PAYMENT_NOT_FOUND", "支付流水不存在"));
        if ("PAID".equals(payment.status())) {
            return view(payment, Map.of("idempotent", true));
        }
        if (!"PENDING".equals(payment.status())) {
            throw BusinessException.conflict("PAYMENT_STATUS_INVALID", "支付流水当前状态不能回调");
        }
        String targetStatus = success ? "PAID" : "FAILED";
        jdbc.sql("""
                UPDATE payments SET status = :status, provider_trade_no = :tradeNo,
                    callback_payload = :payload, paid_at = CASE WHEN :success = TRUE THEN CURRENT_TIMESTAMP ELSE NULL END,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("status", targetStatus).param("tradeNo", providerTradeNo)
                .param("payload", json(Map.of("success", success, "providerTradeNo",
                        providerTradeNo == null ? "" : providerTradeNo)))
                .param("success", success).param("id", payment.id()).update();
        if (success) {
            orderService.markPaid(payment.orderId());
        }
        PaymentRow updated = jdbc.sql("""
                SELECT id, payment_no, order_id, channel, status, amount, provider_trade_no, created_at
                FROM payments WHERE id = :id
                """).param("id", payment.id()).query(PaymentRow.class).single();
        return view(updated, Map.of("callbackAccepted", true));
    }

    public List<PaymentView> orderPayments(long userId, String orderNo) {
        Long orderId = jdbc.sql("SELECT id FROM shop_orders WHERE order_no = :orderNo AND user_id = :userId")
                .param("orderNo", orderNo).param("userId", userId).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在"));
        return jdbc.sql("""
                SELECT id, payment_no, order_id, channel, status, amount, provider_trade_no, created_at
                FROM payments WHERE order_id = :orderId ORDER BY id DESC
                """).param("orderId", orderId).query(PaymentRow.class).list().stream()
                .map(row -> view(row, null)).toList();
    }

    @Transactional
    public PaymentView refund(String orderNo, BigDecimal requestedAmount, String reason) {
        PaymentRow payment = jdbc.sql("""
                SELECT p.id, p.payment_no, p.order_id, p.channel, p.status, p.amount,
                       p.provider_trade_no, p.created_at
                FROM payments p JOIN shop_orders o ON o.id = p.order_id
                WHERE o.order_no = :orderNo AND p.status IN ('PAID','PARTIAL_REFUND')
                ORDER BY p.id DESC LIMIT 1 FOR UPDATE
                """).param("orderNo", orderNo).query(PaymentRow.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PAID_PAYMENT_NOT_FOUND", "没有可退款的支付流水"));
        BigDecimal alreadyRefunded = jdbc.sql("SELECT refunded_amount FROM payments WHERE id = :id")
                .param("id", payment.id()).query(BigDecimal.class).single();
        BigDecimal available = payment.amount().subtract(alreadyRefunded);
        BigDecimal amount = requestedAmount == null ? available : requestedAmount;
        if (amount.signum() <= 0 || amount.compareTo(available) > 0) {
            throw BusinessException.badRequest("INVALID_REFUND_AMOUNT", "退款金额超出可退金额");
        }
        PaymentProvider provider = providers.get(payment.channel());
        if (provider == null) {
            throw BusinessException.conflict("PAYMENT_CHANNEL_UNAVAILABLE", "原支付渠道当前不可用");
        }
        provider.refund(payment.paymentNo(), amount, reason);
        BigDecimal refunded = alreadyRefunded.add(amount);
        String status = refunded.compareTo(payment.amount()) == 0 ? "REFUNDED" : "PARTIAL_REFUND";
        jdbc.sql("""
                UPDATE payments SET refunded_amount = :refunded, status = :status,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("refunded", refunded).param("status", status).param("id", payment.id()).update();
        if ("REFUNDED".equals(status)) {
            jdbc.sql("UPDATE shop_orders SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                    .param("id", payment.orderId()).update();
        }
        PaymentRow updated = jdbc.sql("""
                SELECT id, payment_no, order_id, channel, status, amount, provider_trade_no, created_at
                FROM payments WHERE id = :id
                """).param("id", payment.id()).query(PaymentRow.class).single();
        return view(updated, Map.of("refundedAmount", refunded));
    }

    private PaymentView view(PaymentRow row, Map<String, Object> payParams) {
        return new PaymentView(row.paymentNo(), row.channel(), row.status(), row.amount(),
                row.providerTradeNo(), payParams, row.createdAt());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to serialize payment payload", ex);
        }
    }

    private record PayableOrder(long id, String orderNo, long userId, String status,
                                BigDecimal payAmount, LocalDateTime payDeadline) {
    }

    private record PaymentRow(long id, String paymentNo, long orderId, String channel, String status,
                              BigDecimal amount, String providerTradeNo, LocalDateTime createdAt) {
    }

    public record PaymentView(String paymentNo, String channel, String status, BigDecimal amount,
                              String providerTradeNo, Map<String, Object> payParams, LocalDateTime createdAt) {
    }
}
