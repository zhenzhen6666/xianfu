package com.xianfupu.shop.user;

import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressService {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public AddressService(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AddressView> list(long userId) {
        return jdbc.sql(addressSql() + " WHERE user_id = :userId ORDER BY is_default DESC, updated_at DESC")
                .param("userId", userId).query(AddressView.class).list();
    }

    public AddressView getOwned(long userId, long addressId) {
        return jdbc.sql(addressSql() + " WHERE id = :id AND user_id = :userId")
                .param("id", addressId).param("userId", userId).query(AddressView.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ADDRESS_NOT_FOUND", "收货地址不存在"));
    }

    @Transactional
    public AddressView create(long userId, AddressCommand command) {
        boolean first = jdbc.sql("SELECT COUNT(*) FROM addresses WHERE user_id = :userId")
                .param("userId", userId).query(Long.class).single() == 0;
        boolean makeDefault = first || command.isDefault();
        if (makeDefault) {
            clearDefaults(userId);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO addresses(user_id, receiver_name, mobile, detail, longitude, latitude, distance_km, is_default)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, command.name());
            ps.setString(3, command.phone());
            ps.setString(4, command.detail());
            ps.setObject(5, command.longitude());
            ps.setObject(6, command.latitude());
            ps.setObject(7, command.distanceKm());
            ps.setBoolean(8, makeDefault);
            return ps;
        }, keyHolder);
        return getOwned(userId, keyHolder.getKey().longValue());
    }

    @Transactional
    public AddressView update(long userId, long addressId, AddressCommand command) {
        getOwned(userId, addressId);
        if (command.isDefault()) {
            clearDefaults(userId);
        }
        jdbc.sql("""
                UPDATE addresses SET receiver_name = :name, mobile = :phone, detail = :detail,
                    longitude = :longitude, latitude = :latitude, distance_km = :distance,
                    is_default = :isDefault, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND user_id = :userId
                """).param("name", command.name()).param("phone", command.phone())
                .param("detail", command.detail()).param("longitude", command.longitude())
                .param("latitude", command.latitude()).param("distance", command.distanceKm())
                .param("isDefault", command.isDefault()).param("id", addressId).param("userId", userId).update();
        ensureOneDefault(userId);
        return getOwned(userId, addressId);
    }

    @Transactional
    public void delete(long userId, long addressId) {
        AddressView address = getOwned(userId, addressId);
        jdbc.sql("DELETE FROM addresses WHERE id = :id AND user_id = :userId")
                .param("id", addressId).param("userId", userId).update();
        if (address.isDefault()) {
            ensureOneDefault(userId);
        }
    }

    private void clearDefaults(long userId) {
        jdbc.sql("UPDATE addresses SET is_default = FALSE WHERE user_id = :userId")
                .param("userId", userId).update();
    }

    private void ensureOneDefault(long userId) {
        long defaults = jdbc.sql("SELECT COUNT(*) FROM addresses WHERE user_id = :userId AND is_default = TRUE")
                .param("userId", userId).query(Long.class).single();
        if (defaults == 0) {
            jdbc.sql("""
                    UPDATE addresses SET is_default = TRUE
                    WHERE id = (SELECT id FROM (SELECT id FROM addresses WHERE user_id = :userId ORDER BY updated_at DESC LIMIT 1) a)
                    """).param("userId", userId).update();
        }
    }

    private String addressSql() {
        return """
                SELECT id, receiver_name AS name, mobile AS phone, detail, longitude, latitude,
                       distance_km, is_default, created_at, updated_at FROM addresses
                """;
    }

    public record AddressCommand(String name, String phone, String detail, BigDecimal longitude,
                                 BigDecimal latitude, BigDecimal distanceKm, boolean isDefault) {
    }

    public record AddressView(long id, String name, String phone, String detail, BigDecimal longitude,
                              BigDecimal latitude, BigDecimal distanceKm, boolean isDefault,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
