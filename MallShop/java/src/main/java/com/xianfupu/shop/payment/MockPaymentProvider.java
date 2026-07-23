package com.xianfupu.shop.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MockPaymentProvider implements PaymentProvider {
    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public PaymentInstruction create(String paymentNo, String orderNo, BigDecimal amount, String description) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("paymentNo", paymentNo);
        params.put("orderNo", orderNo);
        params.put("amount", amount);
        params.put("description", description);
        params.put("mock", true);
        params.put("createdAt", Instant.now());
        return new PaymentInstruction(params, null);
    }

    @Override
    public RefundInstruction refund(String paymentNo, BigDecimal amount, String reason) {
        return new RefundInstruction("MOCK-REFUND-" + paymentNo, "SUCCESS");
    }
}
