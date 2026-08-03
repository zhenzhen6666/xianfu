package com.xianfupu.shop.admin;

import com.xianfupu.shop.order.OrderService;
import com.xianfupu.shop.payment.PaymentService;
import com.xianfupu.shop.security.CurrentUser;
import com.xianfupu.shop.user.AfterSaleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    private final AdminOrderNotificationService orderNotifications;
    private final AfterSaleService afterSaleService;

    public AdminController(AdminService service, AdminOrderNotificationService orderNotifications,
                           AfterSaleService afterSaleService) {
        this.service = service;
        this.orderNotifications = orderNotifications;
        this.afterSaleService = afterSaleService;
    }

    @GetMapping("/dashboard")
    AdminService.Dashboard dashboard() {
        return service.dashboard();
    }

    @GetMapping("/products")
    List<AdminService.AdminProduct> products(@RequestParam(defaultValue = "ALL") String status) {
        return service.products(status);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    AdminService.AdminProduct createProduct(@Valid @RequestBody ProductRequest request) {
        return service.saveProduct(CurrentUser.id(), null, request.toCommand());
    }

    @PutMapping("/products/{productCode}")
    AdminService.AdminProduct updateProduct(@PathVariable String productCode,
                                            @Valid @RequestBody ProductRequest request) {
        return service.saveProduct(CurrentUser.id(), productCode, request.toCommand());
    }

    @DeleteMapping("/products/{productCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteProduct(@PathVariable String productCode) {
        service.deleteProduct(CurrentUser.id(), productCode);
    }

    @GetMapping("/products/{productCode}/skus")
    List<AdminService.AdminSku> productSkus(@PathVariable String productCode) {
        return service.productSkus(productCode);
    }

    @PutMapping("/products/{productCode}/skus")
    List<AdminService.AdminSku> saveProductSkus(@PathVariable String productCode,
                                                @Valid @RequestBody ProductSkusRequest request) {
        return service.saveProductSkus(CurrentUser.id(), productCode,
                request.skus().stream().map(SkuRequest::toCommand).toList());
    }

    @GetMapping("/orders")
    List<AdminService.AdminOrder> orders(@RequestParam(defaultValue = "ALL") String status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.orders(status, page, size);
    }

    @GetMapping("/order-notifications")
    List<AdminOrderNotificationService.OrderNotificationView> orderNotifications() {
        return orderNotifications.activeNotifications();
    }

    @GetMapping("/order-notifications/unread-count")
    AdminOrderNotificationService.UnreadCount unreadOrderNotificationCount() {
        return orderNotifications.unreadCount();
    }

    @PatchMapping("/order-notifications/{orderNo}/read")
    AdminOrderNotificationService.OrderNotificationView readOrderNotification(@PathVariable String orderNo) {
        return orderNotifications.markRead(orderNo);
    }

    @PatchMapping("/order-notifications/{orderNo}/accept")
    AdminOrderNotificationService.OrderNotificationView acceptOrderNotification(@PathVariable String orderNo) {
        return orderNotifications.accept(orderNo);
    }

    @GetMapping(value = "/order-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter orderEvents(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return orderNotifications.subscribe();
    }

    @GetMapping("/orders/{orderNo}")
    OrderService.OrderDetail order(@PathVariable String orderNo) {
        return service.order(orderNo);
    }

    @PatchMapping("/orders/{orderNo}/status")
    OrderService.OrderDetail updateOrderStatus(@PathVariable String orderNo,
                                               @Valid @RequestBody StatusRequest request) {
        return service.updateOrderStatus(CurrentUser.id(), orderNo, request.status());
    }

    @PostMapping("/orders/{orderNo}/refund")
    PaymentService.PaymentView refund(@PathVariable String orderNo,
                                      @Valid @RequestBody RefundRequest request) {
        return service.refund(CurrentUser.id(), orderNo, request.amount(), request.reason());
    }

    @GetMapping("/delivery-config")
    AdminService.DeliveryConfig delivery() {
        return service.delivery();
    }

    @PutMapping("/delivery-config")
    AdminService.DeliveryConfig updateDelivery(@Valid @RequestBody DeliveryRequest request) {
        return service.updateDelivery(CurrentUser.id(), request.toCommand());
    }

    @GetMapping("/users")
    List<AdminService.AdminUser> users(@RequestParam(required = false) String keyword) {
        return service.users(keyword);
    }

    @PatchMapping("/users/{userId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void userStatus(@PathVariable long userId, @Valid @RequestBody StatusRequest request) {
        service.setUserStatus(CurrentUser.id(), userId, request.status());
    }

    @GetMapping("/coupons")
    List<AdminService.AdminCoupon> coupons() {
        return service.coupons();
    }

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    AdminService.AdminCoupon createCoupon(@Valid @RequestBody CouponRequest request) {
        return service.createCoupon(CurrentUser.id(), request.toCommand());
    }

    @PutMapping("/coupons/{couponCode}")
    AdminService.AdminCoupon updateCoupon(@PathVariable String couponCode,
                                          @Valid @RequestBody CouponRequest request) {
        return service.updateCoupon(CurrentUser.id(), couponCode, request.toCommand());
    }

    @PatchMapping("/coupons/{couponCode}/status")
    AdminService.AdminCoupon updateCouponStatus(@PathVariable String couponCode,
                                                @Valid @RequestBody EnabledRequest request) {
        return service.setCouponEnabled(CurrentUser.id(), couponCode, request.enabled());
    }

    @DeleteMapping("/coupons/{couponCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCoupon(@PathVariable String couponCode) {
        service.deleteCoupon(CurrentUser.id(), couponCode);
    }

    @PostMapping("/coupons/{couponCode}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    AdminService.CouponIssueResult issueCoupon(@PathVariable String couponCode,
                                               @Valid @RequestBody CouponIssueRequest request) {
        return service.issueCoupon(CurrentUser.id(), couponCode, request.mobile());
    }

    @PostMapping("/coupons/{couponCode}/batch-issues")
    AdminService.CouponBatchResult batchIssueCoupon(@PathVariable String couponCode,
                                                    @Valid @RequestBody CouponBatchIssueRequest request) {
        return service.batchIssueCoupon(CurrentUser.id(), couponCode, request.mobiles());
    }

    @GetMapping("/coupon-issue-records")
    List<AdminService.CouponIssueRecordView> couponIssueRecords(
            @RequestParam(required = false) String couponCode) {
        return service.couponIssueRecords(couponCode);
    }

    @GetMapping("/after-sales")
    List<AdminService.AfterSaleAdminView> afterSales(@RequestParam(defaultValue = "ALL") String status) {
        return service.afterSales(status);
    }

    @GetMapping("/after-sales/{afterSaleNo}")
    AfterSaleService.AfterSaleView afterSale(@PathVariable String afterSaleNo) {
        return afterSaleService.adminGet(afterSaleNo);
    }

    @PatchMapping("/after-sales/{afterSaleNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateAfterSale(@PathVariable String afterSaleNo, @Valid @RequestBody AfterSaleUpdateRequest request) {
        service.updateAfterSale(CurrentUser.id(), afterSaleNo, request.status(), request.reply(),
                request.refundAmount());
    }

    @GetMapping("/categories")
    List<AdminService.AdminCategory> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    List<AdminService.AdminCategory> createCategory(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(CurrentUser.id(), request.toCommand());
    }

    @PutMapping("/categories/{code}")
    List<AdminService.AdminCategory> saveCategory(@PathVariable String code,
                                                  @Valid @RequestBody CategoryRequest request) {
        return service.saveCategory(CurrentUser.id(), code, request.toCommand());
    }

    @DeleteMapping("/categories/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@PathVariable String code) {
        service.deleteCategory(CurrentUser.id(), code);
    }

    @GetMapping("/home-modules")
    List<AdminService.AdminHomeModule> homeModules() {
        return service.homeModules();
    }

    @PostMapping("/home-modules")
    List<AdminService.AdminHomeModule> createHomeModule(@Valid @RequestBody HomeModuleRequest request) {
        return service.saveHomeModule(CurrentUser.id(), null, request.toCommand());
    }

    @PutMapping("/home-modules/{id}")
    List<AdminService.AdminHomeModule> updateHomeModule(@PathVariable long id,
                                                        @Valid @RequestBody HomeModuleRequest request) {
        return service.saveHomeModule(CurrentUser.id(), id, request.toCommand());
    }

    @DeleteMapping("/home-modules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteHomeModule(@PathVariable long id) {
        service.deleteHomeModule(CurrentUser.id(), id);
    }

    @GetMapping("/activities")
    List<AdminService.AdminActivity> activities() {
        return service.activities();
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    AdminService.AdminActivity createActivity(@Valid @RequestBody ActivityRequest request) {
        return service.createActivity(CurrentUser.id(), request.toCommand());
    }

    @PutMapping("/activities/{code}")
    AdminService.AdminActivity updateActivity(@PathVariable String code,
                                              @Valid @RequestBody ActivityRequest request) {
        return service.updateActivity(CurrentUser.id(), code, request.toCommand());
    }

    @DeleteMapping("/activities/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteActivity(@PathVariable String code) {
        service.deleteActivity(CurrentUser.id(), code);
    }

    @GetMapping("/activities/{code}/products")
    List<AdminService.AdminActivityProduct> activityProducts(@PathVariable String code) {
        return service.activityProducts(code);
    }

    @PutMapping("/activities/{code}/products/{productCode}")
    AdminService.AdminActivityProduct saveActivityProduct(
            @PathVariable String code, @PathVariable String productCode,
            @Valid @RequestBody ActivityProductRequest request) {
        return service.saveActivityProduct(
                CurrentUser.id(), code, productCode, request.toCommand());
    }

    @DeleteMapping("/activities/{code}/products/{productCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteActivityProduct(@PathVariable String code, @PathVariable String productCode) {
        service.deleteActivityProduct(CurrentUser.id(), code, productCode);
    }

    public record ProductRequest(@NotBlank String categoryCode, @NotBlank @Size(max = 120) String name,
                                 @Size(max = 255) String subtitle, String description,
                                 @NotBlank @Size(max = 100) String spec, @Size(max = 500) String imageUrl,
                                 @NotNull @DecimalMin("0") BigDecimal originalPrice,
                                 @NotNull @DecimalMin("0") BigDecimal salePrice,
                                 @DecimalMin("0") BigDecimal memberPrice, @Min(0) int stock,
                                 @Size(max = 40) String badge, @NotBlank String status, int sortOrder) {
        AdminService.ProductCommand toCommand() {
            return new AdminService.ProductCommand(categoryCode, name, subtitle, description, spec, imageUrl,
                    originalPrice, salePrice, memberPrice, stock, badge, status, sortOrder);
        }
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record ProductSkusRequest(@NotNull @Size(min = 1, max = 30) List<@Valid SkuRequest> skus) {
    }

    public record SkuRequest(String skuCode, @NotBlank @Size(max = 100) String specName,
                             @NotNull @DecimalMin("0") BigDecimal originalPrice,
                             @NotNull @DecimalMin("0") BigDecimal salePrice,
                             @DecimalMin("0") BigDecimal memberPrice, @Min(0) int stock) {
        AdminService.SkuCommand toCommand() {
            return new AdminService.SkuCommand(skuCode, specName, originalPrice, salePrice,
                    memberPrice, stock);
        }
    }

    public record RefundRequest(@DecimalMin("0.01") BigDecimal amount,
                                @NotBlank @Size(max = 255) String reason) {
    }

    public record DeliveryRequest(@NotBlank @Size(max = 80) String name,
                                  @NotNull @DecimalMin("0") BigDecimal freeThreshold,
                                  @NotNull @DecimalMin("0") BigDecimal baseFee,
                                  @NotNull @DecimalMin("0.1") BigDecimal maxDistanceKm,
                                  @Min(1) int promiseMinutes,
                                  @NotBlank @Size(max = 255) String announcement) {
        AdminService.DeliveryCommand toCommand() {
            return new AdminService.DeliveryCommand(name, freeThreshold, baseFee, maxDistanceKm,
                    promiseMinutes, announcement);
        }
    }

    public record AfterSaleUpdateRequest(@NotBlank String status, @Size(max = 1000) String reply,
                                         @DecimalMin("0.01") BigDecimal refundAmount) {
    }

    public record CategoryRequest(String parentCode, @NotBlank @Size(max = 80) String name,
                                  @Size(max = 500) String iconUrl, int sortOrder, boolean enabled) {
        AdminService.CategoryCommand toCommand() {
            return new AdminService.CategoryCommand(parentCode, name, iconUrl, sortOrder, enabled);
        }
    }

    public record HomeModuleRequest(@NotBlank String moduleType, @Size(max = 100) String title,
                                    @NotBlank String contentJson, int sortOrder, boolean enabled) {
        AdminService.HomeModuleCommand toCommand() {
            return new AdminService.HomeModuleCommand(moduleType, title, contentJson, sortOrder, enabled);
        }
    }

    public record ActivityRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank String activityType, @Size(max = 500) String description,
                                  @NotBlank String rulesJson, @NotNull LocalDateTime startsAt,
                                  @NotNull LocalDateTime endsAt, boolean enabled) {
        AdminService.ActivityCommand toCommand() {
            return new AdminService.ActivityCommand(name, activityType, description, rulesJson,
                    startsAt, endsAt, enabled);
        }
    }

    public record ActivityProductRequest(@DecimalMin("0") BigDecimal activityPrice,
                                         @NotNull @Min(0) Integer activityStock,
                                         @Min(1) Integer perUserLimit,
                                         @Min(0) int sortOrder) {
        AdminService.ActivityProductCommand toCommand() {
            return new AdminService.ActivityProductCommand(
                    activityPrice, activityStock, perUserLimit, sortOrder);
        }
    }

    public record CouponRequest(@NotBlank @Size(max = 100) String name,
                                @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
                                @NotNull @DecimalMin("0") BigDecimal minimumAmount,
                                @Min(1) int totalStock, @NotNull LocalDateTime startsAt,
                                @NotNull LocalDateTime endsAt, boolean enabled) {
        AdminService.CouponCommand toCommand() {
            return new AdminService.CouponCommand(name, amount, minimumAmount, totalStock,
                    startsAt, endsAt, enabled);
        }
    }

    public record CouponIssueRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的 11 位手机号")
            String mobile) {
    }

    public record CouponBatchIssueRequest(@NotNull @Size(min = 1, max = 500) List<String> mobiles) {
    }
}
