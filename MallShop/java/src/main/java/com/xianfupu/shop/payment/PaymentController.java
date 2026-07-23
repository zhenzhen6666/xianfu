package com.xianfupu.shop.payment;

import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderNo}/pay")
    PaymentService.PaymentView create(@PathVariable String orderNo,
                                      @RequestBody(required = false) CreatePaymentRequest request) {
        return paymentService.create(CurrentUser.id(), orderNo, request == null ? null : request.channel());
    }

    @GetMapping("/orders/{orderNo}/payments")
    List<PaymentService.PaymentView> list(@PathVariable String orderNo) {
        return paymentService.orderPayments(CurrentUser.id(), orderNo);
    }

    @PostMapping("/payments/mock/callback")
    PaymentService.PaymentView mockCallback(@Valid @RequestBody MockCallbackRequest request) {
        return paymentService.mockCallback(request.paymentNo(), request.success(), request.providerTradeNo(),
                request.callbackSecret());
    }

    public record CreatePaymentRequest(String channel) {
    }

    public record MockCallbackRequest(@NotBlank String paymentNo, boolean success,
                                      String providerTradeNo, @NotBlank String callbackSecret) {
    }
}
