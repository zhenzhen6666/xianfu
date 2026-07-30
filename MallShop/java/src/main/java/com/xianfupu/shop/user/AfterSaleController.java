package com.xianfupu.shop.user;

import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/after-sales")
public class AfterSaleController {
    private final AfterSaleService service;

    public AfterSaleController(AfterSaleService service) {
        this.service = service;
    }

    @GetMapping
    List<AfterSaleService.AfterSaleView> list() {
        return service.list(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AfterSaleService.AfterSaleView create(@Valid @RequestBody AfterSaleRequest request) {
        return service.create(CurrentUser.id(), request.orderNo(), request.reason(),
                request.description(), request.imageUrls());
    }

    @GetMapping("/{afterSaleNo}")
    AfterSaleService.AfterSaleView get(@PathVariable String afterSaleNo) {
        return service.get(CurrentUser.id(), afterSaleNo);
    }

    public record AfterSaleRequest(@NotBlank String orderNo, @NotBlank @Size(max = 255) String reason,
                                   @Size(max = 1000) String description,
                                   @Size(max = 6) List<@Size(max = 500) String> imageUrls) {
    }
}
