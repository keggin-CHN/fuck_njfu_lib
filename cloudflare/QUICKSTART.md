# 快速开始指南

本文档提供最简化的部署步骤，帮助你快速上手。

## ⭐ 推荐方式：Fork + 一键部署

**最简单的部署方式！** 详见：[FORK_DEPLOY_GUIDE.md](./FORK_DEPLOY_GUIDE.md)

1. Fork 本项目：https://github.com/keggin-CHN/fuck_njfu_lib
2. 点击 Deploy 按钮一键部署 Worker
3. 使用 Cloudflare Dashboard 部署 Pages

**完整教程**：[ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md)

---

## 前置要求

- Cloudflare账号（免费注册）
- Node.js 16+ 和 npm
- 图书馆账号和密码
- **已 Fork 本项目**（如需使用一键部署）

## 5分钟快速部署（命令行方式）

### 第0步：Fork 项目（推荐）

如果你打算使用 GitHub + Cloudflare 集成部署，先 Fork 项目：
1. 访问：https://github.com/keggin-CHN/fuck_njfu_lib
2. 点击右上角 **"Fork"** 按钮

### 第1步：安装Wrangler

```bash
npm install -g wrangler
wrangler login
```

### 第2步：部署Worker

```bash
cd cloudflare/worker
npm install

# 设置环境变量（按提示输入账号密码）
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD

# 部署
wrangler deploy
```

记下显示的Worker URL，例如：`https://library-info-worker.xxx.workers.dev`

### 第3步：配置并部署Pages

```bash
cd ../pages

# 修改API配置
# 编辑 assets/js/config.js，将 API_BASE_URL 改为你的Worker URL
```

使用编辑器打开 `assets/js/config.js`，修改：
```javascript
const API_BASE_URL = 'https://library-info-worker.xxx.workers.dev';
```

```bash
# 部署Pages
wrangler pages deploy . --project-name=library-info
```

### 第4步：访问网站

部署完成后，访问显示的Pages URL，例如：
```
https://library-info.pages.dev
```

## 完成！

现在你可以：
- 查看实时流量
- 查询座位信息
- 查看座位详情

## 后续步骤

1. **自定义域名**：在Cloudflare Dashboard中为Pages配置自定义域名
2. **监控使用**：在Dashboard中查看Worker的请求统计
3. **更新代码**：修改后重新运行 `wrangler deploy`

## 常见问题

**Q: Worker部署失败？**
A: 确保已运行 `wrangler login` 并且网络正常

**Q: 无法获取数据？**
A: 检查环境变量是否正确设置，可以运行 `wrangler tail` 查看日志

**Q: 如何更新？**
A: 在对应目录运行 `wrangler deploy` 即可

## 需要帮助？

查看完整文档：[README.md](./README.md)
