# 🚀 一键部署指南

本文档提供多种部署方式，**推荐使用方式三**进行一键部署。

---

## 📌 方式三：Fork + Deploy to Cloudflare 按钮 ⭐ **强烈推荐**

这是最简单、最快速的部署方式！只需三步即可完成部署。

### 第一步：Fork 本项目

**重要：** 你必须先 Fork 本项目到你自己的 GitHub 账号，才能进行部署。

1. 访问本项目的 GitHub 页面：https://github.com/keggin-CHN/fuck_njfu_lib
2. 点击页面右上角的 **"Fork"** 按钮
3. 选择你的 GitHub 账号，等待 Fork 完成
4. 现在你拥有了自己的项目副本：`https://github.com/你的用户名/fuck_njfu_lib`

### 第二步：部署 Worker 后端

1. **点击下方按钮一键部署 Worker 后端**：

   [![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/你的用户名/fuck_njfu_lib/tree/main/cloudflare/worker)

   > ⚠️ **注意**：请将上方 URL 中的 `你的用户名` 替换为你的 GitHub 用户名！

2. 在打开的页面中：
   - 登录你的 Cloudflare 账号（如没有账号，需先注册）
   - 选择你的 Cloudflare 账户
   - 输入 Worker 名称（建议使用：`library-info-worker`）
   - 点击 **"Deploy"** 开始部署

3. 部署完成后，你会得到一个 Worker URL，例如：
   ```
   https://library-info-worker.你的子域名.workers.dev
   ```
   **请保存这个 URL，下一步需要用到！**

### 第三步：配置并部署 Pages 前端

#### 3.1 配置 API 地址

1. 在你 Fork 的项目中，编辑文件：`cloudflare/pages/assets/js/config.js`
2. 找到这一行：
   ```javascript
   const API_BASE_URL = 'https://library-info-worker.your-subdomain.workers.dev';
   ```
3. 将其替换为你在第二步获得的 Worker URL：
   ```javascript
   const API_BASE_URL = 'https://library-info-worker.你的子域名.workers.dev';
   ```
4. 保存并提交更改

#### 3.2 部署 Pages 前端

**方式 A：使用 Cloudflare Dashboard（推荐）**

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
2. 进入 **"Workers & Pages"** 选项卡
3. 点击 **"Create application"** → **"Pages"** → **"Connect to Git"**
4. 选择你 Fork 的 GitHub 仓库：`你的用户名/fuck_njfu_lib`
5. 配置构建设置：
   - **项目名称**：`library-info`
   - **生产分支**：`main`
   - **构建命令**：留空
   - **构建输出目录**：`cloudflare/pages`
   - **根目录**：`/`（默认）
6. 点击 **"Save and Deploy"**
7. 等待部署完成（约1-2分钟）

**方式 B：使用 Wrangler CLI**

```bash
# 1. 安装 Wrangler CLI
npm install -g wrangler

# 2. 登录 Cloudflare
wrangler login

# 3. 进入 pages 目录
cd cloudflare/pages

# 4. 部署 Pages
wrangler pages deploy . --project-name=library-info
```

#### 3.3 设置 Worker 环境变量（重要！）

Worker 需要你的图书馆账号密码才能正常工作：

```bash
cd cloudflare/worker

# 设置学号
wrangler secret put USERNAME
# 输入你的学号，例如：220123456

# 设置统一认证密码
wrangler secret put EDU_PASSWORD
# 输入你的统一认证密码

# 设置图书馆密码
wrangler secret put LIB_PASSWORD
# 输入你的图书馆密码
```

### 第四步：访问你的应用 🎉

部署完成！现在你可以访问：

- **前端网站**：`https://library-info.pages.dev`
- **后端API**：`https://library-info-worker.你的子域名.workers.dev`

测试一下功能：
- 点击 "实时流量" 查看图书馆当前人数
- 点击 "座位查询" 查看座位占用情况

---

## 📋 方式一：GitHub Actions 自动部署

适合需要频繁更新代码的用户，每次推送代码自动部署。

### 配置步骤（简要说明）

1. **Fork 本项目**（同方式三第一步）

2. **设置 GitHub Secrets**：在你的仓库设置中添加以下 Secrets：
   - `CLOUDFLARE_API_TOKEN` - [如何获取](#获取-cloudflare-凭证)
   - `CLOUDFLARE_ACCOUNT_ID` - [如何获取](#获取-cloudflare-凭证)
   - `CLOUDFLARE_ACCOUNT_SUBDOMAIN` - [如何获取](#获取-cloudflare-凭证)
   - `USERNAME` - 你的学号
   - `EDU_PASSWORD` - 统一认证密码
   - `LIB_PASSWORD` - 图书馆密码

3. **触发部署**：
   ```bash
   git add .
   git commit -m "Update deployment"
   git push
   ```
   每次推送到 main 分支，GitHub Actions 会自动部署！

详细说明请参考：[GitHub Actions 完整配置指南](#github-actions-详细配置)

---

## 🖥️ 方式二：本地脚本部署

适合开发者或需要完全控制部署过程的用户。

### Bash 脚本（Linux/macOS）

```bash
cd cloudflare
chmod +x deploy.sh
./deploy.sh
```

### Node.js 脚本（Windows/跨平台）

```bash
cd cloudflare
npm install
node deploy.js
```

脚本会自动完成：
- ✅ 检查所需工具（Node.js、Wrangler）
- ✅ 登录 Cloudflare
- ✅ 设置环境变量
- ✅ 部署 Worker 和 Pages
- ✅ 显示部署结果

---

## 🔧 方式四：完全手动部署（源码部署）

适合需要自定义配置或学习部署流程的高级用户。

### 准备工作

1. **注册 Cloudflare 账号**：访问 https://dash.cloudflare.com/sign-up

2. **安装 Node.js**：访问 https://nodejs.org/ 下载并安装（需要 Node.js 16+）

3. **安装 Wrangler CLI**：
   ```bash
   npm install -g wrangler
   wrangler --version  # 验证安装
   ```

4. **登录 Cloudflare**：
   ```bash
   wrangler login
   ```
   会打开浏览器进行授权。

### 部署 Worker 后端

1. **进入 Worker 目录**：
   ```bash
   cd cloudflare/worker
   ```

2. **安装依赖**：
   ```bash
   npm install
   ```

3. **配置 Worker 名称**（可选）：
   
   编辑 `wrangler.toml` 文件，修改 Worker 名称：
   ```toml
   name = "library-info-worker"  # 改成你想要的名称
   ```

4. **部署 Worker**：
   ```bash
   wrangler deploy
   ```
   
   部署成功后会显示 Worker URL，例如：
   ```
   Published library-info-worker (0.52 sec)
     https://library-info-worker.你的子域名.workers.dev
   ```
   
   **记下这个 URL！**

5. **设置环境变量**（重要）：
   ```bash
   # 设置学号
   wrangler secret put USERNAME
   
   # 设置统一认证密码
   wrangler secret put EDU_PASSWORD
   
   # 设置图书馆密码
   wrangler secret put LIB_PASSWORD
   ```
   
   **安全说明**：环境变量以加密形式存储在 Cloudflare，不会出现在代码中。

6. **测试 Worker API**：
   ```bash
   # 测试健康检查
   curl https://library-info-worker.你的子域名.workers.dev/api/health
   
   # 测试流量接口
   curl https://library-info-worker.你的子域名.workers.dev/api/traffic
   ```

### 部署 Pages 前端

1. **配置 API 地址**：
   
   编辑 `cloudflare/pages/assets/js/config.js`：
   ```javascript
   const API_BASE_URL = 'https://library-info-worker.你的子域名.workers.dev';
   ```
   将 URL 替换为上一步部署的 Worker URL。

2. **部署 Pages**：
   
   **方式 A：使用 Wrangler CLI（推荐）**
   ```bash
   cd cloudflare/pages
   wrangler pages deploy . --project-name=library-info
   ```
   
   **方式 B：通过 Cloudflare Dashboard**
   - 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
   - 进入 "Workers & Pages" → "Create application" → "Pages"
   - 选择 "Upload assets"
   - 上传 `cloudflare/pages` 目录的所有文件
   - 项目名称：`library-info`
   - 点击 "Deploy"

3. **获取 Pages URL**：
   
   部署完成后，你会得到一个 URL：
   ```
   https://library-info.pages.dev
   ```

4. **访问并测试**：
   
   在浏览器中打开 Pages URL，测试各项功能是否正常：
   - 实时流量显示
   - 座位查询功能
   - 座位详情查看

### 本地开发调试

**Worker 本地开发：**
```bash
cd cloudflare/worker

# 创建本地环境变量文件
cat > .dev.vars << EOF
USERNAME=你的学号
EDU_PASSWORD=你的统一认证密码
LIB_PASSWORD=你的图书馆密码
EOF

# 启动本地开发服务器
npm run dev
# 访问 http://localhost:8787
```

**Pages 本地开发：**
```bash
cd cloudflare/pages

# 修改 config.js 中的 API_BASE_URL 为本地地址
# const API_BASE_URL = 'http://localhost:8787';

# 启动本地服务器（选择一种方式）
python -m http.server 8000  # Python
# 或
npx http-server -p 8000     # Node.js

# 访问 http://localhost:8000
```

### 更新部署

**更新 Worker：**
```bash
cd cloudflare/worker
wrangler deploy
```

**更新 Pages：**
```bash
cd cloudflare/pages
wrangler pages deploy .
```

### 配置自定义域名（可选）

1. **在 Cloudflare Dashboard 中**：
   - 进入你的 Pages 项目
   - 选择 "Custom domains"
   - 添加你的域名（例如：`library.yourdomain.com`）
   - 按提示配置 DNS 记录

2. **等待 DNS 生效**（通常 5-10 分钟）

3. **自动 HTTPS**：Cloudflare 会自动为自定义域名配置 SSL 证书

---

## 🔐 获取 Cloudflare 凭证

### 获取 API Token

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
2. 点击右上角头像 → **"My Profile"**
3. 选择 **"API Tokens"** 标签
4. 点击 **"Create Token"**
5. 使用 **"Edit Cloudflare Workers"** 模板
6. 权限设置：
   - Account Resources: `All accounts`
   - Zone Resources: `All zones`
7. 点击 **"Continue to summary"** → **"Create Token"**
8. **复制并保存 Token**（只显示一次！）

### 获取 Account ID

**方式 1：从 Dashboard 获取**
1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
2. 选择任意一个域名（或 Workers & Pages）
3. 在右侧栏找到 **"Account ID"**
4. 点击复制

**方式 2：使用 Wrangler**
```bash
wrangler whoami
```
查看输出中的 `Account ID`

### 获取 Worker 子域名

**方式 1：从 Dashboard 获取**
1. 进入 **"Workers & Pages"**
2. 查看任意 Worker 的 URL
3. 格式：`https://xxx.你的子域名.workers.dev`
4. 提取其中的子域名部分

**方式 2：使用 Wrangler**
```bash
wrangler whoami
```
查看输出中的 `subdomain`

---

## ✅ 部署后验证

### 检查 Worker

```bash
curl https://library-info-worker.你的子域名.workers.dev/api/health
```

预期输出：
```json
{
  "success": true,
  "message": "服务正常运行",
  "timestamp": 1234567890
}
```

### 检查 Pages

访问：`https://library-info.pages.dev`

应该能看到：
- 首页正常显示
- "实时流量"功能正常
- "座位查询"功能正常

### 检查 API 连接

打开浏览器开发者工具（F12），查看 Console：
- 没有网络错误
- 数据能正常加载

---

## 🐛 常见问题

### Q1: Deploy to Cloudflare 按钮提示找不到 wrangler 文件？

**解决方法**：确保你点击的部署 URL 正确指向 `worker` 子目录：
```
https://deploy.workers.cloudflare.com/?url=https://github.com/你的用户名/fuck_njfu_lib/tree/main/cloudflare/worker
```

### Q2: Worker 部署成功但 API 返回错误？

**原因**：环境变量未设置

**解决方法**：
```bash
cd cloudflare/worker
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD
```

### Q3: Pages 无法连接 Worker？

**原因**：`config.js` 中的 API_BASE_URL 未正确配置

**解决方法**：
1. 编辑 `cloudflare/pages/assets/js/config.js`
2. 确保 `API_BASE_URL` 指向你的 Worker URL
3. 重新部署 Pages

### Q4: Wrangler 登录失败？

**解决方法**：
1. 确保浏览器已安装
2. 网络连接正常
3. 尝试手动授权：访问显示的授权链接

### Q5: GitHub Actions 部署失败？

**检查清单**：
- [ ] 所有 Secrets 已正确设置
- [ ] API Token 权限正确
- [ ] Account ID 正确
- [ ] 查看 Actions 日志获取详细错误

### Q6: 本地开发无法访问 API？

**解决方法**：
1. 检查 `.dev.vars` 文件是否正确配置
2. 确保 `npm run dev` 正在运行
3. 检查防火墙设置

---

## 📊 推荐使用方式

| 使用场景 | 推荐方式 | 优势 |
|---------|---------|------|
| 🚀 快速体验 | **方式三：Deploy 按钮** | 最简单，三步完成 |
| 🔄 持续更新 | 方式一：GitHub Actions | 自动化，无需手动操作 |
| 🛠️ 开发调试 | 方式四：手动部署 | 完全控制，便于调试 |
| 💻 跨平台部署 | 方式二：脚本部署 | 一键完成，适合各系统 |

---

## 🔒 安全提示

⚠️ **重要安全建议**：

- ❌ **不要**在代码中直接写入密码
- ✅ **务必**使用环境变量或 Secrets 存储敏感信息
- 🔄 **定期**更换 API Token 和密码
- 🔐 **限制** Token 权限范围到最小需要
- 📝 **不要**将 `.dev.vars` 提交到 Git

---

## 📚 相关文档

- 📖 [完整功能说明](./README.md)
- 🏗️ [架构设计文档](./ARCHITECTURE.md)
- 📋 [部署检查清单](./DEPLOYMENT_CHECKLIST.md)
- 🎯 [快速开始指南](./QUICKSTART.md)
- ⚙️ [使用指南](./USAGE_GUIDE.md)

---

## 🆘 获取帮助

遇到问题？

1. 📖 查看本文档的[常见问题](#常见问题)部分
2. 🔍 搜索 [GitHub Issues](https://github.com/keggin-CHN/fuck_njfu_lib/issues)
3. 💬 提交新的 [Issue](https://github.com/keggin-CHN/fuck_njfu_lib/issues/new)
4. 📧 联系项目维护者

---

## 🎉 开始部署

现在选择一种方式开始部署吧！

**推荐新手使用：[方式三 - Fork + Deploy 按钮](#方式三fork--deploy-to-cloudflare-按钮--强烈推荐)**

祝部署顺利！🚀
