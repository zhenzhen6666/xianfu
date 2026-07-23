INSERT INTO member_levels (id, code, name, min_spend, points_multiplier, discount_rate, sort_order) VALUES
(1, 'NORMAL', '普通会员', 0, 1.00, 1.0000, 1),
(2, 'SILVER', '银卡会员', 500, 1.20, 0.9800, 2),
(3, 'GOLD', '金卡会员', 2000, 1.50, 0.9500, 3),
(4, 'DIAMOND', '钻石会员', 5000, 2.00, 0.9200, 4);

INSERT INTO categories (id, code, name, sort_order, enabled) VALUES
(1, 'vegetable', '蔬菜', 1, TRUE),
(2, 'fruit', '水果', 2, TRUE),
(3, 'meat-seafood', '肉禽水产', 3, TRUE),
(4, 'grocery', '零食百货', 4, TRUE),
(5, 'prepared', '净菜', 5, TRUE),
(6, 'fruit-cup', '鲜切果盒', 6, TRUE);

INSERT INTO products (id, product_code, category_id, name, subtitle, description, spec, image_url, original_price, sale_price, member_price, stock, sales, badge, sort_order) VALUES
(1, 'p1', 1, '上海青', '清甜脆嫩，当日采摘', '社区直采，当日分拣。', '约 400g/份', '/assets/products/bok-choy.jpg', 5.80, 3.99, 3.59, 86, 1260, '今日鲜采', 1),
(2, 'p2', 1, '沙瓤番茄', '自然熟，酸甜多汁', '自然成熟，适合生食或烹饪。', '约 500g/份', '/assets/products/tomato.jpg', 8.80, 6.80, 6.20, 64, 983, '特价', 2),
(3, 'p3', 2, '奶油草莓', '颗颗精选，香甜软糯', '精选大果，冷链到店。', '250g/盒', '/assets/products/strawberry.jpg', 22.90, 16.90, 15.90, 32, 842, '人气', 3),
(4, 'p4', 2, '阳光甜橙', '果肉饱满，汁水充足', '皮薄多汁，酸甜平衡。', '约 1kg/份', '/assets/products/orange.jpg', 16.80, 12.90, 11.90, 59, 734, '9.9专区', 4),
(5, 'p5', 3, '大西洋三文鱼', '鲜切冷藏，肉质细嫩', '低温冷链，门店鲜切。', '200g/盒', '/assets/products/salmon.jpg', 36.90, 29.90, 27.90, 25, 418, '会员价', 5),
(6, 'p6', 3, '优选鸡胸肉', '低脂高蛋白，冷鲜配送', '去皮鸡胸，冷鲜包装。', '500g/盒', '/assets/products/chicken.jpg', 16.90, 13.90, 12.50, 41, 671, '放心肉', 6),
(7, 'p7', 5, '菌菇鲜拼', '免洗搭配，下锅即烹', '多种菌菇科学搭配。', '350g/盒', '/assets/products/mushroom.jpg', 12.90, 9.90, 8.90, 38, 592, '省心菜', 7),
(8, 'p8', 6, '缤纷鲜切果盒', '现切现装，开盒即食', '门店现切，冷藏配送。', '400g/盒', '/assets/products/fruit-cup.jpg', 19.90, 15.80, 14.20, 44, 1102, '鲜切', 8);

INSERT INTO delivery_configs (name, free_threshold, base_fee, max_distance_km, promise_minutes, announcement, active) VALUES
('默认配送规则', 30.00, 3.00, 3.00, 30, '城区 3 公里配送，满 30 元免配送费，30 分钟送货上门', TRUE);

INSERT INTO coupons (code, name, amount, minimum_amount, total_stock, per_user_limit, new_user_only, starts_at, ends_at) VALUES
('NEW5', '新人满29减5元券', 5.00, 29.00, 10000, 1, TRUE, '2026-01-01 00:00:00', '2030-12-31 23:59:59'),
('MEMBER8', '会员满59减8元券', 8.00, 59.00, 10000, 1, FALSE, '2026-01-01 00:00:00', '2030-12-31 23:59:59'),
('MONTH5', '每月满39减5元券', 5.00, 39.00, 10000, 1, FALSE, '2026-01-01 00:00:00', '2030-12-31 23:59:59');

INSERT INTO activities (id, code, name, activity_type, description, rules_json, starts_at, ends_at) VALUES
(1, 'penny', '一分钱专享', 'FLASH_SALE', '新朋友的第一口新鲜', '{"newUserOnly":true,"limit":1}', '2026-01-01 00:00:00', '2030-12-31 23:59:59'),
(2, 'new', '新人福利站', 'COUPON', '注册即领 18 元券包', '{"autoClaim":true}', '2026-01-01 00:00:00', '2030-12-31 23:59:59'),
(3, 'clean', '今晚在家做饭', 'ZONE', '洗切搭配好，下锅就开饭', '{}', '2026-01-01 00:00:00', '2030-12-31 23:59:59'),
(4, 'egg', '欢乐砸金蛋', 'LOTTERY', '20 积分一次，奖品为优惠券或实物商品', '{"pointsCost":20,"dailyLimit":3}', '2026-01-01 00:00:00', '2030-12-31 23:59:59'),
(5, 'deal', '9.9 特惠专区', 'ZONE', '限时限量，售完即止', '{}', '2026-01-01 00:00:00', '2030-12-31 23:59:59');

INSERT INTO activity_products (activity_id, product_id, activity_price, activity_stock, per_user_limit, sort_order) VALUES
(1, 1, 0.01, 1000, 1, 1), (1, 2, 0.01, 1000, 1, 2),
(3, 7, 9.90, 200, 10, 1), (3, 6, 13.90, 200, 10, 2), (3, 1, 3.99, 200, 10, 3),
(5, 7, 9.90, 200, 10, 1), (5, 1, 3.99, 200, 10, 2), (5, 2, 6.80, 200, 10, 3);

INSERT INTO home_modules (module_type, title, content_json, sort_order) VALUES
('BANNER', '新店开业', '{"image":"","link":"/pages/activity/activity?type=new","text":"新人注册领券"}', 1),
('QUICK_ENTRY', '营销快捷入口', '{"items":[{"name":"一分专享","type":"penny"},{"name":"新人福利","type":"new"},{"name":"绿捷净菜","type":"clean"},{"name":"欢乐金蛋","type":"egg"}]}', 2),
('ACTIVITY_ZONE', '活动专区', '{"items":[{"title":"今晚在家做饭","type":"clean"},{"title":"9.9 特惠专区","type":"deal"},{"title":"鲜切果盒专区","type":"fruit-cup"}]}', 3),
('ANNOUNCEMENT', '配送公告', '{"text":"城区 3 公里配送，满 30 元免配送费，30 分钟送货上门"}', 4);
