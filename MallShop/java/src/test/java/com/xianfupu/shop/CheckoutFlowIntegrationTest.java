package com.xianfupu.shop;

import com.xianfupu.shop.admin.AdminService;
import com.xianfupu.shop.admin.AdminOrderNotificationService;
import com.xianfupu.shop.auth.AuthService;
import com.xianfupu.shop.cart.CartService;
import com.xianfupu.shop.catalog.CatalogService;
import com.xianfupu.shop.common.BusinessException;
import com.xianfupu.shop.order.OrderService;
import com.xianfupu.shop.payment.PaymentService;
import com.xianfupu.shop.user.AddressService;
import com.xianfupu.shop.user.UserBenefitService;
import com.xianfupu.shop.user.AfterSaleService;
import com.xianfupu.shop.security.AuthSessionService;
import com.xianfupu.shop.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired
    AdminService adminService;
    @Autowired
    AdminOrderNotificationService orderNotificationService;
    @Autowired
    AuthSessionService authSessionService;
    @Autowired
    JwtService jwtService;
    @Autowired
    AfterSaleService afterSaleService;
    @Autowired
    CatalogService catalogService;

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
        assertThat(orderService.create(userId, command).orderNo()).isEqualTo(order.orderNo());
        assertThat(cartService.get(userId).items()).isEmpty();

        OrderService.OrderDetail adminDetail = adminService.order(order.orderNo());
        assertThat(adminDetail.receiverName()).isEqualTo("林女士");
        assertThat(adminDetail.receiverAddress()).isEqualTo("幸福里社区 8 栋 1202 室");
        assertThat(adminDetail.note()).isEqualTo("请放门口");
        assertThat(adminDetail.items()).hasSize(2);

        PaymentService.PaymentView payment = paymentService.create(userId, order.orderNo(), "MOCK");
        assertThat(payment.status()).isEqualTo("PENDING");
        paymentService.mockCallback(payment.paymentNo(), true, "LOCAL-TEST-001", "local-mock-callback");
        assertThat(orderService.detail(userId, order.orderNo()).status()).isEqualTo("PAID");
        assertThat(orderNotificationService.unreadCount().count()).isEqualTo(1);
        assertThat(orderNotificationService.activeNotifications())
                .anyMatch(item -> item.orderNo().equals(order.orderNo())
                        && "UNREAD".equals(item.status()));
        assertThat(orderNotificationService.markRead(order.orderNo()).status()).isEqualTo("READ");
        assertThat(orderNotificationService.unreadCount().count()).isZero();

        long adminId = authService.devAdminLogin("TestAdminSecret", "订单管理员").user().id();
        assertThat(adminService.updateOrderStatus(adminId, order.orderNo(), "ACCEPTED").status())
                .isEqualTo("ACCEPTED");
        assertThat(adminService.updateOrderStatus(adminId, order.orderNo(), "PREPARING").status())
                .isEqualTo("PREPARING");
        assertThat(adminService.updateOrderStatus(adminId, order.orderNo(), "SHIPPING").status())
                .isEqualTo("SHIPPING");
        assertThat(adminService.updateOrderStatus(adminId, order.orderNo(), "DELIVERED").status())
                .isEqualTo("DELIVERED");
        OrderService.OrderDetail completed = adminService.updateOrderStatus(adminId, order.orderNo(), "COMPLETED");
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(orderNotificationService.activeNotifications())
                .noneMatch(item -> item.orderNo().equals(order.orderNo()));
        assertThat(authService.findById(userId).points()).isGreaterThanOrEqualTo(26);
        assertThat(authService.findById(userId).totalSpent()).isEqualByComparingTo("26.19");

        AfterSaleService.AfterSaleView afterSale = afterSaleService.create(
                userId, order.orderNo(), "商品品质问题", "包装破损",
                java.util.List.of("/api/files/evidence-test.jpg"));
        assertThat(afterSale.images()).containsExactly("/api/files/evidence-test.jpg");
        adminService.updateAfterSale(adminId, afterSale.afterSaleNo(), "APPROVED",
                "同意退款", new BigDecimal("10.00"));
        AfterSaleService.AfterSaleView processed = afterSaleService.get(userId, afterSale.afterSaleNo());
        assertThat(processed.refundStatus()).isEqualTo("SUCCEEDED");
        assertThat(processed.refundedAmount()).isEqualByComparingTo("10.00");
        assertThat(processed.history()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void rotatesRefreshTokenAndRevokesSessionOnLogout() {
        AuthService.LoginResult login = authService.devLogin("13700009999", "会话测试");
        JwtService.TokenClaims initialClaims = jwtService.verify(login.accessToken());
        assertThat(authSessionService.isActive(initialClaims.sessionId(), initialClaims.userId(), "USER")).isTrue();

        AuthService.LoginResult refreshed = authService.refresh(login.refreshToken(), "127.0.0.1", "JUnit");
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThatThrownBy(() -> authService.refresh(login.refreshToken(), null, null))
                .isInstanceOf(BusinessException.class);

        authService.logout(refreshed.refreshToken());
        JwtService.TokenClaims refreshedClaims = jwtService.verify(refreshed.accessToken());
        assertThat(authSessionService.isActive(
                refreshedClaims.sessionId(), refreshedClaims.userId(), "USER")).isFalse();
    }

    @Test
    void selectsIndependentProductSkuAndKeepsSpecOnOrder() {
        AuthService.UserProfile user = authService.devLogin("13500007777", "多规格用户").user();
        AddressService.AddressView address = addressService.create(user.id(),
                new AddressService.AddressCommand("多规格用户", "13500007777", "规格测试地址",
                        null, null, BigDecimal.ONE, true));
        assertThat(catalogService.productDetail("p1").skus()).hasSizeGreaterThan(1);

        CartService.CartView cart = cartService.add(user.id(), "p1", 2, null, "p1-LARGE");
        assertThat(cart.items().get(0).spec()).isEqualTo("约 800g/份");
        assertThat(cart.items().get(0).price()).isEqualByComparingTo("7.50");

        OrderService.OrderDetail order = orderService.create(user.id(),
                new OrderService.OrderCommand(address.id(), null, 0, null, "sku-order-test"));
        assertThat(order.items().get(0).spec()).isEqualTo("约 800g/份");
        assertThat(order.items().get(0).count()).isEqualTo(2);
    }

    @Test
    void adminCreatesAndIssuesThresholdFreeCouponToMobileAccount() {
        long adminId = authService.devAdminLogin("TestAdminSecret", "运营管理员").user().id();
        AuthService.UserProfile user = authService.devLogin("13900001234", "优惠券用户").user();
        LocalDateTime now = LocalDateTime.now();

        AdminService.AdminCoupon coupon = adminService.createCoupon(adminId,
                new AdminService.CouponCommand("无门槛 3 元券", new BigDecimal("3.00"),
                        BigDecimal.ZERO, 20, now.minusMinutes(1), now.plusDays(7), true));
        assertThat(coupon.minimumAmount()).isEqualByComparingTo("0.00");
        assertThat(coupon.status()).isEqualTo("ACTIVE");

        AdminService.CouponIssueResult issued =
                adminService.issueCoupon(adminId, coupon.code(), "13900001234");
        assertThat(issued.userId()).isEqualTo(user.id());
        assertThat(issued.status()).isEqualTo("AVAILABLE");
        assertThat(benefitService.coupons(user.id(), "AVAILABLE"))
                .anyMatch(item -> item.id() == issued.userCouponId()
                        && item.amount().compareTo(new BigDecimal("3.00")) == 0);
        assertThatThrownBy(() -> adminService.issueCoupon(adminId, coupon.code(), "13900001234"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该用户已拥有这张优惠券");

        AddressService.AddressView address = addressService.create(user.id(),
                new AddressService.AddressCommand("优惠券用户", "13900001234", "测试小区 1 号",
                        null, null, BigDecimal.ONE, true));
        cartService.add(user.id(), "p1", 1, null);
        OrderService.OrderPreview preview = orderService.preview(user.id(),
                new OrderService.OrderCommand(address.id(), issued.userCouponId(), 0, null));
        assertThat(preview.couponDiscount()).isEqualByComparingTo("3.00");

        AdminService.AdminCoupon refreshed = adminService.coupons().stream()
                .filter(item -> item.code().equals(coupon.code())).findFirst().orElseThrow();
        assertThat(refreshed.claimedCount()).isEqualTo(1);
        assertThat(refreshed.remainingStock()).isEqualTo(19);

        authService.devLogin("13600001234", "批量发券用户");
        AdminService.CouponBatchResult batch = adminService.batchIssueCoupon(
                adminId, coupon.code(), java.util.List.of("13600001234", "13900001234", "123"));
        assertThat(batch.successCount()).isEqualTo(1);
        assertThat(batch.failedCount()).isEqualTo(2);
        assertThat(adminService.couponIssueRecords(coupon.code())).hasSize(4);
    }

    @Test
    void adminCreatesUpdatesAndDeletesHomeContent() {
        long adminId = authService.devAdminLogin("TestAdminSecret", "首页管理员").user().id();
        AdminService.HomeModuleCommand createdCommand = new AdminService.HomeModuleCommand(
                "QUICK_ENTRY", "测试入口区",
                "{\"items\":[{\"icon\":\"测\",\"name\":\"测试入口\",\"theme\":\"mint\",\"type\":\"new\"}]}",
                99, true);
        AdminService.AdminHomeModule created = adminService.saveHomeModule(adminId, null, createdCommand)
                .stream().filter(item -> "测试入口区".equals(item.title())).findFirst().orElseThrow();

        AdminService.HomeModuleCommand updatedCommand = new AdminService.HomeModuleCommand(
                "QUICK_ENTRY", "测试入口区",
                "{\"items\":[]}", 99, false);
        AdminService.AdminHomeModule updated = adminService.saveHomeModule(adminId, created.id(), updatedCommand)
                .stream().filter(item -> item.id() == created.id()).findFirst().orElseThrow();
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.contentJson()).isEqualTo("{\"items\":[]}");

        adminService.deleteHomeModule(adminId, created.id());
        assertThat(adminService.homeModules()).noneMatch(item -> item.id() == created.id());
        assertThatThrownBy(() -> adminService.deleteHomeModule(adminId, created.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("首页组件不存在");
    }
}
