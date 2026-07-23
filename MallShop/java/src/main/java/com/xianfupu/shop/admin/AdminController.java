package com.xianfupu.shop.admin;

import com.xianfupu.shop.order.OrderService;
import com.xianfupu.shop.payment.PaymentService;
import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
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

    @GetMapping("/orders")
    List<AdminService.AdminOrder> orders(@RequestParam(defaultValue = "ALL") String status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.orders(status, page, size);
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

    @GetMapping("/after-sales")
    List<AdminService.AfterSaleAdminView> afterSales(@RequestParam(defaultValue = "ALL") String status) {
        return service.afterSales(status);
    }

    @PatchMapping("/after-sales/{afterSaleNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateAfterSale(@PathVariable String afterSaleNo, @Valid @RequestBody AfterSaleUpdateRequest request) {
        service.updateAfterSale(CurrentUser.id(), afterSaleNo, request.status(), request.reply());
    }

    @GetMapping("/categories")
    List<AdminService.AdminCategory> categories() {
        return service.categories();
    }

    @PutMapping("/categories/{code}")
    List<AdminService.AdminCategory> saveCategory(@PathVariable String code,
                                                  @Valid @RequestBody CategoryRequest request) {
        return service.saveCategory(CurrentUser.id(), code, request.toCommand());
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

    @GetMapping("/activities")
    List<AdminService.AdminActivity> activities() {
        return service.activities();
    }

    @PutMapping("/activities/{code}")
    AdminService.AdminActivity updateActivity(@PathVariable String code,
                                              @Valid @RequestBody ActivityRequest request) {
        return service.updateActivity(CurrentUser.id(), code, request.toCommand());
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

    public record AfterSaleUpdateRequest(@NotBlank String status, @Size(max = 1000) String reply) {
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
}
