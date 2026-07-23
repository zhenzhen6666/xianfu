package com.xianfupu.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredOrderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiredOrderScheduler.class);
    private final OrderService orderService;

    public ExpiredOrderScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.order.expire-scan-delay-ms:60000}")
    public void cancelExpiredOrders() {
        for (Long orderId : orderService.expiredOrderIds()) {
            try {
                orderService.cancelExpired(orderId);
            } catch (RuntimeException ex) {
                log.error("Failed to cancel expired order {}", orderId, ex);
            }
        }
    }
}
