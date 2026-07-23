package com.xianfupu.shop;

import com.xianfupu.shop.auth.AuthService;
import com.xianfupu.shop.cart.CartService;
import com.xianfupu.shop.order.OrderService;
import com.xianfupu.shop.payment.PaymentService;
import com.xianfupu.shop.user.AddressService;
import com.xianfupu.shop.user.UserBenefitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CheckoutFlowIntegrationTest {
    @Autowired
    AuthService authService;
    @Autowired
    AddressService addressService;
    @Autowired
    CartService cartService;
    @Autowired
    UserBenefitService benefitService;
    @Autowired
    OrderService orderService;
    @Autowired
    PaymentService paymentService;

    @Test
    void completesCheckoutPaymentAndMemberRewardFlow() {
        AuthService.LoginResult login = authService.devLogin("13866686688", "林女士");
        long userId = login.user().id();
        AddressService.AddressView address = addressService.create(userId,
                new AddressService.AddressCommand("林女士", "13866686688", "幸福里社区 8 栋 1202 室",
                        new BigDecimal("121.1234567"), new BigDecimal("31.1234567"),
                        new BigDecimal("2.50"), true));

        CartService.CartView smallCart = cartService.add(userId, "p1", 1, null);
        assertThat(smallCart.deliveryFee()).isEqualByComparingTo("3.00");
        cartService.add(userId, "p2", 4, null);

        long couponId = benefitService.coupons(userId, "AVAILABLE").get(0).id();
        OrderService.OrderCommand command = new OrderService.OrderCommand(address.id(), couponId, 0, "请放门口");
        OrderService.OrderPreview preview = orderService.preview(userId, command);
        assertThat(preview.subtotal()).isEqualByComparingTo("31.19");
        assertThat(preview.deliveryFee()).isEqualByComparingTo("0.00");
        assertThat(preview.couponDiscount()).isEqualByComparingTo("5.00");
        assertThat(preview.payAmount()).isEqualByComparingTo("26.19");

        OrderService.OrderDetail order = orderService.create(userId, command);
        assertThat(order.status()).isEqualTo("WAIT_PAY");
        assertThat(cartService.get(userId).items()).isEmpty();

        PaymentService.PaymentView payment = paymentService.create(userId, order.orderNo(), "MOCK");
        assertThat(payment.status()).isEqualTo("PENDING");
        paymentService.mockCallback(payment.paymentNo(), true, "LOCAL-TEST-001", "local-mock-callback");
        assertThat(orderService.detail(userId, order.orderNo()).status()).isEqualTo("SHIPPING");

        OrderService.OrderDetail completed = orderService.confirm(userId, order.orderNo());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(authService.findById(userId).points()).isGreaterThanOrEqualTo(26);
        assertThat(authService.findById(userId).totalSpent()).isEqualByComparingTo("26.19");
    }
}
