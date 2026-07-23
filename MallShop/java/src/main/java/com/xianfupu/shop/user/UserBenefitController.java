package com.xianfupu.shop.user;

import com.xianfupu.shop.auth.AuthService;
import com.xianfupu.shop.catalog.CatalogService;
import com.xianfupu.shop.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserBenefitController {
    private final UserBenefitService benefitService;
    private final AuthService authService;

    public UserBenefitController(UserBenefitService benefitService, AuthService authService) {
        this.benefitService = benefitService;
        this.authService = authService;
    }

    @GetMapping("/profile")
    AuthService.UserProfile profile() {
        return authService.findById(CurrentUser.id());
    }

    @GetMapping("/coupons")
    List<UserBenefitService.CouponView> coupons(@RequestParam(defaultValue = "ALL") String status) {
        return benefitService.coupons(CurrentUser.id(), status);
    }

    @PostMapping("/coupons/{code}/claim")
    UserBenefitService.CouponView claim(@PathVariable String code) {
        return benefitService.claim(CurrentUser.id(), code);
    }

    @GetMapping("/points")
    UserBenefitService.PointAccount points() {
        return benefitService.points(CurrentUser.id());
    }

    @PostMapping("/activities/egg/draw")
    UserBenefitService.DrawResult drawEgg() {
        return benefitService.drawEgg(CurrentUser.id());
    }

    @GetMapping("/favorites")
    List<CatalogService.ProductView> favorites() {
        return benefitService.favorites(CurrentUser.id());
    }

    @PostMapping("/favorites/{productCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void favorite(@PathVariable String productCode) {
        benefitService.favorite(CurrentUser.id(), productCode);
    }

    @DeleteMapping("/favorites/{productCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfavorite(@PathVariable String productCode) {
        benefitService.unfavorite(CurrentUser.id(), productCode);
    }
}
