# 🚀 一键部署指南

本文档提供多种一键部署方式，选择最适合你的方式。

## 方式一：本地一键部署脚本 ⭐ 推荐

### Bash脚本（Linux/macOS）

```bash
cd cloudflare
chmod +x deploy.sh
./deploy.sh
```

这个脚本会自动：
1. ✅ 检查所需工具（Node.js、npm、Wrangler）
2. ✅ 登录Cloudflare账号
3. ✅ 设置环境变量（账号密码）
4. ✅ 部署Worker后端
5. ✅ 配置并部署Pages前端
6. ✅ 显示部署结果

### Node.js脚本（跨平台）

```bash
cd cloudflare
npm install  # 安装依赖（首次运行）
node deploy.js
```

或者使用npm命令：

```bash
cd cloudflare
npm run deploy
```

**特点**：
- 🎨 彩色输出，界面友好
- 🔐 安全的密码输入（不显示）
- ⚡ 全自动流程
- 💡 智能错误提示

## 方式二：GitHub Actions 自动部署 🤖

适合将代码托管在GitHub的用户。

### 1. 设置GitHub Secrets

在你的GitHub仓库中，进入 `Settings` > `Secrets and variables` > `Actions`，添加以下Secrets：

| Secret名称 | 说明 | 获取方式 |
|-----------|------|---------|
| `CLOUDFLARE_API_TOKEN` | Cloudflare API令牌 | [获取教程](#获取cloudflare-api-token) |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare账户ID | [获取教程](#获取cloudflare-account-id) |
| `CLOUDFLARE_ACCOUNT_SUBDOMAIN` | Worker子域名 | [获取教程](#获取worker子域名) |
| `USERNAME` | 图书馆账号 | 你的学号 |
| `EDU_PASSWORD` | 统一认证密码 | 你的密码 |
| `LIB_PASSWORD` | 图书馆密码 | 你的密码 |

### 2. 复制工作流文件

将 `.github/workflows/deploy.yml` 复制到你的仓库根目录：

```bash
mkdir -p .github/workflows
cp cloudflare/.github/workflows/deploy.yml .github/workflows/
```

### 3. 推送代码触发部署

```bash
git add .
git commit -m "Add Cloudflare deployment"
git push
```

每次推送到main/master分支时，会自动部署！

### 4. 手动触发部署

在GitHub仓库页面：
1. 点击 `Actions` 标签
2. 选择 `Deploy to Cloudflare` 工作流
3. 点击 `Run workflow` 按钮
4. 选择分支并运行

## 方式三：Deploy to Cloudflare 按钮 🔘

### 使用官方部署按钮

点击下方按钮一键部署到Cloudflare：

[![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/your-username/your-repo/tree/main/cloudflare)

**注意**：需要替换URL中的 `your-username` 和 `your-repo`

### 添加到你的README

在README.md中添加：

```markdown
[![Deploy to Cloudflare](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/your-username/your-repo/tree/main/cloudflare)
```

## 方式四：Cloudflare Dashboard 手动部署 🖱️

### Worker部署

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
2. 进入 `Workers & Pages`
3. 点击 `Create application` > `Create Worker`
4. 复制 `worker/src/index.js` 的内容到编辑器
5. 添加其他文件（auth.js, traffic.js, seats.js, utils.js）
6. 在 `Settings` > `Variables` 中添加环境变量
7. 点击 `Deploy`

### Pages部署

1. 在Dashboard进入 `Workers & Pages`
2. 点击 `Create application` > `Pages` > `Upload assets`
3. 上传 `pages/` 目录的所有文件
4. 项目名称：`library-info`
5. 点击 `Deploy`

## 获取Cloudflare凭证

### 获取Cloudflare API Token

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
2. 点击右上角头像 > `My Profile`
3. 选择 `API Tokens` 标签
4. 点击 `Create Token`
5. 使用 `Edit Cloudflare Workers` 模板
6. 权限设置：
   - Account Resources: All accounts
   - Zone Resources: All zones
7. 点击 `Continue to summary` > `Create Token`
8. **复制并保存Token（只显示一次）**

### 获取Cloudflare Account ID

方法一：从Dashboard获取
1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
2. 选择任意一个域名
3. 在右侧栏中找到 `Account ID`
4. 点击复制

方法二：从Wrangler获取
```bash
wrangler whoami
```
查看输出中的 `Account ID`

### 获取Worker子域名

方法一：从Dashboard获取
1. 进入 `Workers & Pages`
2. 查看任意Worker的URL
3. 格式：`https://xxx.your-subdomain.workers.dev`
4. `your-subdomain` 就是你的子域名

方法二：从Wrangler获取
```bash
wrangler whoami
```
查看输出中的 `subdomain`

## 部署后验证

### 检查Worker

```bash
curl https://library-info-worker.your-subdomain.workers.dev/api/health
```

预期输出：
```json
{
  "success": true,
  "message": "服务正常运行",
  "timestamp": 1234567890
}
```

### 检查Pages

访问：`https://library-info.pages.dev`

应该能看到首页，并且可以正常查询流量和座位信息。

## 更新部署

### 更新Worker

```bash
cd cloudflare/worker
wrangler deploy
```

### 更新Pages

```bash
cd cloudflare/pages
wrangler pages deploy .
```

### 使用脚本更新

```bash
cd cloudflare
./deploy.sh  # 或 node deploy.js
```

## 常见问题

### Q: 脚本提示权限错误？

**Linux/macOS:**
```bash
chmod +x deploy.sh
./deploy.sh
```

**Windows:**
使用Node.js脚本：
```bash
node deploy.js
```

### Q: 无法登录Cloudflare？

确保：
1. 浏览器已安装
2. 网络连接正常
3. 允许打开浏览器窗口

手动登录：
```bash
wrangler login
```

### Q: 部署失败？

查看详细日志：
```bash
wrangler deploy --verbose
```

或查看实时日志：
```bash
wrangler tail
```

### Q: 环境变量设置失败？

手动设置：
```bash
cd worker
echo "your-username" | wrangler secret put USERNAME
echo "your-edu-password" | wrangler secret put EDU_PASSWORD
echo "your-lib-password" | wrangler secret put LIB_PASSWORD
```

### Q: GitHub Actions部署失败？

检查：
1. 所有Secrets都已正确设置
2. API Token权限正确
3. 查看Actions日志获取详细错误

### Q: Worker URL自动获取失败？

手动指定Worker URL：
```bash
# 编辑 pages/assets/js/config.js
const API_BASE_URL = 'https://your-actual-worker-url.workers.dev';
```

## 推荐使用方式

| 场景 | 推荐方式 | 原因 |
|------|---------|------|
| 首次部署 | Bash/Node脚本 | 简单快速，交互友好 |
| 持续部署 | GitHub Actions | 自动化，无需手动操作 |
| 快速体验 | Deploy按钮 | 一键完成 |
| 调试问题 | 手动部署 | 完全控制每个步骤 |

## 性能优化建议

部署完成后，建议：

1. **自定义域名**：提升专业性
2. **启用缓存**：加快访问速度
3. **配置告警**：监控服务状态
4. **定期更新**：保持最新功能

## 安全提示

⚠️ **重要**：
- 不要在代码中包含密码
- 使用环境变量或Secrets
- 定期更换API Token
- 限制Token权限范围

## 获取帮助

- 📖 [完整文档](./README.md)
- 🏗️ [架构说明](./ARCHITECTURE.md)
- 📋 [部署清单](./DEPLOYMENT_CHECKLIST.md)
- 🐛 [问题反馈](https://github.com/your-repo/issues)

---

**开始部署** → 选择上面任一方式立即开始！
