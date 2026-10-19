package com.xianfupu.shop.order;

import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/preview")
    OrderService.OrderPreview preview(@Valid @RequestBody OrderRequest request) {
        return orderService.preview(CurrentUser.id(), request.toCommand());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderService.OrderDetail create(@Valid @RequestBody OrderRequest request) {
        return orderService.create(CurrentUser.id(), request.toCommand());
    }

    @GetMapping
    List<OrderService.OrderSummary> list(@RequestParam(name = "status", defaultValue = "ALL") String status,
                                         @RequestParam(name = "page", defaultValue = "0") int page,
                                         @RequestParam(name = "size", defaultValue = "20") int size) {
        return orderService.list(CurrentUser.id(), status, page, size);
    }

    @GetMapping("/{orderNo}")
    OrderService.OrderDetail detail(@PathVariable("orderNo") String orderNo) {
        return orderService.detail(CurrentUser.id(), orderNo);
    }

    @PostMapping("/{orderNo}/cancel")
    OrderService.OrderDetail cancel(@PathVariable("orderNo") String orderNo, @RequestBody(required = false) CancelRequest request) {
        return orderService.cancel(CurrentUser.id(), orderNo, request == null ? null : request.reason());
    }

    @PostMapping("/{orderNo}/confirm")
    OrderService.OrderDetail confirm(@PathVariable("orderNo") String orderNo) {
        return orderService.confirm(CurrentUser.id(), orderNo);
    }

    public record OrderRequest(@Min(1) long addressId, @Min(1) Long userCouponId,
                               @Min(0) Integer pointsToUse, @Size(max = 500) String note,
                               @Size(max = 80) String idempotencyKey) {
        OrderService.OrderCommand toCommand() {
            String key = idempotencyKey == null || idempotencyKey.isBlank()
                    ? java.util.UUID.randomUUID().toString() : idempotencyKey;
            return new OrderService.OrderCommand(addressId, userCouponId, pointsToUse, note, key);
        }
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }
}
