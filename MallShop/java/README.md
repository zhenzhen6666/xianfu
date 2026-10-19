# 鲜福铺 Java 17 后端

这是与当前微信原生小程序及《鲜福铺超市购物小程序开发需求文档》配套的后端工程。技术栈为 Java 17、Spring Boot 4.1、Spring JDBC、Spring Security、Flyway 和 MySQL 8.4。

依赖已按 Java 17 兼容性升级至 2026-07-23 的稳定版本：Spring Boot `4.1.0` 统一管理 Spring Framework `7.0.8+`、Spring Security `7.1.0`、Flyway `12.4.0`、MySQL Connector/J `9.7.0` 和 H2 `2.4.240`；Maven Wrapper 与 Docker 构建镜像已升级到 Maven `3.9.16`。不要单独覆盖 BOM 内的组件版本，以免破坏 Spring Boot 的兼容性组合。

## 已实现能力

- 微信 `code2session` 登录、JWT 鉴权、本地开发登录和独立管理员权限
- 首页装修数据、配送规则、分类、搜索、价格/销量排序、活动专区和商品详情
- 服务端购物车、会员价、一分钱活动价、库存与限购校验
- 收货地址、3 公里配送范围校验、满 30 元免 3 元配送费
- 服务端预结算、优惠券、100 积分抵 1 元、订单快照和并发库存扣减
- 15 分钟待支付订单自动取消，自动返还库存、优惠券和积分
- 可插拔支付渠道接口、模拟支付、幂等回调和退款入口
- 确认收货赠送积分、累计消费自动升级会员等级
- 优惠券、积分明细、收藏、欢乐砸金蛋和售后申请
- 搜索历史、热门关键词、无结果关键词统计和核心经营数据 CSV 导出
- 管理端商品、订单、配送规则、用户状态、售后、退款和数据看板接口
- Flyway 自动建表及与小程序一致的 8 件商品、分类、活动、会员和首页种子数据

## 快速启动

最省事的方式是使用 Docker：

```bash
cd java
docker compose up --build
```

服务健康检查：

```bash
curl http://localhost:8080/actuator/health
```

也可以只启动 MySQL，再使用本机 Java 17 运行：

```bash
docker compose up -d mysql
./mvnw spring-boot:run
```

如果 IDE 启动后接口报 `Name for argument ... parameter name information not available`，请重新加载 Maven 工程，清理旧编译产物后再启动。项目默认启用 `-parameters`，接口的查询参数和路径参数也显式声明了名称，可兼容未保留参数名的编译环境。可在 `java` 目录执行 `./mvnw clean spring-boot:run` 重新编译并运行。

首次启动时 Flyway 会自动执行 `src/main/resources/db/migration` 下的脚本。默认本地连接参数为：

- 数据库：`xianfupu`
- 用户名：`xianfupu`
- 密码：`xianfupu123`
- 服务端口：`8080`

生产环境必须通过环境变量修改数据库密码、`JWT_SECRET`、`DEV_ADMIN_SECRET`，并设置 `DEV_LOGIN_ENABLED=false`。完整变量见 `.env.example`。

## 本地联调账号

本地开发用户登录：

```bash
curl -X POST http://localhost:8080/api/auth/dev-login \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"林女士","mobile":"13866686688"}'
```

本地管理员登录：

```bash
curl -X POST http://localhost:8080/api/auth/dev-admin-login \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"鲜福铺管理员","secret":"Admin@123456"}'
```

响应中的 `accessToken` 通过请求头传递：

```text
Authorization: Bearer <accessToken>
```

## 微信小程序配置

配置 `WECHAT_APP_ID` 和 `WECHAT_APP_SECRET` 后，调用 `POST /api/auth/wechat-login`，请求体为：

```json
{"code":"wx.login 返回的 code","nickname":"用户昵称","avatarUrl":"头像地址"}
```

开发阶段需要在微信开发者工具中配置合法请求域名；本地调试可暂时关闭域名校验。前端接入字段与接口示例见 [docs/API.md](docs/API.md)。

## 构建与测试

```bash
./mvnw clean test
./mvnw clean package
```

测试使用 H2 的 MySQL 兼容模式，不污染本地 MySQL。生产运行仍使用 MySQL。

## 目录结构

```text
src/main/java/com/xianfupu/shop
├── admin       商家管理接口
├── auth        登录与用户身份
├── cart        购物车
├── catalog     商品、首页、活动、配送公开数据
├── common      统一业务异常
├── order       订单、库存、积分结算、超时任务
├── payment     支付渠道抽象、模拟支付与退款
├── security    JWT 与接口权限
└── user        地址、优惠券、积分、收藏和售后
```

支付扩展只需新增一个 `PaymentProvider` 实现（如微信支付），订单代码无需修改。具体约定见 [docs/PAYMENT.md](docs/PAYMENT.md)。

配套 Vue 3 商家后台位于 [`../admin-web`](../admin-web)，可直接调用本项目的管理员接口。
