# 小程序接口接入说明

基础地址默认是 `http://localhost:8080/api`。除公开商品、首页、活动、配送配置、登录和支付回调外，其余接口都需要 `Authorization: Bearer <token>`。

## 主要接口

| 模块 | 方法与路径 | 用途 |
|---|---|---|
| 登录 | `POST /auth/wechat-login` | 微信登录 |
| 登录 | `POST /auth/dev-login` | 本地开发登录 |
| 首页 | `GET /home` | 首页装修模块、推荐商品、配送规则 |
| 商品 | `GET /catalog/categories` | 分类列表 |
| 商品 | `GET /catalog/products` | 搜索、筛选、排序、分页 |
| 商品 | `GET /catalog/products/{id}` | 商品详情；`id` 与现有小程序的 `p1`～`p8` 一致 |
| 购物车 | `GET /cart` | 购物车和配送费预估 |
| 购物车 | `POST /cart/items` | 加购 |
| 购物车 | `PATCH /cart/items/{id}` | 修改数量或选中状态 |
| 地址 | `GET/POST /addresses` | 地址列表或新增 |
| 地址 | `PUT/DELETE /addresses/{id}` | 编辑或删除 |
| 结算 | `POST /orders/preview` | 服务端重算订单金额 |
| 订单 | `POST /orders` | 创建订单并锁定库存 |
| 订单 | `GET /orders?status=WAIT_PAY` | 订单列表 |
| 订单 | `GET /orders/{orderNo}` | 用户查看自己的订单详情 |
| 管理订单 | `GET /admin/orders/{orderNo}` | 管理员查看任意订单详情 |
| 订单 | `POST /orders/{orderNo}/cancel` | 取消待支付订单 |
| 支付 | `POST /orders/{orderNo}/pay` | 创建支付流水并返回支付参数 |
| 支付 | `POST /payments/mock/callback` | 本地模拟支付结果回调 |
| 收货 | `POST /orders/{orderNo}/confirm` | 确认收货、发积分、升级会员 |
| 权益 | `GET /users/me/coupons` | 优惠券卡包 |
| 权益 | `GET /users/me/points` | 积分余额与明细 |
| 活动 | `POST /users/me/activities/egg/draw` | 砸金蛋 |
| 收藏 | `GET/POST/DELETE /users/me/favorites...` | 商品收藏 |
| 售后 | `GET/POST /after-sales` | 售后申请 |

## 商品列表参数

`GET /catalog/products` 支持：

- `category`：分类名称或分类 code
- `keyword`：商品名、简介、分类模糊搜索
- `sort`：`default`、`price_asc`、`price_desc`、`sales`
- `specialOnly`、`memberOnly`：布尔筛选
- `page`、`size`：从 0 开始分页，`size` 最大 100

## 加入购物车

普通加购：

```json
{"productId":"p1","quantity":1}
```

一分钱活动加购：

```json
{"productId":"p1","quantity":1,"activityCode":"penny"}
```

活动价格由服务端读取，客户端不能直接传入价格。

## 结算与下单

预结算和创建订单使用相同请求体：

```json
{
  "addressId": 1,
  "userCouponId": 1,
  "pointsToUse": 250,
  "note": "请放在门口"
}
```

只有购物车中 `selected=true` 的商品会被结算。服务端会重新验证商品状态、库存、会员价、活动价、优惠券、积分余额、配送距离和配送费。

## 模拟支付

创建支付：

```json
{"channel":"MOCK"}
```

得到 `paymentNo` 后模拟成功回调：

```json
{
  "paymentNo":"PAY...",
  "success":true,
  "providerTradeNo":"LOCAL-001",
  "callbackSecret":"local-mock-callback"
}
```

正式环境应禁用或隔离模拟渠道，并实现微信支付渠道的签名验证。

## 管理端接口

管理员 token 可访问 `/api/admin/**`：

- `GET /admin/dashboard`
- `GET/POST/PUT /admin/products...`
- `GET/PUT /admin/categories...`
- `GET/POST/PUT/DELETE /admin/home-modules...`：管理首页模块及快捷入口、活动卡片、配送提示
- `GET/PUT /admin/activities...`
- `GET /admin/orders`
- `GET /admin/order-events`：SSE 实时新订单事件
- `GET /admin/order-notifications`：待处理订单提醒列表
- `GET /admin/order-notifications/unread-count`：未读订单数量
- `PATCH /admin/order-notifications/{orderNo}/read|accept`：标记已读或接单
- `PATCH /admin/orders/{orderNo}/status`
- `POST /admin/orders/{orderNo}/refund`
- `GET/PUT /admin/delivery-config`
- `GET /admin/users`
- `PATCH /admin/users/{userId}/status`
- `GET/POST /admin/coupons`：查询或创建满减/无门槛优惠券
- `POST /admin/coupons/{couponCode}/issues`：按已注册手机号发放到用户券账户
- `GET/PATCH /admin/after-sales...`

所有关键管理操作都会写入 `admin_audit_logs`。
