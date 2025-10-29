# 图书馆预约系统（Go + Vue 重构版）

本项目将原有的 Flask 单体应用完整重构为 **Go + Vue** 的前后端分离架构，覆盖账号注册、自动抢座、预约历史、馆内人数监控、管理员中心等全部功能。

## 项目结构

```
/ backend   # Go 语言后端服务（Gin + Gorm + Cron）
/ frontend  # Vue 3 前端应用（Vite + Pinia + Vue Router）
```

### 后端（`backend`）

- Gin 提供 RESTful API
- Gorm 使用 SQLite 存储数据
- JWT 进行用户认证
- AES-256-GCM 对统一认证/图书馆密码进行加密存储
- Cron 任务实现自动抢座、迟到保护、馆内人数采集
- 模块划分：
  - `cmd/server`：服务入口
  - `internal/config`：配置加载
  - `internal/handlers`：业务接口
  - `internal/middleware`：鉴权、日志
  - `internal/models`：数据库模型
  - `internal/scheduler`：任务调度
  - `internal/utils`：安全、工具函数

### 前端（`frontend`）

- Vue 3 组合式 API + Vite 构建
- Pinia 管理登录态、用户信息
- Axios 封装 API 调用
- 路由划分：登录 / 注册 / 仪表盘 / 预约管理 / 自动设置 / 馆内人数 / 管理员中心

## 本地运行

### 前端开发

```bash
cd frontend
npm install
npm run dev
```
默认开启 <http://localhost:5173>，已配置代理转发 `/api` 到后台服务。

### 后端开发

```bash
cd backend
go mod tidy
go run ./cmd/server
```
默认监听 `:8080`。

## 部署

项目提供一键部署脚本 `deploy.sh`（详见下文），支持：

- 自动安装 Go 1.24、Node.js 18、pm2、systemd 等依赖
- 编译前端并拷贝到后端的静态目录
- 构建后端可执行文件与 systemd 服务

卸载可使用 `uninstall.sh`。

## 环境变量

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `APP_PORT` | 后端监听端口 | `8080` |
| `DATABASE_PATH` | SQLite 路径 | `data/app.db` |
| `JWT_SECRET` | JWT 签名密钥 | `change-me-please` |
| `ENCRYPTION_SECRET` | 32 字节加密密钥 | `0123456789abcdef0123456789abcdef` |
| `TIMEZONE` | 时区 | `Asia/Shanghai` |

## License

MIT
