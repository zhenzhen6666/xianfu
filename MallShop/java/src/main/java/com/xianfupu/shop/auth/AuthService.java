package com.xianfupu.shop.auth;

import tools.jackson.databind.JsonNode;
import com.xianfupu.shop.common.BusinessException;
import com.xianfupu.shop.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final RestClient restClient;
    private final String appId;
    private final String appSecret;
    private final boolean devLoginEnabled;
    private final String devAdminSecret;

    public AuthService(JdbcClient jdbc, JdbcTemplate jdbcTemplate, JwtService jwtService,
                       @Value("${app.wechat.app-id:}") String appId,
                       @Value("${app.wechat.app-secret:}") String appSecret,
                       @Value("${app.dev-login-enabled:false}") boolean devLoginEnabled,
                       @Value("${app.dev-admin-secret:}") String devAdminSecret) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.restClient = RestClient.create();
        this.appId = appId;
        this.appSecret = appSecret;
        this.devLoginEnabled = devLoginEnabled;
        this.devAdminSecret = devAdminSecret;
    }

    @Transactional
    public LoginResult wechatLogin(String code, String nickname, String avatarUrl) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_NOT_CONFIGURED",
                    "尚未配置微信小程序 APPID 和密钥");
        }
        String uri = UriComponentsBuilder.fromUriString("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build().encode().toUriString();
        JsonNode result = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        if (result == null || result.path("openid").asText().isBlank()) {
            String message = result == null ? "微信登录失败" : result.path("errmsg").asText("微信登录失败");
            throw BusinessException.badRequest("WECHAT_LOGIN_FAILED", message);
        }
        UserProfile user = findByOpenid(result.path("openid").asText())
                .orElseGet(() -> createUser(result.path("openid").asText(), null, nickname, avatarUrl, "USER"));
        ensureActive(user);
        return result(user);
    }

    @Transactional
    public LoginResult devLogin(String mobile, String nickname) {
        requireDevLogin();
        UserProfile user = Optional.ofNullable(mobile).filter(value -> !value.isBlank())
                .flatMap(this::findByMobile)
                .orElseGet(() -> createUser("dev:" + UUID.randomUUID(), mobile, nickname, null, "USER"));
        ensureActive(user);
        return result(user);
    }

    @Transactional
    public LoginResult devAdminLogin(String secret, String nickname) {
        requireDevLogin();
        if (devAdminSecret.isBlank() || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), devAdminSecret.getBytes(StandardCharsets.UTF_8))) {
            throw BusinessException.forbidden("INVALID_ADMIN_SECRET", "管理员口令不正确");
        }
        UserProfile user = findByOpenid("dev:admin").orElseGet(
                () -> createUser("dev:admin", null, nickname, null, "ADMIN"));
        if (!"ADMIN".equals(user.role())) {
            jdbc.sql("UPDATE users SET role = 'ADMIN', updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                    .param("id", user.id()).update();
            user = findById(user.id());
        }
        return result(user);
    }

    public UserProfile findById(long userId) {
        return jdbc.sql(profileSql() + " WHERE u.id = :id")
                .param("id", userId).query(UserProfile.class).optional()
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND", "用户不存在"));
    }

    private Optional<UserProfile> findByOpenid(String openid) {
        return jdbc.sql(profileSql() + " WHERE u.openid = :openid")
                .param("openid", openid).query(UserProfile.class).optional();
    }

    private Optional<UserProfile> findByMobile(String mobile) {
        return jdbc.sql(profileSql() + " WHERE u.mobile = :mobile ORDER BY u.id LIMIT 1")
                .param("mobile", mobile).query(UserProfile.class).optional();
    }

    private UserProfile createUser(String openid, String mobile, String nickname, String avatarUrl, String role) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users(openid, mobile, nickname, avatar_url, role) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, openid);
            ps.setString(2, mobile);
            ps.setString(3, nickname == null || nickname.isBlank() ? "鲜福铺用户" : nickname);
            ps.setString(4, avatarUrl);
            ps.setString(5, role);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a user id");
        }
        long userId = key.longValue();
        if ("USER".equals(role)) {
            grantNewUserCoupon(userId);
        }
        return findById(userId);
    }

    private void grantNewUserCoupon(long userId) {
        int inserted = jdbc.sql("""
                INSERT INTO user_coupons(user_id, coupon_id)
                SELECT :userId, id FROM coupons
                WHERE code = 'NEW5' AND enabled = TRUE AND CURRENT_TIMESTAMP BETWEEN starts_at AND ends_at
                """).param("userId", userId).update();
        if (inserted == 1) {
            jdbc.sql("UPDATE coupons SET claimed_count = claimed_count + 1 WHERE code = 'NEW5'").update();
        }
    }

    private void requireDevLogin() {
        if (!devLoginEnabled) {
            throw BusinessException.notFound("DEV_LOGIN_DISABLED", "本地登录入口未启用");
        }
    }

    private void ensureActive(UserProfile user) {
        if (!"ACTIVE".equals(user.status())) {
            throw BusinessException.forbidden("USER_DISABLED", "账号已被停用");
        }
    }

    private LoginResult result(UserProfile user) {
        return new LoginResult(jwtService.issue(user.id(), user.role()), "Bearer", user);
    }

    private String profileSql() {
        return """
                SELECT u.id, u.nickname, u.avatar_url, u.mobile, u.role, u.status,
                       u.points, u.total_spent, ml.code AS member_level_code, ml.name AS member_level_name,
                       ml.min_spend AS member_level_min_spend, ml.points_multiplier
                FROM users u JOIN member_levels ml ON ml.id = u.member_level_id
                """;
    }

    public record LoginResult(String accessToken, String tokenType, UserProfile user) {
    }

    public record UserProfile(long id, String nickname, String avatarUrl, String mobile, String role,
                              String status, int points, BigDecimal totalSpent, String memberLevelCode,
                              String memberLevelName, BigDecimal memberLevelMinSpend, BigDecimal pointsMultiplier) {
    }
}
