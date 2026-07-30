package com.xianfupu.shop.monitoring;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonitoringService {
    private final JdbcClient jdbc;

    public MonitoringService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void error(String requestId, String code, String message, String method, String path,
                      Long userId, Throwable throwable) {
        StringWriter trace = new StringWriter();
        if (throwable != null) throwable.printStackTrace(new PrintWriter(trace));
        jdbc.sql("""
                INSERT INTO system_error_logs(request_id, level, error_code, message, request_method,
                    request_path, user_id, stack_trace)
                VALUES (:requestId, 'ERROR', :code, :message, :method, :path, :userId, :trace)
                """).param("requestId", requestId).param("code", code).param("message", limit(message, 1000))
                .param("method", method).param("path", limit(path, 500)).param("userId", userId)
                .param("trace", limit(trace.toString(), 20000)).update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alert(String type, String severity, String title, String message,
                      String targetType, String targetId) {
        jdbc.sql("""
                INSERT INTO business_alerts(alert_type, severity, title, message, target_type, target_id)
                VALUES (:type, :severity, :title, :message, :targetType, :targetId)
                """).param("type", type).param("severity", severity).param("title", title)
                .param("message", limit(message, 1000)).param("targetType", targetType)
                .param("targetId", targetId).update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alertOnce(String type, String severity, String title, String message,
                          String targetType, String targetId) {
        long exists = jdbc.sql("""
                SELECT COUNT(*) FROM business_alerts
                WHERE alert_type = :type AND target_type = :targetType AND target_id = :targetId
                  AND status = 'OPEN'
                """).param("type", type).param("targetType", targetType)
                .param("targetId", targetId).query(Long.class).single();
        if (exists == 0) alert(type, severity, title, message, targetType, targetId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void paymentCallback(String paymentNo, String channel, boolean success, boolean signatureValid,
                                String providerTradeNo, String resultCode, String message, String payload) {
        jdbc.sql("""
                INSERT INTO payment_callback_logs(payment_no, channel, success, signature_valid,
                    provider_trade_no, result_code, message, payload)
                VALUES (:paymentNo, :channel, :success, :signatureValid, :tradeNo, :code, :message, :payload)
                """).param("paymentNo", paymentNo).param("channel", channel).param("success", success)
                .param("signatureValid", signatureValid).param("tradeNo", providerTradeNo)
                .param("code", resultCode).param("message", limit(message, 1000))
                .param("payload", limit(payload, 20000)).update();
    }

    public List<ErrorLogView> errors(int limit) {
        return jdbc.sql("""
                SELECT id, request_id, level, error_code, message, request_method, request_path,
                       user_id, stack_trace, created_at
                FROM system_error_logs ORDER BY id DESC LIMIT :limit
                """).param("limit", safeLimit(limit)).query(ErrorLogView.class).list();
    }

    public List<AlertView> alerts(String status, int limit) {
        String condition = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? "" : " WHERE status = :status";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT id, alert_type, severity, title, message, target_type, target_id, status,
                       acknowledged_by, acknowledged_at, created_at
                FROM business_alerts
                """ + condition + " ORDER BY id DESC LIMIT :limit").param("limit", safeLimit(limit));
        if (!condition.isBlank()) query = query.param("status", status.toUpperCase());
        return query.query(AlertView.class).list();
    }

    public List<PaymentCallbackView> callbacks(int limit) {
        return jdbc.sql("""
                SELECT id, payment_no, channel, success, signature_valid, provider_trade_no,
                       result_code, message, payload, created_at
                FROM payment_callback_logs ORDER BY id DESC LIMIT :limit
                """).param("limit", safeLimit(limit)).query(PaymentCallbackView.class).list();
    }

    @Transactional
    public void acknowledge(long id, long adminId) {
        jdbc.sql("""
                UPDATE business_alerts SET status = 'ACKNOWLEDGED', acknowledged_by = :adminId,
                    acknowledged_at = CURRENT_TIMESTAMP WHERE id = :id AND status = 'OPEN'
                """).param("adminId", adminId).param("id", id).update();
    }

    private int safeLimit(int limit) {
        return Math.min(500, Math.max(1, limit));
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    public record ErrorLogView(long id, String requestId, String level, String errorCode, String message,
                               String requestMethod, String requestPath, Long userId, String stackTrace,
                               LocalDateTime createdAt) {
    }
    public record AlertView(long id, String alertType, String severity, String title, String message,
                            String targetType, String targetId, String status, Long acknowledgedBy,
                            LocalDateTime acknowledgedAt, LocalDateTime createdAt) {
    }
    public record PaymentCallbackView(long id, String paymentNo, String channel, boolean success,
                                      boolean signatureValid, String providerTradeNo, String resultCode,
                                      String message, String payload, LocalDateTime createdAt) {
    }
}
