package com.xianfupu.shop.payment;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentProvider {
    String channel();

    PaymentInstruction create(String paymentNo, String orderNo, BigDecimal amount, String description);

    default RefundInstruction refund(String paymentNo, BigDecimal amount, String reason) {
        throw new UnsupportedOperationException("Refund is not supported by " + channel());
    }

    record PaymentInstruction(Map<String, Object> payParams, String providerTradeNo) {
    }

    record RefundInstruction(String providerRefundNo, String status) {
    }
}
