package com.xianfupu.shop.security;

import com.xianfupu.shop.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AuthSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcClient jdbc;
    private final JwtService jwtService;
    private final int refreshExpireDays;

    public AuthSessionService(JdbcClient jdbc, JwtService jwtService,
                              @Value("${app.jwt.refresh-expire-days:30}") int refreshExpireDays) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.refreshExpireDays = refreshExpireDays;
    }

    @Transactional
    public SessionTokens create(long userId, String role, String ipAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = randomToken();
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(refreshExpireDays);
        jdbc.sql("""
                INSERT INTO auth_sessions(id, user_id, role, refresh_token_hash, expires_at, ip_address, user_agent)
                VALUES (:id, :userId, :role, :hash, :expiresAt, :ip, :userAgent)
                """).param("id", sessionId).param("userId", userId).param("role", role)
                .param("hash", hash(refreshToken)).param("expiresAt", refreshExpiresAt)
                .param("ip", trim(ipAddress, 64)).param("userAgent", trim(userAgent, 500)).update();
        return tokens(sessionId, userId, role, refreshToken, refreshExpiresAt);
    }

    @Transactional
    public SessionTokens refresh(String refreshToken, String ipAddress, String userAgent) {
        SessionRow session = jdbc.sql("""
                SELECT id, user_id, role, revoked, expires_at
                FROM auth_sessions WHERE refresh_token_hash = :hash FOR UPDATE
                """).param("hash", hash(refreshToken)).query(SessionRow.class).optional()
                .orElseThrow(this::invalidRefresh);
        if (session.revoked() || !session.expiresAt().isAfter(LocalDateTime.now())) {
            throw invalidRefresh();
        }
        String rotated = randomToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshExpireDays);
        jdbc.sql("""
                UPDATE auth_sessions SET refresh_token_hash = :hash, expires_at = :expiresAt,
                    last_seen_at = CURRENT_TIMESTAMP, ip_address = :ip, user_agent = :userAgent
                WHERE id = :id
                """).param("hash", hash(rotated)).param("expiresAt", expiresAt)
                .param("ip", trim(ipAddress, 64)).param("userAgent", trim(userAgent, 500))
                .param("id", session.id()).update();
        return tokens(session.id(), session.userId(), session.role(), rotated, expiresAt);
    }

    public boolean isActive(String sessionId, long userId, String role) {
        if (sessionId == null || sessionId.isBlank()) return false;
        boolean active = jdbc.sql("""
                SELECT COUNT(*) FROM auth_sessions
                WHERE id = :id AND user_id = :userId AND role = :role AND revoked = FALSE
                  AND expires_at > CURRENT_TIMESTAMP
                """).param("id", sessionId).param("userId", userId).param("role", role)
                .query(Long.class).single() == 1;
        if (active) {
            jdbc.sql("UPDATE auth_sessions SET last_seen_at = CURRENT_TIMESTAMP WHERE id = :id")
                    .param("id", sessionId).update();
        }
        return active;
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        jdbc.sql("""
                UPDATE auth_sessions SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP
                WHERE refresh_token_hash = :hash AND revoked = FALSE
                """).param("hash", hash(refreshToken)).update();
    }

    @Transactional
    public void revoke(String sessionId) {
        int changed = jdbc.sql("""
                UPDATE auth_sessions SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP
                WHERE id = :id AND revoked = FALSE
                """).param("id", sessionId).update();
        if (changed == 0) {
            throw BusinessException.notFound("SESSION_NOT_FOUND", "会话不存在或已失效");
        }
    }

    public List<SessionView> list(String role) {
        String condition = role == null || role.isBlank() ? "" : " WHERE s.role = :role";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT s.id, s.user_id, u.nickname, u.mobile, s.role, s.revoked, s.expires_at,
                       s.last_seen_at, s.ip_address, s.user_agent, s.created_at, s.revoked_at
                FROM auth_sessions s JOIN users u ON u.id = s.user_id
                """ + condition + " ORDER BY s.created_at DESC LIMIT 500");
        if (!condition.isBlank()) query = query.param("role", role.toUpperCase());
        return query.query(SessionView.class).list();
    }

    private SessionTokens tokens(String sessionId, long userId, String role, String refreshToken,
                                 LocalDateTime refreshExpiresAt) {
        String accessToken = jwtService.issue(userId, role, sessionId);
        Instant accessExpiresAt = Instant.now().plus(jwtService.expireSeconds(), ChronoUnit.SECONDS);
        return new SessionTokens(sessionId, accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    private String randomToken() {
        byte[] value = new byte[48];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String value) {
        if (value == null || value.isBlank()) throw invalidRefresh();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }

    private BusinessException invalidRefresh() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "登录已失效，请重新登录");
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    private record SessionRow(String id, long userId, String role, boolean revoked, LocalDateTime expiresAt) {
    }

    public record SessionTokens(String sessionId, String accessToken, String refreshToken,
                                Instant accessExpiresAt, LocalDateTime refreshExpiresAt) {
    }

    public record SessionView(String id, long userId, String nickname, String mobile, String role,
                              boolean revoked, LocalDateTime expiresAt, LocalDateTime lastSeenAt,
                              String ipAddress, String userAgent, LocalDateTime createdAt,
                              LocalDateTime revokedAt) {
    }
}
