# 🚀 Fork + 一键部署速查指南

## 为什么要 Fork？⚠️

**必须先 Fork 的3个原因：**

1. ✅ **权限要求** - Cloudflare Deploy 按钮需要访问你的仓库
2. ✅ **配置修改** - 你需要修改 API URL 等配置文件
3. ✅ **独立管理** - 拥有自己的副本，可以自由更新和定制

---

## 快速部署三步走 🎯

### 步骤 1️⃣：Fork 本项目

```
访问：https://github.com/keggin-CHN/fuck_njfu_lib
点击：右上角的 "Fork" 按钮
等待：Fork 完成
结果：https://github.com/你的用户名/fuck_njfu_lib
```

### 步骤 2️⃣：部署 Worker（后端）

**方法 A：使用 Deploy 按钮**

1. 将下面 URL 中的 `你的用户名` 替换为你的 GitHub 用户名
2. 在浏览器中访问这个 URL：

```
https://deploy.workers.cloudflare.com/?url=https://github.com/你的用户名/fuck_njfu_lib/tree/main/cloudflare/worker
```

3. 登录 Cloudflare 并完成部署
4. **保存 Worker URL**（例如：`https://library-info-worker.xxx.workers.dev`）

**方法 B：使用命令行**

```bash
cd cloudflare/worker
wrangler login
wrangler deploy
```

**设置环境变量（必须）：**

```bash
cd cloudflare/worker
wrangler secret put USERNAME       # 输入你的学号
wrangler secret put EDU_PASSWORD   # 输入统一认证密码
wrangler secret put LIB_PASSWORD   # 输入图书馆密码
```

### 步骤 3️⃣：部署 Pages（前端）

#### 3.1 配置 API 地址

在你 Fork 的仓库中，编辑文件：`cloudflare/pages/assets/js/config.js`

```javascript
// 修改这一行，替换为你的 Worker URL
const API_BASE_URL = 'https://library-info-worker.xxx.workers.dev';
```

提交更改到 GitHub。

#### 3.2 部署 Pages

**方法 A：Cloudflare Dashboard（推荐）**

1. 访问：https://dash.cloudflare.com/
2. 点击：**Workers & Pages** → **Create application** → **Pages** → **Connect to Git**
3. 选择：你 Fork 的仓库 `fuck_njfu_lib`
4. 配置：
   - 项目名称：`library-info`
   - 构建命令：留空
   - 构建输出目录：`cloudflare/pages`
5. 点击：**Save and Deploy**

**方法 B：命令行**

```bash
cd cloudflare/pages
wrangler pages deploy . --project-name=library-info
```

---

## 部署成功标志 ✅

- [ ] Worker URL 可访问：`https://library-info-worker.xxx.workers.dev/api/health`
- [ ] Pages URL 可访问：`https://library-info.pages.dev`
- [ ] 实时流量功能正常
- [ ] 座位查询功能正常

---

## 常见错误速查 🐛

| 错误 | 原因 | 解决方法 |
|------|------|---------|
| 找不到 wrangler.toml | URL 路径错误 | 确保 URL 指向 `/cloudflare/worker` |
| 401 认证失败 | 环境变量未设置 | 运行 `wrangler secret put` 命令 |
| API 请求失败 | API URL 未配置 | 检查 `config.js` 中的 `API_BASE_URL` |
| Pages 部署失败 | 构建配置错误 | 输出目录设为 `cloudflare/pages` |

---

## 完整文档链接 📚

- 📖 [ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md) - 所有部署方式详细说明
- 📸 [DEPLOY_GUIDE.md](./DEPLOY_GUIDE.md) - 图文部署教程 + 故障排查
- 📘 [README.md](./README.md) - 完整功能说明和 API 文档
- 🏗️ [ARCHITECTURE.md](./ARCHITECTURE.md) - 系统架构说明

---

## 快速命令参考 ⚡

```bash
# 安装 Wrangler CLI
npm install -g wrangler

# 登录 Cloudflare
wrangler login

# 部署 Worker
cd cloudflare/worker
wrangler deploy

# 设置环境变量
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD

# 部署 Pages
cd cloudflare/pages
wrangler pages deploy . --project-name=library-info

# 查看 Worker 日志
cd cloudflare/worker
wrangler tail

# 查看账户信息
wrangler whoami
```

---

## 架构说明 📊

```
用户浏览器
    ↓
Cloudflare Pages (前端)
    ├─ HTML/CSS/JavaScript
    ├─ 全球 CDN 加速
    └─ 静态资源托管
    ↓
Cloudflare Workers (后端)
    ├─ 认证模块
    ├─ 流量查询 API
    ├─ 座位查询 API
    └─ 环境变量（账号密码）
    ↓
南林图书馆系统
```

**为什么这样设计？**
- ✅ Pages 托管前端：免费无限流量，全球 CDN
- ✅ Workers 处理后端：边缘计算，低延迟
- ✅ 分离部署：安全性更好，密码不暴露在前端

---

## 获取帮助 🆘

1. 📖 查看完整文档：[ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md)
2. 🔍 搜索问题：[GitHub Issues](https://github.com/keggin-CHN/fuck_njfu_lib/issues)
3. 💬 提问：[新建 Issue](https://github.com/keggin-CHN/fuck_njfu_lib/issues/new)

---

## 小贴士 💡

- 🔐 环境变量以加密形式存储，安全可靠
- 🌍 全球任何地方访问速度都很快
- 💰 个人使用完全免费（Cloudflare 免费套餐）
- ⚡ 部署后几分钟内即可使用
- 🔄 更新代码只需 `git push`（如果使用 Git 集成）

---

**立即开始部署** → [ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md) 💪
