# 鲜福铺 · 三公里鲜到家

根据《鲜福铺超市购物小程序开发需求文档》实现的微信原生小程序、Java 后端和 Vue 3 商家管理系统。三个子项目分别放在 `miniprogram`、`java` 和 `admin-web` 文件夹中。小程序已接入 Java REST API；Java 后端包含 Spring Boot、MySQL 接口和商家管理端 API，真实支付渠道需按商户资料接入。

## 项目目录

```text
MallShop/
├── miniprogram/   微信原生小程序
├── java/          Java 17 + MySQL 后端
├── admin-web/     Vue 3 + TypeScript 商家管理系统
└── README.md      项目总说明
```

## 已实现页面

- 首页：首页搜索、活动入口、专区卡片、配送公告、商品推荐和首次使用引导
- 分类选购：分类栏、关键词搜索、价格/销量排序、特价与会员价筛选、商品规格弹层
- 购物车：全选、批量删除、数量调整、实时金额与免配送费进度
- 确认订单：地址编辑、配送费计算、优惠券、积分抵扣、备注和模拟收款接口
- 订单中心：全部/待支付/待配送/已完成/已取消，支持模拟支付、配送进度、确认收货和再来一单
- 会员中心：会员等级、成长进度、会员权益、积分和月度券包
- 个人中心：订单快捷入口、优惠券、积分、地址、收藏、客服和设置入口
- 营销活动：新人福利、一分钱专享、9.9 特惠、净菜专区和砸金蛋演示

## 运行方式

1. 打开微信开发者工具。
2. 选择“导入项目”，目录选择 `MallShop/miniprogram`。
3. 使用测试号或将 `miniprogram/project.config.json` 中的 `appid` 替换为实际小程序 AppID。
4. 先启动 `java` 后端，再编译小程序。开发者工具默认请求 `http://127.0.0.1:8080/api`。

后端启动、数据库配置和接口方式见 [`java/README.md`](java/README.md) 与 [`java/docs/API.md`](java/docs/API.md)。商家后台的启动方式见 [`admin-web/README.md`](admin-web/README.md)。

## 商家管理系统

先启动 `java` 后端，再运行：

```bash
cd admin-web
pnpm install
pnpm dev
```

访问 `http://localhost:5173`，本地管理员默认口令为 `Admin@123456`。管理系统已包含经营看板、商品、分类、订单、用户、售后、首页装修、活动和配送设置页面。

## 业务规则

- 商品合计不足 30 元时，配送费为 3 元；满 30 元免配送费。
- 本地支付使用 Java 后端的 `MOCK` 支付渠道，不会产生真实扣款；正式上线需要接入微信支付渠道。
- 商品、购物车、订单、地址、优惠券和积分保存在 MySQL；只有接口地址、登录令牌和首次引导状态保存在微信本地缓存。

## 三端接口联调

小程序的统一请求层位于 `miniprogram/utils/request.js`，业务接口集中在 `miniprogram/utils/api.js`，接口地址配置位于 `miniprogram/utils/config.js`。开发者工具和后端运行在同一台电脑时默认可以直接联通；真机调试需要把接口地址改为电脑局域网 IP。具体方法见 [`miniprogram/README.md`](miniprogram/README.md)。
