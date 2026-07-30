package com.xianfupu.shop.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderPaidEvent(String orderNo, String status, String nickname, String mobile,
                             BigDecimal payAmount, String receiverName, String receiverMobile,
                             String receiverAddress, LocalDateTime paidAt,
                             LocalDateTime createdAt) {
}
