package com.xianfupu.shop.cart;

import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    CartService.CartView get() {
        return cartService.get(CurrentUser.id());
    }

    @PostMapping("/items")
    CartService.CartView add(@Valid @RequestBody AddItemRequest request) {
        return cartService.add(CurrentUser.id(), request.productId(), request.quantity(), request.activityCode());
    }

    @PatchMapping("/items/{productCode}")
    CartService.CartView update(@PathVariable String productCode, @Valid @RequestBody UpdateItemRequest request) {
        return cartService.update(CurrentUser.id(), productCode, request.quantity(), request.selected());
    }

    @PutMapping("/selection")
    CartService.CartView selectAll(@RequestBody SelectionRequest request) {
        return cartService.selectAll(CurrentUser.id(), request.selected());
    }

    @DeleteMapping("/items/{productCode}")
    CartService.CartView remove(@PathVariable String productCode) {
        return cartService.remove(CurrentUser.id(), productCode);
    }

    @DeleteMapping("/selected")
    CartService.CartView removeSelected() {
        return cartService.removeSelected(CurrentUser.id());
    }

    public record AddItemRequest(@NotBlank String productId, @Min(1) @Max(99) int quantity,
                                 String activityCode) {
    }

    public record UpdateItemRequest(@Min(1) @Max(99) Integer quantity, Boolean selected) {
    }

    public record SelectionRequest(boolean selected) {
    }
}
