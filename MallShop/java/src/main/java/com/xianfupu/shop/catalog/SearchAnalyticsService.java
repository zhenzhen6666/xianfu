package com.xianfupu.shop.catalog;

import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SearchAnalyticsService {
    private final JdbcClient jdbc;

    public SearchAnalyticsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public SearchHistory record(long userId, String rawKeyword) {
        String keyword = normalize(rawKeyword);
        int resultCount = jdbc.sql("""
                SELECT COUNT(*) FROM products p JOIN categories c ON c.id = p.category_id
                WHERE p.status = 'ON_SALE' AND c.enabled = TRUE
                  AND (LOWER(p.name) LIKE :keyword OR LOWER(COALESCE(p.subtitle, '')) LIKE :keyword
                       OR LOWER(c.name) LIKE :keyword)
                """).param("keyword", "%" + keyword + "%").query(Integer.class).single();
        jdbc.sql("""
                INSERT INTO search_events(user_id, keyword, result_count)
                VALUES (:userId, :keyword, :resultCount)
                """).param("userId", userId).param("keyword", keyword)
                .param("resultCount", resultCount).update();
        int updated = jdbc.sql("""
                UPDATE user_search_history SET result_count = :resultCount,
                    searched_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId AND keyword = :keyword
                """).param("resultCount", resultCount).param("userId", userId)
                .param("keyword", keyword).update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO user_search_history(user_id, keyword, result_count)
                    VALUES (:userId, :keyword, :resultCount)
                    """).param("userId", userId).param("keyword", keyword)
                    .param("resultCount", resultCount).update();
        }
        return jdbc.sql("""
                SELECT keyword, result_count, searched_at FROM user_search_history
                WHERE user_id = :userId AND keyword = :keyword
                """).param("userId", userId).param("keyword", keyword)
                .query(SearchHistory.class).single();
    }

    public List<SearchHistory> history(long userId) {
        return jdbc.sql("""
                SELECT keyword, result_count, searched_at FROM user_search_history
                WHERE user_id = :userId ORDER BY searched_at DESC, id DESC LIMIT 20
                """).param("userId", userId).query(SearchHistory.class).list();
    }

    @Transactional
    public void clearHistory(long userId) {
        jdbc.sql("DELETE FROM user_search_history WHERE user_id = :userId")
                .param("userId", userId).update();
    }

    public List<HotKeyword> hot(int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays(days));
        int safeLimit = Math.min(30, Math.max(1, limit));
        return jdbc.sql("""
                SELECT keyword, COUNT(*) AS search_count, MAX(searched_at) AS last_searched_at
                FROM search_events
                WHERE searched_at >= :since AND result_count > 0
                GROUP BY keyword
                ORDER BY search_count DESC, last_searched_at DESC
                LIMIT :limit
                """).param("since", since).param("limit", safeLimit)
                .query(HotKeyword.class).list();
    }

    public List<KeywordStat> stats(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays(days));
        return jdbc.sql("""
                SELECT keyword, COUNT(*) AS search_count,
                       SUM(CASE WHEN result_count = 0 THEN 1 ELSE 0 END) AS no_result_count,
                       MAX(result_count) AS max_result_count,
                       MAX(searched_at) AS last_searched_at
                FROM search_events WHERE searched_at >= :since
                GROUP BY keyword
                ORDER BY search_count DESC, no_result_count DESC, last_searched_at DESC
                LIMIT 200
                """).param("since", since).query(KeywordStatRow.class).list().stream()
                .map(row -> new KeywordStat(row.keyword(), row.searchCount(), row.noResultCount(),
                        row.searchCount() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(row.noResultCount())
                                .divide(BigDecimal.valueOf(row.searchCount()), 4, RoundingMode.HALF_UP),
                        row.maxResultCount(), row.lastSearchedAt()))
                .toList();
    }

    private int safeDays(int days) {
        return Math.min(365, Math.max(1, days));
    }

    private String normalize(String rawKeyword) {
        String keyword = rawKeyword == null ? "" : rawKeyword.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        if (keyword.isBlank() || keyword.length() > 80) {
            throw BusinessException.badRequest("INVALID_SEARCH_KEYWORD", "搜索关键词长度必须为 1 到 80 个字符");
        }
        return keyword;
    }

    public record SearchHistory(String keyword, int resultCount, LocalDateTime searchedAt) {
    }

    public record HotKeyword(String keyword, long searchCount, LocalDateTime lastSearchedAt) {
    }

    public record KeywordStat(String keyword, long searchCount, long noResultCount,
                              BigDecimal noResultRate, int maxResultCount,
                              LocalDateTime lastSearchedAt) {
    }

    private record KeywordStatRow(String keyword, long searchCount, long noResultCount,
                                  int maxResultCount, LocalDateTime lastSearchedAt) {
    }
}
