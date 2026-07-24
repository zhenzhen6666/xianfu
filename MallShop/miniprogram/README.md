# 鲜福铺微信小程序

小程序已接入 `../java` 后端，不再使用本地商品、购物车和订单模拟数据。

## 同一台电脑本地联调

先启动 Java 后端与 MySQL：

```bash
cd ../java
docker compose up --build
```

然后用微信开发者工具导入当前 `miniprogram` 目录。默认接口地址为：

```text
http://127.0.0.1:8080/api
```

`project.config.json` 已关闭本地开发域名校验。默认使用后端的 `/api/auth/dev-login` 完成本地登录，适合开发者工具联调。

## 修改接口地址

接口地址集中在 `utils/config.js`。无需修改源码，也可以在微信开发者工具控制台执行：

```javascript
wx.setStorageSync('xianfupu_api_base_url', 'http://127.0.0.1:8080/api')
```

真机不能使用 `127.0.0.1`，因为它代表手机本身。真机调试时改为电脑的局域网 IP：

```javascript
wx.setStorageSync('xianfupu_api_base_url', 'http://192.168.1.100:8080/api')
```

修改后重新编译小程序。手机和电脑需要连接同一个局域网，并确保系统防火墙允许访问 `8080` 端口。

## 切换真实微信登录

配置后端的 `WECHAT_APP_ID`、`WECHAT_APP_SECRET`，并把小程序 `project.config.json` 中的 `appid` 替换为实际 AppID。然后在开发者工具控制台执行：

```javascript
wx.setStorageSync('xianfupu_login_mode', 'wechat')
wx.removeStorageSync('xianfupu_access_token')
```

恢复本地开发登录：

```javascript
wx.setStorageSync('xianfupu_login_mode', 'dev')
wx.removeStorageSync('xianfupu_access_token')
```

## 已接入接口

- 首页、配送规则、分类、商品搜索和活动
- 本地开发登录/微信登录与 JWT 自动续登
- 服务端购物车、库存、活动价和全选状态
- 收货地址、优惠券、积分和服务端预结算
- 创建订单、模拟支付、取消、确认收货和再来一单
- 会员资料、优惠券、积分、收藏数量和售后申请
