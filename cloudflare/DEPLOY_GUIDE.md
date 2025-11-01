# 📖 图文部署指南

这是一个详细的图文部署教程，帮助你快速完成部署。

---

## 🎯 部署架构说明

本项目采用 Cloudflare 的边缘计算架构：

```
┌─────────────────────────────────────────────────┐
│                   用户浏览器                      │
└────────────────┬────────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────────┐
│          Cloudflare Pages (前端)                 │
│  - 静态 HTML/CSS/JavaScript                      │
│  - 全球 CDN 加速                                  │
│  - 自动 HTTPS                                    │
└────────────────┬────────────────────────────────┘
                 │
                 ↓ API 请求
┌─────────────────────────────────────────────────┐
│         Cloudflare Workers (后端)                │
│  - 认证模块 (auth.js)                            │
│  - 流量查询 (traffic.js)                         │
│  - 座位查询 (seats.js)                           │
│  - 边缘计算，全球低延迟                           │
└────────────────┬────────────────────────────────┘
                 │
                 ↓ 数据请求
┌─────────────────────────────────────────────────┐
│            南京林业大学图书馆系统                  │
│  - 统一认证系统                                   │
│  - 图书馆预约系统                                 │
└─────────────────────────────────────────────────┘
```

**为什么是前端 Pages + 后端 Workers？**

- ✅ **Pages 适合前端**：静态资源托管，全球 CDN 加速，免费无限流量
- ✅ **Workers 适合后端**：边缘计算，支持动态逻辑，可以调用外部 API
- ✅ **安全性更好**：凭证存储在 Workers 环境变量中，前端无法访问
- ✅ **性能更优**：边缘节点处理，延迟更低

---

## 🚀 快速开始：方式三详细教程

### 为什么要 Fork？

**必须先 Fork 的原因：**

1. **部署需要权限**：Deploy to Cloudflare 需要访问你的仓库
2. **自定义配置**：你需要修改配置文件（API URL）
3. **持续更新**：Fork 后可以随时更新和自定义功能
4. **多用户部署**：每个人都应该有自己的独立副本

---

### 第一步：Fork 本项目（必须！）

#### 1. 访问项目主页

在浏览器中打开：https://github.com/keggin-CHN/fuck_njfu_lib

#### 2. 点击 Fork 按钮

在页面右上角找到 **"Fork"** 按钮并点击：

```
┌─────────────────────────────────────────┐
│  GitHub 页面右上角                        │
│  ┌───────┐  ┌──────┐  ┌──────────┐      │
│  │ Watch │  │ Star │  │   Fork   │ ← 点这里
│  └───────┘  └──────┘  └──────────┘      │
└─────────────────────────────────────────┘
```

#### 3. 选择账号完成 Fork

- 选择你的 GitHub 账号
- 确保仓库名称为：`fuck_njfu_lib`（保持默认）
- 可选：修改描述
- 点击 **"Create fork"**

#### 4. 等待 Fork 完成

Fork 完成后，你会被跳转到你自己的仓库：
```
https://github.com/你的用户名/fuck_njfu_lib
```

✅ **完成！** 现在你有了自己的项目副本。

---

### 第二步：部署 Worker 后端

#### 1. 准备部署 URL

将下面的 URL 中的 `你的用户名` 替换为你的 GitHub 用户名：

```
https://deploy.workers.cloudflare.com/?url=https://github.com/你的用户名/fuck_njfu_lib/tree/main/cloudflare/worker
```

例如，如果你的用户名是 `zhangsan`，那么 URL 应该是：
```
https://deploy.workers.cloudflare.com/?url=https://github.com/zhangsan/fuck_njfu_lib/tree/main/cloudflare/worker
```

#### 2. 点击部署按钮

在浏览器中访问上面准备好的 URL，或者点击修改后的按钮：

[![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/)

#### 3. 登录 Cloudflare

如果没有 Cloudflare 账号：
1. 点击 **"Sign up"** 注册
2. 填写邮箱和密码
3. 验证邮箱
4. 完成注册

如果已有账号：
1. 输入邮箱和密码
2. 完成登录

#### 4. 授权 GitHub 访问

首次部署需要授权：
1. 点击 **"Authorize Cloudflare Workers"**
2. 确认授权访问你的 GitHub 仓库

#### 5. 配置 Worker

填写以下信息：

- **Account**：选择你的 Cloudflare 账户
- **Worker name**：输入 `library-info-worker`（或自定义名称）
- **Repository**：应该自动填充为你的仓库路径

#### 6. 开始部署

点击 **"Deploy"** 按钮，等待部署完成（约 30-60 秒）。

#### 7. 获取 Worker URL

部署成功后，你会看到 Worker URL，格式如下：
```
https://library-info-worker.你的子域名.workers.dev
```

**⚠️ 重要：请保存这个 URL！**

把这个 URL 复制到记事本或笔记应用中，后面会用到。

#### 8. 设置环境变量（关键步骤！）

Worker 需要你的图书馆账号密码才能工作。打开终端或命令行：

**macOS/Linux：**
```bash
# 进入 worker 目录
cd cloudflare/worker

# 设置学号
wrangler secret put USERNAME
# 输入提示出现后，输入你的学号，例如：220123456

# 设置统一认证密码
wrangler secret put EDU_PASSWORD
# 输入提示出现后，输入你的统一认证密码

# 设置图书馆密码
wrangler secret put LIB_PASSWORD
# 输入提示出现后，输入你的图书馆密码
```

**Windows：**
```cmd
cd cloudflare\worker
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD
```

**如果没有安装 Wrangler：**
```bash
npm install -g wrangler
wrangler login
```

✅ **Worker 后端部署完成！**

---

### 第三步：配置并部署 Pages 前端

#### 方法 A：使用 Cloudflare Dashboard（推荐）

##### 1. 更新 API 配置

在部署 Pages 之前，需要先配置 Worker 的 URL：

1. 在你 Fork 的仓库中，找到文件：
   ```
   cloudflare/pages/assets/js/config.js
   ```

2. 点击编辑（铅笔图标）

3. 找到这一行：
   ```javascript
   const API_BASE_URL = 'https://library-info-worker.your-subdomain.workers.dev';
   ```

4. 将其修改为你在第二步获得的 Worker URL：
   ```javascript
   const API_BASE_URL = 'https://library-info-worker.你的子域名.workers.dev';
   ```

5. 滚动到页面底部，填写提交信息（例如：`Update API URL`）

6. 点击 **"Commit changes"** 保存

##### 2. 登录 Cloudflare Dashboard

访问：https://dash.cloudflare.com/

##### 3. 创建 Pages 项目

1. 在左侧菜单中，点击 **"Workers & Pages"**
2. 点击右上角的 **"Create application"** 按钮
3. 选择 **"Pages"** 标签
4. 点击 **"Connect to Git"**

##### 4. 连接 GitHub 仓库

1. 如果是首次使用，点击 **"Connect GitHub"** 并授权
2. 在仓库列表中找到 `fuck_njfu_lib`
3. 点击 **"Begin setup"**

##### 5. 配置构建设置

填写以下配置：

```
项目名称（Project name）：
  library-info

生产分支（Production branch）：
  main

构建设置（Build settings）：
  Framework preset: None
  Build command: （留空）
  Build output directory: cloudflare/pages
  Root directory: /
```

**重要提示：**
- 构建命令不需要填写
- 构建输出目录填写：`cloudflare/pages`
- 根目录使用默认的 `/`

##### 6. 开始部署

1. 检查所有配置是否正确
2. 点击 **"Save and Deploy"**
3. 等待部署完成（约 1-2 分钟）

##### 7. 获取 Pages URL

部署成功后，你会看到：
```
✓ Success! Your site is now live at:
  https://library-info.pages.dev
```

**🎉 恭喜！前端部署完成！**

#### 方法 B：使用 Wrangler CLI

如果你熟悉命令行，可以使用 Wrangler CLI 快速部署：

```bash
# 1. 克隆你 Fork 的仓库
git clone https://github.com/你的用户名/fuck_njfu_lib.git
cd fuck_njfu_lib

# 2. 更新 API 配置
cd cloudflare/pages/assets/js
# 编辑 config.js，更新 API_BASE_URL

# 3. 部署 Pages
cd ../../  # 回到 pages 目录
wrangler pages deploy . --project-name=library-info
```

---

### 第四步：访问和测试

#### 1. 打开网站

在浏览器中访问你的 Pages URL：
```
https://library-info.pages.dev
```

#### 2. 测试功能

**测试实时流量：**
1. 点击首页的 **"实时流量"** 按钮
2. 应该能看到图书馆当前在馆人数、总容量、占用率
3. 数据会自动刷新

**测试座位查询：**
1. 点击首页的 **"座位查询"** 按钮
2. 选择楼层（例如：二层、三层）
3. 查看各区域的座位占用情况
4. 点击 **"查看详情"** 查看具体座位信息

#### 3. 检查错误

如果遇到问题，按 `F12` 打开浏览器开发者工具：

**检查 Console：**
- 如果显示"网络请求失败"或"Failed to fetch"
  → 检查 `config.js` 中的 `API_BASE_URL` 是否正确

- 如果显示"认证失败"或"401 Unauthorized"
  → 检查 Worker 的环境变量是否正确设置

**检查 Network：**
- 查看 API 请求是否成功
- 检查返回的状态码和响应内容

#### 4. 验证部署成功

✅ **部署成功的标志：**
- [ ] 首页能正常访问
- [ ] 实时流量能显示数据
- [ ] 座位查询能加载座位信息
- [ ] 点击详情能看到座位列表
- [ ] 没有网络错误

🎉 **恭喜！你已经成功部署了图书馆查询系统！**

---

## 🔧 进阶配置

### 配置自定义域名

如果你有自己的域名，可以配置自定义域名：

#### 1. 在 Cloudflare Dashboard 中

1. 进入你的 Pages 项目
2. 点击 **"Custom domains"** 标签
3. 点击 **"Set up a custom domain"**

#### 2. 添加域名

1. 输入你的域名或子域名，例如：
   - `library.yourdomain.com`
   - `lib.yourdomain.com`
2. 点击 **"Continue"**

#### 3. 配置 DNS

Cloudflare 会显示需要添加的 DNS 记录：

**如果域名在 Cloudflare：**
- 自动添加 DNS 记录

**如果域名在其他服务商：**
- 复制 DNS 记录信息
- 到你的域名服务商添加 CNAME 记录

#### 4. 等待生效

- DNS 生效通常需要 5-10 分钟
- Cloudflare 会自动配置 SSL 证书
- 完成后，你可以通过自定义域名访问

### 配置环境变量（可选）

如果需要修改 Worker 的环境变量：

```bash
cd cloudflare/worker

# 更新环境变量
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD

# 查看已设置的变量（不会显示值）
wrangler secret list
```

### 启用访问日志

在 Cloudflare Dashboard 中：

1. 进入 **Workers & Pages**
2. 选择你的 Worker
3. 点击 **"Logs"** 标签
4. 点击 **"Begin log stream"**

实时查看 API 请求日志。

---

## 📊 监控和维护

### 查看访问统计

#### Pages 统计

1. 进入 Cloudflare Dashboard
2. 选择 **Workers & Pages** → 你的 Pages 项目
3. 查看 **Analytics** 标签

显示：
- 请求数
- 带宽使用
- 访问分布

#### Worker 统计

1. 选择你的 Worker
2. 查看 **Metrics** 标签

显示：
- CPU 时间
- 请求数
- 错误率

### 更新部署

#### 更新 Worker

```bash
cd cloudflare/worker

# 修改代码后重新部署
wrangler deploy
```

#### 更新 Pages

**方法 1：通过 Git（推荐）**
```bash
# 修改代码
git add .
git commit -m "Update frontend"
git push
```
Cloudflare 会自动检测推送并重新部署。

**方法 2：使用 Wrangler**
```bash
cd cloudflare/pages
wrangler pages deploy .
```

### 回滚部署

如果新版本有问题，可以快速回滚：

1. 进入 Cloudflare Dashboard
2. 选择 Pages 项目
3. 点击 **"Deployments"** 标签
4. 找到之前的稳定版本
5. 点击 **"Rollback to this deployment"**

---

## 🐛 故障排查指南

### 常见错误及解决方法

#### 错误 1：Worker 部署失败

**错误信息：**
```
Error: Authentication error
```

**解决方法：**
```bash
# 重新登录 Cloudflare
wrangler login

# 检查账户信息
wrangler whoami

# 重新部署
wrangler deploy
```

#### 错误 2：找不到 wrangler.toml

**错误信息：**
```
Error: Could not find wrangler.toml
```

**解决方法：**
```bash
# 确保在正确的目录
cd cloudflare/worker

# 检查文件是否存在
ls -la wrangler.toml

# 重新部署
wrangler deploy
```

#### 错误 3：Pages 部署失败

**错误信息：**
```
Build failed: Could not find output directory
```

**解决方法：**
1. 检查构建输出目录设置
2. 确保设置为：`cloudflare/pages`
3. 重新部署

#### 错误 4：API 请求失败（CORS）

**错误信息：**
```
CORS policy: No 'Access-Control-Allow-Origin' header
```

**解决方法：**
1. 检查 Worker 代码中的 CORS 设置
2. 确保 Worker URL 正确
3. 检查 `config.js` 中的 API_BASE_URL

#### 错误 5：环境变量未生效

**症状：**
- API 返回 401 或 403
- 认证失败

**解决方法：**
```bash
cd cloudflare/worker

# 重新设置环境变量
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD

# 验证
wrangler secret list

# 重启 Worker（重新部署）
wrangler deploy
```

### 调试技巧

#### 1. 使用浏览器开发者工具

```
F12 → Console 标签
- 查看错误日志
- 检查 API 请求

F12 → Network 标签
- 查看请求详情
- 检查响应内容
```

#### 2. 查看 Worker 日志

```bash
cd cloudflare/worker
wrangler tail
```

实时查看 Worker 的运行日志。

#### 3. 本地测试

```bash
# 启动本地 Worker
cd cloudflare/worker
npm run dev

# 启动本地 Pages
cd cloudflare/pages
python -m http.server 8000
```

在本地环境测试功能。

---

## 📞 获取帮助

### 文档资源

- 📖 [主文档](./README.md) - 完整功能说明
- 🚀 [一键部署指南](./ONE_CLICK_DEPLOY.md) - 所有部署方式
- 🏗️ [架构文档](./ARCHITECTURE.md) - 系统设计说明
- 📋 [部署清单](./DEPLOYMENT_CHECKLIST.md) - 部署检查清单

### 社区支持

- 💬 [GitHub Issues](https://github.com/keggin-CHN/fuck_njfu_lib/issues) - 报告问题
- 🔍 [搜索已有问题](https://github.com/keggin-CHN/fuck_njfu_lib/issues?q=is%3Aissue) - 查找解决方案

### 官方文档

- [Cloudflare Workers 文档](https://developers.cloudflare.com/workers/)
- [Cloudflare Pages 文档](https://developers.cloudflare.com/pages/)
- [Wrangler CLI 文档](https://developers.cloudflare.com/workers/wrangler/)

---

## ✅ 部署检查清单

部署前检查：

- [ ] 已注册 Cloudflare 账号
- [ ] 已安装 Node.js 和 npm
- [ ] 已安装 Wrangler CLI
- [ ] 已登录 Cloudflare（`wrangler login`）
- [ ] 已 Fork 本项目到自己的 GitHub

Worker 部署检查：

- [ ] Worker 部署成功
- [ ] 获得 Worker URL
- [ ] 已设置环境变量（USERNAME、EDU_PASSWORD、LIB_PASSWORD）
- [ ] API 健康检查通过（`/api/health`）
- [ ] 流量接口测试通过（`/api/traffic`）

Pages 部署检查：

- [ ] 已更新 `config.js` 中的 API_BASE_URL
- [ ] Pages 部署成功
- [ ] 获得 Pages URL
- [ ] 首页能正常访问
- [ ] 实时流量功能正常
- [ ] 座位查询功能正常

---

## 🎉 总结

通过本指南，你应该已经成功：

1. ✅ Fork 了项目到自己的 GitHub
2. ✅ 部署了 Worker 后端
3. ✅ 配置了环境变量
4. ✅ 部署了 Pages 前端
5. ✅ 完成了功能测试

**下一步：**
- 🎨 自定义界面样式
- 🔔 配置通知功能（如果需要）
- 📱 添加移动端适配
- 🌐 配置自定义域名

享受你的图书馆查询系统吧！🚀
