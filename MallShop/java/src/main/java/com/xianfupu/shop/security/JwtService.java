package com.xianfupu.shop.security;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expireHours;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-hours:168}") long expireHours) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must contain at least 32 bytes");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireHours = expireHours;
    }

    public String issue(long userId, String role) {
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            Instant now = Instant.now();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", Long.toString(userId));
            claims.put("role", role);
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", now.plus(expireHours, ChronoUnit.HOURS).getEpochSecond());
            String payload = encodeJson(claims);
            String unsigned = header + "." + payload;
            return unsigned + "." + URL_ENCODER.encodeToString(sign(unsigned));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue token", ex);
        }
    }

    public TokenClaims verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed token");
            }
            byte[] actual = URL_DECODER.decode(parts[2]);
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(actual, expected)) {
                throw new IllegalArgumentException("Invalid signature");
            }
            Map<String, Object> claims = objectMapper.readValue(
                    URL_DECODER.decode(parts[1]), new TypeReference<>() {});
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new IllegalArgumentException("Expired token");
            }
            return new TokenClaims(Long.parseLong((String) claims.get("sub")), (String) claims.get("role"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid token", ex);
        }
    }

    private String encodeJson(Object value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    public record TokenClaims(long userId, String role) {
    }
}
