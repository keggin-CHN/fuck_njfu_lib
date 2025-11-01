# 🚀 开始使用 - 图书馆实时流量和座位查询系统

> **这是什么？** 这是一个独立的Cloudflare版本，专注于实时流量监控和座位查询功能。

## ⭐ 推荐部署方式：Fork + 一键部署按钮

这是最简单、最快速的部署方式！

### 第一步：Fork 本项目（必须！）

1. 访问：https://github.com/keggin-CHN/fuck_njfu_lib
2. 点击右上角 **"Fork"** 按钮
3. 完成 Fork 到你的 GitHub 账号

### 第二步：一键部署

点击下方按钮部署（记得替换URL中的 `你的用户名`）：

[![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/你的用户名/fuck_njfu_lib/tree/main/cloudflare/worker)

### 第三步：部署前端

使用 Cloudflare Dashboard 连接你 Fork 的仓库部署 Pages。

**📖 详细教程**：
- [ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md) - 完整的一键部署指南
- [DEPLOY_GUIDE.md](./DEPLOY_GUIDE.md) - 图文部署教程

---

## 🖥️ 其他部署方式

### 本地脚本部署

```bash
cd cloudflare
./deploy.sh
```

或者（Windows/跨平台）：

```bash
cd cloudflare
npm install
node deploy.js
```

**脚本会自动完成所有步骤！** 包括登录、配置、部署。

---

## 📖 手动部署（详细步骤）

如果你想了解每一步，可以按照以下方式：

### 第1步：部署Worker后端

```bash
cd cloudflare/worker
npm install

# 设置账号密码（按提示输入）
wrangler secret put USERNAME      # 你的学号
wrangler secret put EDU_PASSWORD  # 统一认证密码
wrangler secret put LIB_PASSWORD  # 图书馆密码

# 部署
wrangler deploy
```

**复制显示的Worker URL！** 例如：`https://library-info-worker.xxx.workers.dev`

### 第2步：配置前端

编辑 `cloudflare/pages/assets/js/config.js`：

```javascript
const API_BASE_URL = 'https://你的Worker地址.workers.dev';
```

### 第3步：部署前端

```bash
cd cloudflare/pages
wrangler pages deploy . --project-name=library-info
```

**完成！** 访问显示的Pages URL。

## 📚 详细文档

| 文档 | 说明 |
|------|------|
| [INDEX.md](./INDEX.md) | 📖 项目总览和功能介绍 |
| [QUICKSTART.md](./QUICKSTART.md) | ⚡ 5分钟快速部署指南 |
| [README.md](./README.md) | 📘 完整的部署和使用文档 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 🏗️ 系统架构和技术细节 |

## 🆘 遇到问题？

### 常见问题速查

**Q: 无法获取数据？**
```bash
# 检查Worker日志
cd cloudflare/worker
wrangler tail
```

**Q: 前端无法连接Worker？**
- 检查 `pages/assets/js/config.js` 中的 API_BASE_URL
- 确保Worker已成功部署

**Q: 认证失败？**
```bash
# 重新设置密码
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD
wrangler deploy
```

## 🎯 功能概览

✅ **实时流量** - 查看在馆人数和占用率
✅ **座位查询** - 12个区域，今天/明天切换
✅ **详细信息** - 每个座位的预约情况
✅ **全球加速** - Cloudflare CDN加速
✅ **零成本** - 免费套餐完全够用

## 🔗 推荐阅读顺序

1. 先看 [INDEX.md](./INDEX.md) 了解项目
2. 按 [QUICKSTART.md](./QUICKSTART.md) 快速部署
3. 如有问题查 [README.md](./README.md)
4. 想深入了解看 [ARCHITECTURE.md](./ARCHITECTURE.md)

## 💡 提示

- 🔒 密码安全存储在Cloudflare Secrets中
- 🌍 全球任何地方都能快速访问
- 💰 个人使用完全免费
- ⚡ 部署后立即可用

---

**立即开始** → [QUICKSTART.md](./QUICKSTART.md)
