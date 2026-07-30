package com.xianfupu.shop.admin;

import com.xianfupu.shop.common.BusinessException;
import com.xianfupu.shop.order.OrderPaidEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminOrderNotificationService {
    private final JdbcClient jdbc;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public AdminOrderNotificationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SseEmitter subscribe() {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(connectionId, emitter);
        emitter.onCompletion(() -> emitters.remove(connectionId));
        emitter.onTimeout(() -> emitters.remove(connectionId));
        emitter.onError(error -> emitters.remove(connectionId));
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("connected", true, "time", LocalDateTime.now())));
        } catch (IOException ex) {
            emitters.remove(connectionId);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public List<OrderNotificationView> activeNotifications() {
        return jdbc.sql("""
                SELECT o.order_no, n.status, u.nickname, u.mobile, o.pay_amount,
                       o.receiver_name, o.receiver_mobile, o.receiver_address,
                       o.paid_at, n.created_at
                FROM admin_order_notifications n
                JOIN shop_orders o ON o.id = n.order_id
                JOIN users u ON u.id = o.user_id
                WHERE n.status IN ('UNREAD', 'READ')
                ORDER BY n.created_at DESC LIMIT 50
                """).query(OrderNotificationView.class).list();
    }

    public UnreadCount unreadCount() {
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM admin_order_notifications WHERE status = 'UNREAD'
                """).query(Long.class).single();
        return new UnreadCount(count);
    }

    @Transactional
    public OrderNotificationView markRead(String orderNo) {
        int updated = jdbc.sql("""
                UPDATE admin_order_notifications SET status = 'READ',
                    read_at = COALESCE(read_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP
                WHERE order_id = (SELECT id FROM shop_orders WHERE order_no = :orderNo)
                  AND status = 'UNREAD'
                """).param("orderNo", orderNo).update();
        if (updated == 0) {
            ensureNotificationExists(orderNo);
        }
        return notification(orderNo);
    }

    @Transactional
    public OrderNotificationView accept(String orderNo) {
        int updated = jdbc.sql("""
                UPDATE admin_order_notifications SET status = 'ACCEPTED',
                    read_at = COALESCE(read_at, CURRENT_TIMESTAMP),
                    accepted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE order_id = (SELECT id FROM shop_orders WHERE order_no = :orderNo)
                  AND status IN ('UNREAD', 'READ')
                """).param("orderNo", orderNo).update();
        if (updated == 0) {
            ensureNotificationExists(orderNo);
        }
        return notification(orderNo);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        for (Map.Entry<String, SseEmitter> connection : emitters.entrySet()) {
            try {
                connection.getValue().send(SseEmitter.event().name("new-order").data(event));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(connection.getKey());
                connection.getValue().complete();
            }
        }
    }

    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        for (Map.Entry<String, SseEmitter> connection : emitters.entrySet()) {
            try {
                connection.getValue().send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(connection.getKey());
                connection.getValue().complete();
            }
        }
    }

    private OrderNotificationView notification(String orderNo) {
        return jdbc.sql("""
                SELECT o.order_no, n.status, u.nickname, u.mobile, o.pay_amount,
                       o.receiver_name, o.receiver_mobile, o.receiver_address,
                       o.paid_at, n.created_at
                FROM admin_order_notifications n
                JOIN shop_orders o ON o.id = n.order_id
                JOIN users u ON u.id = o.user_id
                WHERE o.order_no = :orderNo
                """).param("orderNo", orderNo).query(OrderNotificationView.class).optional()
                .orElseThrow(() -> BusinessException.notFound(
                        "ORDER_NOTIFICATION_NOT_FOUND", "订单提醒不存在"));
    }

    private void ensureNotificationExists(String orderNo) {
        long exists = jdbc.sql("""
                SELECT COUNT(*) FROM admin_order_notifications n
                JOIN shop_orders o ON o.id = n.order_id WHERE o.order_no = :orderNo
                """).param("orderNo", orderNo).query(Long.class).single();
        if (exists == 0) {
            throw BusinessException.notFound("ORDER_NOTIFICATION_NOT_FOUND", "订单提醒不存在");
        }
    }

    public record UnreadCount(long count) {
    }

    public record OrderNotificationView(String orderNo, String status, String nickname,
                                        String mobile, BigDecimal payAmount, String receiverName,
                                        String receiverMobile, String receiverAddress,
                                        LocalDateTime paidAt, LocalDateTime createdAt) {
    }
}
