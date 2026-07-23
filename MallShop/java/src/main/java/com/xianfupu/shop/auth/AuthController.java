package com.xianfupu.shop.auth;

import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wechat-login")
    AuthService.LoginResult wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return authService.wechatLogin(request.code(), request.nickname(), request.avatarUrl());
    }

    @PostMapping("/dev-login")
    AuthService.LoginResult devLogin(@Valid @RequestBody DevLoginRequest request) {
        return authService.devLogin(request.mobile(), request.nickname());
    }

    @PostMapping("/dev-admin-login")
    AuthService.LoginResult devAdminLogin(@Valid @RequestBody DevAdminLoginRequest request) {
        return authService.devAdminLogin(request.secret(), request.nickname());
    }

    @GetMapping("/me")
    AuthService.UserProfile me() {
        return authService.findById(CurrentUser.id());
    }

    public record WechatLoginRequest(@NotBlank String code, @Size(max = 80) String nickname,
                                     @Size(max = 500) String avatarUrl) {
    }

    public record DevLoginRequest(@Size(max = 32) String mobile, @NotBlank @Size(max = 80) String nickname) {
    }

    public record DevAdminLoginRequest(@NotBlank String secret, @NotBlank @Size(max = 80) String nickname) {
    }
}
