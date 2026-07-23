package com.xianfupu.shop.user;

import com.xianfupu.shop.catalog.CatalogService;
import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserBenefitService {
    private final JdbcClient jdbc;
    private final CatalogService catalogService;

    public UserBenefitService(JdbcClient jdbc, CatalogService catalogService) {
        this.jdbc = jdbc;
        this.catalogService = catalogService;
    }

    public List<CouponView> coupons(long userId, String status) {
        String condition = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? "" : " AND uc.status = :status";
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT uc.id, c.code, c.name, c.amount, c.minimum_amount, uc.status,
                       c.starts_at, c.ends_at, uc.claimed_at, uc.used_at
                FROM user_coupons uc JOIN coupons c ON c.id = uc.coupon_id
                WHERE uc.user_id = :userId
                """ + condition + " ORDER BY uc.claimed_at DESC")
                .param("userId", userId);
        if (!condition.isBlank()) {
            query = query.param("status", status.toUpperCase());
        }
        return query.query(CouponView.class).list();
    }

    @Transactional
    public CouponView claim(long userId, String code) {
        CouponDefinition coupon = jdbc.sql("""
                SELECT id, code, name, amount, minimum_amount, total_stock, claimed_count,
                       per_user_limit, new_user_only
                FROM coupons WHERE code = :code AND enabled = TRUE
                  AND CURRENT_TIMESTAMP BETWEEN starts_at AND ends_at FOR UPDATE
                """).param("code", code).query(CouponDefinition.class).optional()
                .orElseThrow(() -> BusinessException.notFound("COUPON_NOT_FOUND", "优惠券不存在或不在领取期"));
        if (coupon.claimedCount() >= coupon.totalStock()) {
            throw BusinessException.conflict("COUPON_OUT_OF_STOCK", "优惠券已领完");
        }
        long claimed = jdbc.sql("SELECT COUNT(*) FROM user_coupons WHERE user_id = :userId AND coupon_id = :couponId")
                .param("userId", userId).param("couponId", coupon.id()).query(Long.class).single();
        if (claimed >= coupon.perUserLimit()) {
            throw BusinessException.conflict("COUPON_LIMIT_REACHED", "您已领取过该优惠券");
        }
        if (coupon.newUserOnly()) {
            long orders = jdbc.sql("SELECT COUNT(*) FROM shop_orders WHERE user_id = :userId AND status <> 'CANCELLED'")
                    .param("userId", userId).query(Long.class).single();
            if (orders > 0) {
                throw BusinessException.forbidden("NEW_USER_ONLY", "该优惠券仅限新用户领取");
            }
        }
        jdbc.sql("INSERT INTO user_coupons(user_id, coupon_id) VALUES (:userId, :couponId)")
                .param("userId", userId).param("couponId", coupon.id()).update();
        jdbc.sql("UPDATE coupons SET claimed_count = claimed_count + 1 WHERE id = :id")
                .param("id", coupon.id()).update();
        return coupons(userId, "AVAILABLE").stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    public PointAccount points(long userId) {
        int balance = jdbc.sql("SELECT points FROM users WHERE id = :userId")
                .param("userId", userId).query(Integer.class).single();
        List<PointRecord> records = jdbc.sql("""
                SELECT change_amount, balance_after, transaction_type, remark, created_at
                FROM point_transactions WHERE user_id = :userId ORDER BY id DESC LIMIT 100
                """).param("userId", userId).query(PointRecord.class).list();
        return new PointAccount(balance, 100, records);
    }

    @Transactional
    public DrawResult drawEgg(long userId) {
        ActivityId activity = jdbc.sql("""
                SELECT id FROM activities WHERE code = 'egg' AND enabled = TRUE
                  AND CURRENT_TIMESTAMP BETWEEN starts_at AND ends_at FOR UPDATE
                """).query(ActivityId.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ACTIVITY_NOT_FOUND", "砸金蛋活动当前未开放"));
        long todayCount = jdbc.sql("""
                SELECT COUNT(*) FROM activity_participations
                WHERE activity_id = :activityId AND user_id = :userId
                  AND participated_at >= CURRENT_DATE
                """).param("activityId", activity.id()).param("userId", userId).query(Long.class).single();
        if (todayCount >= 3) {
            throw BusinessException.conflict("DAILY_LIMIT_REACHED", "今天已参与 3 次，请明天再来");
        }
        int changed = jdbc.sql("UPDATE users SET points = points - 20 WHERE id = :userId AND points >= 20")
                .param("userId", userId).update();
        if (changed == 0) {
            throw BusinessException.conflict("INSUFFICIENT_POINTS", "积分不足，砸金蛋需要 20 积分");
        }
        boolean couponReward = ThreadLocalRandom.current().nextInt(100) < 80;
        String rewardType;
        String rewardValue;
        String message;
        if (couponReward) {
            Long couponId = jdbc.sql("SELECT id FROM coupons WHERE code = 'MONTH5' AND enabled = TRUE")
                    .query(Long.class).single();
            jdbc.sql("INSERT INTO user_coupons(user_id, coupon_id) VALUES (:userId, :couponId)")
                    .param("userId", userId).param("couponId", couponId).update();
            jdbc.sql("UPDATE coupons SET claimed_count = claimed_count + 1 WHERE id = :id")
                    .param("id", couponId).update();
            rewardType = "COUPON";
            rewardValue = "MONTH5";
            message = "恭喜获得 5 元优惠券";
        } else {
            jdbc.sql("UPDATE users SET points = points + 10 WHERE id = :userId").param("userId", userId).update();
            rewardType = "POINTS";
            rewardValue = "10";
            message = "恭喜获得 10 积分";
        }
        jdbc.sql("""
                INSERT INTO activity_participations(activity_id, user_id, points_cost, reward_type, reward_value)
                VALUES (:activityId, :userId, 20, :rewardType, :rewardValue)
                """).param("activityId", activity.id()).param("userId", userId)
                .param("rewardType", rewardType).param("rewardValue", rewardValue).update();
        int balance = jdbc.sql("SELECT points FROM users WHERE id = :userId")
                .param("userId", userId).query(Integer.class).single();
        int netChange = couponReward ? -20 : -10;
        jdbc.sql("""
                INSERT INTO point_transactions(user_id, change_amount, balance_after, transaction_type, remark)
                VALUES (:userId, :change, :balance, 'ACTIVITY', '欢乐砸金蛋')
                """).param("userId", userId).param("change", netChange).param("balance", balance).update();
        return new DrawResult(rewardType, rewardValue, message, balance, (int) todayCount + 1, 3);
    }

    public List<CatalogService.ProductView> favorites(long userId) {
        List<String> codes = jdbc.sql("""
                SELECT p.product_code FROM product_favorites f JOIN products p ON p.id = f.product_id
                WHERE f.user_id = :userId ORDER BY f.created_at DESC
                """).param("userId", userId).query(String.class).list();
        return codes.stream().map(catalogService::product).toList();
    }

    @Transactional
    public void favorite(long userId, String productCode) {
        Long productId = jdbc.sql("SELECT id FROM products WHERE product_code = :code AND status = 'ON_SALE'")
                .param("code", productCode).query(Long.class).optional()
                .orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在或已下架"));
        long exists = jdbc.sql("SELECT COUNT(*) FROM product_favorites WHERE user_id = :userId AND product_id = :productId")
                .param("userId", userId).param("productId", productId).query(Long.class).single();
        if (exists == 0) {
            jdbc.sql("INSERT INTO product_favorites(user_id, product_id) VALUES (:userId, :productId)")
                    .param("userId", userId).param("productId", productId).update();
        }
    }

    @Transactional
    public void unfavorite(long userId, String productCode) {
        jdbc.sql("""
                DELETE FROM product_favorites WHERE user_id = :userId
                  AND product_id = (SELECT id FROM products WHERE product_code = :code)
                """).param("userId", userId).param("code", productCode).update();
    }

    private record CouponDefinition(long id, String code, String name, BigDecimal amount,
                                    BigDecimal minimumAmount, int totalStock, int claimedCount,
                                    int perUserLimit, boolean newUserOnly) {
    }

    private record ActivityId(long id) {
    }

    public record CouponView(long id, String code, String name, BigDecimal amount,
                             BigDecimal minimumAmount, String status, LocalDateTime startsAt,
                             LocalDateTime endsAt, LocalDateTime claimedAt, LocalDateTime usedAt) {
    }

    public record PointRecord(int changeAmount, int balanceAfter, String transactionType,
                              String remark, LocalDateTime createdAt) {
    }

    public record PointAccount(int balance, int pointsPerYuan, List<PointRecord> records) {
    }

    public record DrawResult(String rewardType, String rewardValue, String message, int pointsBalance,
                             int todayCount, int dailyLimit) {
    }
}
