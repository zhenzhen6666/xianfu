package com.xianfupu.shop.catalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ContentController {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final CatalogService catalogService;

    public ContentController(JdbcClient jdbc, ObjectMapper objectMapper, CatalogService catalogService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
    }

    @GetMapping("/home")
    HomeView home() {
        List<HomeModuleRow> rows = jdbc.sql("""
                SELECT id, module_type, title, content_json, sort_order
                FROM home_modules WHERE enabled = TRUE ORDER BY sort_order, id
                """).query(HomeModuleRow.class).list();
        List<HomeModule> modules = new ArrayList<>();
        for (HomeModuleRow row : rows) {
            try {
                modules.add(new HomeModule(row.id(), row.moduleType(), row.title(),
                        objectMapper.readTree(row.contentJson()), row.sortOrder()));
            } catch (Exception ex) {
                throw new IllegalStateException("Invalid home module JSON: " + row.id(), ex);
            }
        }
        return new HomeView(modules, catalogService.products(null, null, "sales", false, false, 0, 6).items(),
                catalogService.deliveryConfig());
    }

    @GetMapping("/config/delivery")
    CatalogService.DeliveryConfig deliveryConfig() {
        return catalogService.deliveryConfig();
    }

    @GetMapping("/activities")
    List<ActivityView> activities() {
        return jdbc.sql("""
                SELECT code, name, activity_type, description, starts_at, ends_at
                FROM activities
                WHERE enabled = TRUE AND CURRENT_TIMESTAMP BETWEEN starts_at AND ends_at
                ORDER BY id
                """).query(ActivityView.class).list();
    }

    @GetMapping("/activities/{code}")
    ActivityDetail activity(@PathVariable("code") String code) {
        ActivityView activity = jdbc.sql("""
                SELECT code, name, activity_type, description, starts_at, ends_at
                FROM activities WHERE code = :code AND enabled = TRUE
                """).param("code", code).query(ActivityView.class).optional()
                .orElseThrow(() -> BusinessException.notFound("ACTIVITY_NOT_FOUND", "活动不存在"));
        List<ActivityProduct> products = jdbc.sql("""
                SELECT p.product_code AS id, p.name, p.subtitle, p.spec, p.image_url AS image,
                       COALESCE(ap.activity_price, p.sale_price) AS price, p.original_price,
                       p.member_price, LEAST(p.stock, COALESCE(ap.activity_stock, p.stock)) AS stock,
                       p.sales, p.badge, ap.per_user_limit
                FROM activity_products ap JOIN activities a ON a.id = ap.activity_id
                JOIN products p ON p.id = ap.product_id
                WHERE a.code = :code AND p.status = 'ON_SALE'
                ORDER BY ap.sort_order, ap.id
                """).param("code", code).query(ActivityProduct.class).list();
        return new ActivityDetail(activity, products);
    }

    private record HomeModuleRow(long id, String moduleType, String title, String contentJson, int sortOrder) {
    }

    public record HomeModule(long id, String moduleType, String title, JsonNode content, int sortOrder) {
    }

    public record HomeView(List<HomeModule> modules, List<CatalogService.ProductView> recommendedProducts,
                           CatalogService.DeliveryConfig delivery) {
    }

    public record ActivityView(String code, String name, String activityType, String description,
                               java.time.LocalDateTime startsAt, java.time.LocalDateTime endsAt) {
    }

    public record ActivityProduct(String id, String name, String subtitle, String spec, String image,
                                  BigDecimal price, BigDecimal originalPrice, BigDecimal memberPrice,
                                  int stock, int sales, String badge, Integer perUserLimit) {
    }

    public record ActivityDetail(ActivityView activity, List<ActivityProduct> products) {
    }
}
