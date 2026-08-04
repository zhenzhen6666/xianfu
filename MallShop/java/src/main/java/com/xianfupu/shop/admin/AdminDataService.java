package com.xianfupu.shop.admin;

import com.xianfupu.shop.catalog.SearchAnalyticsService;
import com.xianfupu.shop.common.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminDataService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcClient jdbc;
    private final SearchAnalyticsService searchAnalytics;

    public AdminDataService(JdbcClient jdbc, SearchAnalyticsService searchAnalytics) {
        this.jdbc = jdbc;
        this.searchAnalytics = searchAnalytics;
    }

    @Transactional
    public ExportFile export(long adminId, String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        CsvData data = switch (type) {
            case "orders" -> orderData();
            case "products" -> productData();
            case "users" -> userData();
            case "coupons" -> couponData();
            default -> throw BusinessException.badRequest(
                    "INVALID_EXPORT_TYPE", "仅支持导出订单、商品、用户和优惠券数据");
        };
        jdbc.sql("""
                INSERT INTO admin_audit_logs(admin_user_id, operation, target_type, target_id, detail_json)
                VALUES (:adminId, 'EXPORT_DATA', 'DATA_EXPORT', :type, :detail)
                """).param("adminId", adminId).param("type", type)
                .param("detail", "{\"rows\":" + data.rows().size() + "}").update();
        byte[] csv = csv(data.headers(), data.rows());
        return new ExportFile(type + "-" + LocalDateTime.now().format(FILE_TIME) + ".csv", csv,
                data.rows().size());
    }

    public List<SearchAnalyticsService.KeywordStat> searchKeywordStats(int days) {
        return searchAnalytics.stats(days);
    }

    private CsvData orderData() {
        List<OrderExportRow> source = jdbc.sql("""
                SELECT o.order_no, o.status, u.nickname, u.mobile,
                       (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.id) AS item_count,
                       o.subtotal, o.delivery_fee, o.coupon_discount, o.member_discount,
                       o.points_used, o.points_discount, o.pay_amount,
                       o.receiver_name, o.receiver_mobile, o.receiver_address, o.note,
                       o.created_at, o.paid_at, o.completed_at, o.cancelled_at, o.cancel_reason
                FROM shop_orders o JOIN users u ON u.id = o.user_id
                ORDER BY o.created_at DESC, o.id DESC
                """).query(OrderExportRow.class).list();
        List<List<Object>> rows = source.stream().map(row -> List.of(
                value(row.orderNo()), value(row.status()), value(row.nickname()), value(row.mobile()),
                row.itemCount(), row.subtotal(), row.deliveryFee(), row.couponDiscount(),
                row.memberDiscount(), row.pointsUsed(), row.pointsDiscount(), row.payAmount(),
                value(row.receiverName()), value(row.receiverMobile()), value(row.receiverAddress()),
                value(row.note()), value(row.createdAt()), value(row.paidAt()), value(row.completedAt()),
                value(row.cancelledAt()), value(row.cancelReason()))).toList();
        return new CsvData(List.of("订单号", "订单状态", "用户昵称", "用户手机号", "商品件数", "商品金额",
                "配送费", "优惠券优惠", "会员优惠", "使用积分", "积分抵扣", "实付金额", "收货人",
                "收货手机号", "收货地址", "订单备注", "下单时间", "支付时间", "完成时间", "取消时间", "取消原因"), rows);
    }

    private CsvData productData() {
        List<ProductExportRow> source = jdbc.sql("""
                SELECT p.product_code, c.name AS category_name, p.name, p.subtitle, p.spec,
                       p.original_price, p.sale_price, p.member_price, p.stock, p.sales,
                       p.badge, p.status, p.sort_order, p.created_at, p.updated_at
                FROM products p JOIN categories c ON c.id = p.category_id
                ORDER BY p.sort_order, p.id
                """).query(ProductExportRow.class).list();
        List<List<Object>> rows = source.stream().map(row -> List.of(
                value(row.productCode()), value(row.categoryName()), value(row.name()), value(row.subtitle()),
                value(row.spec()), row.originalPrice(), row.salePrice(), value(row.memberPrice()),
                row.stock(), row.sales(), value(row.badge()), value(row.status()), row.sortOrder(),
                value(row.createdAt()), value(row.updatedAt()))).toList();
        return new CsvData(List.of("商品编码", "分类", "商品名称", "副标题", "规格", "原价", "销售价",
                "会员价", "库存", "销量", "角标", "状态", "排序", "创建时间", "更新时间"), rows);
    }

    private CsvData userData() {
        List<UserExportRow> source = jdbc.sql("""
                SELECT u.id, u.nickname, u.mobile, u.role, u.status, ml.name AS member_level,
                       u.points, u.total_spent, u.created_at, u.updated_at
                FROM users u JOIN member_levels ml ON ml.id = u.member_level_id
                ORDER BY u.created_at DESC, u.id DESC
                """).query(UserExportRow.class).list();
        List<List<Object>> rows = source.stream().map(row -> List.of(
                row.id(), value(row.nickname()), value(row.mobile()), value(row.role()), value(row.status()),
                value(row.memberLevel()), row.points(), row.totalSpent(), value(row.createdAt()),
                value(row.updatedAt()))).toList();
        return new CsvData(List.of("用户ID", "昵称", "手机号", "角色", "账号状态", "会员等级", "积分",
                "累计消费", "注册时间", "更新时间"), rows);
    }

    private CsvData couponData() {
        List<CouponExportRow> source = jdbc.sql("""
                SELECT code, name, coupon_type, amount, minimum_amount, total_stock, claimed_count,
                       per_user_limit, new_user_only, starts_at, ends_at, enabled, created_at
                FROM coupons ORDER BY created_at DESC, id DESC
                """).query(CouponExportRow.class).list();
        List<List<Object>> rows = source.stream().map(row -> List.of(
                value(row.code()), value(row.name()), value(row.couponType()), row.amount(),
                row.minimumAmount(), row.totalStock(), row.claimedCount(),
                Math.max(0, row.totalStock() - row.claimedCount()), row.perUserLimit(),
                row.newUserOnly() ? "是" : "否", value(row.startsAt()), value(row.endsAt()),
                row.enabled() ? "启用" : "停用", value(row.createdAt()))).toList();
        return new CsvData(List.of("优惠券编码", "名称", "类型", "优惠金额", "使用门槛", "发行总量",
                "已领取", "剩余数量", "每人限领", "仅新用户", "开始时间", "结束时间", "状态", "创建时间"), rows);
    }

    private byte[] csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder value = new StringBuilder("\uFEFF");
        appendRow(value, new ArrayList<>(headers));
        rows.forEach(row -> appendRow(value, row));
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder target, List<?> row) {
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) target.append(',');
            target.append(csvCell(row.get(index)));
        }
        target.append("\r\n");
    }

    private String csvCell(Object raw) {
        String value;
        if (raw == null) value = "";
        else if (raw instanceof BigDecimal decimal) value = decimal.toPlainString();
        else value = raw.toString();
        if (raw instanceof String && !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private Object value(Object value) {
        return value == null ? "" : value instanceof LocalDateTime time ? time.format(DATE_TIME) : value;
    }

    public record ExportFile(String filename, byte[] content, int rowCount) {
    }

    private record CsvData(List<String> headers, List<List<Object>> rows) {
    }

    private record OrderExportRow(String orderNo, String status, String nickname, String mobile,
                                  int itemCount, BigDecimal subtotal, BigDecimal deliveryFee,
                                  BigDecimal couponDiscount, BigDecimal memberDiscount, int pointsUsed,
                                  BigDecimal pointsDiscount, BigDecimal payAmount, String receiverName,
                                  String receiverMobile, String receiverAddress, String note,
                                  LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime completedAt,
                                  LocalDateTime cancelledAt, String cancelReason) {
    }

    private record ProductExportRow(String productCode, String categoryName, String name, String subtitle,
                                    String spec, BigDecimal originalPrice, BigDecimal salePrice,
                                    BigDecimal memberPrice, int stock, int sales, String badge,
                                    String status, int sortOrder, LocalDateTime createdAt,
                                    LocalDateTime updatedAt) {
    }

    private record UserExportRow(long id, String nickname, String mobile, String role, String status,
                                 String memberLevel, int points, BigDecimal totalSpent,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private record CouponExportRow(String code, String name, String couponType, BigDecimal amount,
                                   BigDecimal minimumAmount, int totalStock, int claimedCount,
                                   int perUserLimit, boolean newUserOnly, LocalDateTime startsAt,
                                   LocalDateTime endsAt, boolean enabled, LocalDateTime createdAt) {
    }
}
