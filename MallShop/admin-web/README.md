# 鲜福铺 Vue 3 管理系统

独立的商家运营后台，对接 `../java` 项目提供的 `/api/auth/**` 和 `/api/admin/**` 接口。技术栈为 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios 和 ECharts。

## 已实现功能

- 管理员口令登录与 JWT 登录态
- 今日订单、销售额、新增会员、热销商品和库存提醒看板
- 商品新增、编辑、上下架、价格及库存管理
- 商品图片和首页活动卡片图片支持从本地电脑上传、预览、替换和移除（JPG/PNG/WebP/GIF，单张不超过 5MB）
- 商品分类维护
- 订单状态流转、取消订单和退款
- 会员搜索、积分与消费额查看、账号停用/恢复
- 售后筛选、审核状态和商家回复
- 小程序首页组件及营销活动配置
- 配送半径、免配送费门槛、基础运费、时效和公告配置
- 桌面端及移动端自适应布局

## 本地运行

先启动 Java 后端和 MySQL：

```bash
cd ../java
docker compose up --build
```

再启动管理系统：

```bash
cd ../admin-web
pnpm install
pnpm dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 代理到 `http://127.0.0.1:8080`。

本地管理员默认信息：

- 管理员名称：可自定义，例如 `鲜福铺管理员`
- 管理员口令：`Admin@123456`

生产环境必须在 Java 后端修改 `DEV_ADMIN_SECRET`，并根据部署方式设置 `VITE_API_BASE_URL`。如果前后端同域且网关已把 `/api` 转发到 Java 服务，则保留默认值 `/api` 即可。

## 构建

当前 Vite 8 要求 Node.js `20.19+` 或 `22.12+`：

```bash
pnpm typecheck
pnpm build
pnpm preview
```

生产文件输出到 `dist` 目录。

## 图片上传

商品编辑和首页活动卡片编辑中，点击“从本地上传”选择电脑上的图片。上传完成后点击“保存商品”或“保存并发布”生效；替换上传失败会保留原图。

图片通过管理员接口 `POST /api/admin/uploads/images` 上传，后端返回 `/api/files/…` 路径并将文件存入 `UPLOAD_DIR`（默认 `./uploads`，相对于后端启动目录）。Docker Compose 使用 `xianfupu_uploads` 数据卷持久保存图片，备份或迁移时需同时保留该数据卷。已有图片引用仍可显示。
