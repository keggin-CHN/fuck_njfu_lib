# 📚 图书馆实时流量和座位查询系统 - Cloudflare 版

> 基于 Cloudflare Workers 和 Pages 的无服务器图书馆信息查询系统

[![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 🎯 项目简介

这是一个基于 Cloudflare 边缘计算平台的图书馆查询系统，提供实时流量监控和座位占用查询功能。

### 核心优势

- ✅ **零成本运营** - Cloudflare 免费套餐完全够用
- ✅ **全球加速** - CDN 边缘节点，低延迟访问
- ✅ **无需服务器** - Serverless 架构，无需维护
- ✅ **安全可靠** - 密码加密存储，HTTPS 传输
- ✅ **一键部署** - 3 分钟完成部署

---

## 🚀 快速开始（推荐方式）

### 方式三：Fork + 一键部署 ⭐ 

**最简单、最快速的部署方式！**

#### 步骤 1：Fork 本项目

1. 访问：https://github.com/keggin-CHN/fuck_njfu_lib
2. 点击右上角 **"Fork"** 按钮
3. 完成 Fork 到你的 GitHub 账号

#### 步骤 2：部署 Worker 后端

点击下方按钮（记得替换 `你的用户名`）：

[![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/你的用户名/fuck_njfu_lib/tree/main/cloudflare/worker)

设置环境变量：
```bash
cd cloudflare/worker
wrangler secret put USERNAME       # 你的学号
wrangler secret put EDU_PASSWORD   # 统一认证密码
wrangler secret put LIB_PASSWORD   # 图书馆密码
```

#### 步骤 3：部署 Pages 前端

1. 配置 API 地址（编辑 `cloudflare/pages/assets/js/config.js`）
2. 登录 Cloudflare Dashboard
3. 创建 Pages 项目并连接你 Fork 的仓库
4. 配置构建设置（输出目录：`cloudflare/pages`）
5. 部署完成

**📖 详细教程**：
- [FORK_DEPLOY_GUIDE.md](./FORK_DEPLOY_GUIDE.md) - 快速参考卡片
- [ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md) - 完整部署指南
- [DEPLOY_GUIDE.md](./DEPLOY_GUIDE.md) - 图文教程 + 故障排查

---

## 📋 其他部署方式

### 方式一：GitHub Actions 自动部署

适合持续更新。Fork 后配置 GitHub Secrets，每次推送自动部署。

[查看教程](./ONE_CLICK_DEPLOY.md#方式一github-actions-自动部署) →

### 方式二：本地脚本部署

```bash
cd cloudflare
./deploy.sh          # Linux/macOS
# 或
node deploy.js       # 跨平台
```

[查看教程](./ONE_CLICK_DEPLOY.md#方式二本地脚本部署) →

### 方式四：完全手动部署

完全控制每个步骤，适合学习和自定义。

[查看教程](./ONE_CLICK_DEPLOY.md#方式四完全手动部署源码部署) →

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────┐
│          用户浏览器                   │
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│    Cloudflare Pages (前端)           │
│    - HTML/CSS/JavaScript             │
│    - 全球 CDN 加速                    │
└──────────────┬──────────────────────┘
               │
               ↓ API 请求
┌─────────────────────────────────────┐
│    Cloudflare Workers (后端)         │
│    - 认证模块                         │
│    - 流量查询 API                     │
│    - 座位查询 API                     │
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│       南京林业大学图书馆系统           │
└─────────────────────────────────────┘
```

**为什么这样设计？**
- Pages 托管前端：免费无限流量，全球 CDN
- Workers 处理后端：边缘计算，支持动态逻辑
- 密码存储在 Workers：安全性更好

---

## ✨ 功能特性

### 实时流量监控

- 📊 查看图书馆当前在馆人数
- 📈 显示总容量和剩余座位
- 🎨 可视化占用率展示
- 🔄 实时数据刷新

### 座位查询

- 🏢 12个区域，覆盖2-7层
- 🗂️ 按楼层筛选查看
- 📅 今天/明天数据切换
- 🔍 详细座位预约信息

---

## 📁 项目结构

```
cloudflare/
├── worker/                  # Cloudflare Worker (后端)
│   ├── src/
│   │   ├── index.js        # 主入口
│   │   ├── auth.js         # 认证模块
│   │   ├── traffic.js      # 流量监控
│   │   ├── seats.js        # 座位查询
│   │   └── utils.js        # 工具函数
│   ├── wrangler.toml       # Worker 配置
│   └── package.json
│
├── pages/                   # Cloudflare Pages (前端)
│   ├── index.html          # 首页
│   ├── traffic.html        # 流量监控页面
│   ├── seats.html          # 座位查询页面
│   └── assets/
│       ├── css/
│       └── js/
│           ├── config.js   # API 配置
│           ├── traffic.js  # 流量页面脚本
│           └── seats.js    # 座位页面脚本
│
└── 文档/
    ├── README.md                  # 本文档（英文版）
    ├── README_CN.md               # 本文档（中文版）
    ├── FORK_DEPLOY_GUIDE.md       # Fork + 一键部署速查
    ├── ONE_CLICK_DEPLOY.md        # 所有部署方式详解
    ├── DEPLOY_GUIDE.md            # 图文教程 + 故障排查
    ├── QUICKSTART.md              # 5分钟快速开始
    ├── ARCHITECTURE.md            # 系统架构说明
    └── INDEX.md                   # 项目总览
```

---

## 📖 文档导航

### 🚀 快速开始
- ⭐ [FORK_DEPLOY_GUIDE.md](./FORK_DEPLOY_GUIDE.md) - Fork + 一键部署速查卡片
- 📖 [ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md) - 所有部署方式详细说明
- 📸 [DEPLOY_GUIDE.md](./DEPLOY_GUIDE.md) - 图文部署教程 + 完整故障排查

### 📚 基础文档
- ⚡ [QUICKSTART.md](./QUICKSTART.md) - 5分钟快速部署
- 📘 [README.md](./README.md) - 完整功能说明和 API 文档
- 📋 [INDEX.md](./INDEX.md) - 项目总览和文档索引

### 🏗️ 进阶文档
- 🔧 [ARCHITECTURE.md](./ARCHITECTURE.md) - 系统架构和技术细节
- 📊 [USAGE_GUIDE.md](./USAGE_GUIDE.md) - 使用指南
- 🔄 [CHANGELOG.md](./CHANGELOG.md) - 更新日志

---

## 🔐 安全性

- ✅ 密码以加密形式存储在 Cloudflare Secrets
- ✅ 全程 HTTPS 加密传输
- ✅ 无数据库，无数据泄露风险
- ✅ 不收集用户数据
- ✅ 认证信息定期自动清除

---

## 💰 成本说明

**免费套餐完全够用！**

| 服务 | 免费额度 | 超出收费 |
|------|---------|---------|
| Workers | 100,000 请求/天 | $0.50/百万请求 |
| Pages | 无限请求 | 免费 |
| CDN | 无限流量 | 免费 |

个人使用基本不会超出免费额度。

---

## ⚙️ API 接口

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/traffic` | GET | 获取实时流量 |
| `/api/seats/summary` | GET | 获取座位摘要 |
| `/api/seats/detail` | GET | 获取座位详情 |
| `/api/areas` | GET | 获取区域列表 |

详细 API 文档见：[README.md - API接口说明](./README.md#api接口说明)

---

## 🐛 故障排查

### 常见问题

| 问题 | 解决方法 |
|------|---------|
| 找不到 wrangler.toml | 确保 URL 指向 `/cloudflare/worker` |
| 401 认证失败 | 运行 `wrangler secret put` 设置环境变量 |
| API 请求失败 | 检查 `config.js` 中的 API_BASE_URL |
| Pages 构建失败 | 输出目录设为 `cloudflare/pages` |

**详细排查指南**：[DEPLOY_GUIDE.md - 故障排查指南](./DEPLOY_GUIDE.md#故障排查指南)

---

## 🔄 更新部署

### 更新 Worker
```bash
cd cloudflare/worker
wrangler deploy
```

### 更新 Pages
```bash
cd cloudflare/pages
wrangler pages deploy .
```

或者直接推送到 GitHub（如果使用 Git 集成）：
```bash
git add .
git commit -m "Update"
git push
```

---

## 🆘 获取帮助

1. 📖 查看文档：
   - [快速部署指南](./FORK_DEPLOY_GUIDE.md)
   - [完整教程](./ONE_CLICK_DEPLOY.md)
   - [故障排查](./DEPLOY_GUIDE.md)

2. 🔍 搜索问题：
   - [GitHub Issues](https://github.com/keggin-CHN/fuck_njfu_lib/issues)

3. 💬 提问：
   - [新建 Issue](https://github.com/keggin-CHN/fuck_njfu_lib/issues/new)

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

MIT License

---

## 🙏 致谢

- Cloudflare Workers & Pages 平台
- Bootstrap UI 框架
- 南京林业大学图书馆系统

---

## 🔗 相关链接

- [原始项目](../) - 完整的图书馆预约系统
- [Cloudflare Workers 文档](https://developers.cloudflare.com/workers/)
- [Cloudflare Pages 文档](https://developers.cloudflare.com/pages/)

---

## 💡 小贴士

- 🔐 环境变量以加密形式存储，安全可靠
- 🌍 全球任何地方访问速度都很快
- 💰 个人使用完全免费
- ⚡ 部署后几分钟内即可使用
- 🔄 更新代码只需 `git push`

---

**立即开始部署** → [FORK_DEPLOY_GUIDE.md](./FORK_DEPLOY_GUIDE.md) 🚀
