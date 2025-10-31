# 图书馆实时流量和座位查询系统 - Cloudflare版

[![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Node.js Version](https://img.shields.io/badge/node-%3E%3D16-brightgreen.svg)](https://nodejs.org/)
[![Cloudflare Workers](https://img.shields.io/badge/Cloudflare-Workers-orange.svg)](https://workers.cloudflare.com/)

基于Cloudflare Workers和Pages构建的图书馆信息查询系统，提供实时流量监控和座位占用查询功能。

## 功能特性

- ✅ **实时流量监控**：查看图书馆当前在馆人数、总容量和占用率
- ✅ **座位查询**：按楼层和区域查看座位占用情况
- ✅ **详细信息**：查看每个座位的详细预约情况
- ✅ **日期选择**：支持查询今天和明天的座位数据
- ✅ **无服务器架构**：完全基于Cloudflare边缘网络，无需维护服务器
- ✅ **全球加速**：利用Cloudflare CDN实现全球快速访问

## 项目结构

```
cloudflare/
├── worker/              # Cloudflare Worker后端
│   ├── src/
│   │   ├── index.js    # 主入口文件
│   │   ├── auth.js     # 认证模块
│   │   ├── traffic.js  # 流量监控模块
│   │   ├── seats.js    # 座位查询模块
│   │   └── utils.js    # 工具函数
│   ├── wrangler.toml   # Worker配置
│   └── package.json    # 依赖配置
├── pages/              # Cloudflare Pages前端
│   ├── index.html      # 首页
│   ├── traffic.html    # 流量监控页面
│   ├── seats.html      # 座位查询页面
│   └── assets/
│       ├── css/
│       │   └── style.css
│       └── js/
│           ├── config.js  # API配置
│           ├── traffic.js # 流量页面脚本
│           └── seats.js   # 座位页面脚本
└── README.md           # 本文档
```

## 🚀 一键部署

**最简单、最快速的部署方式！**

### 方式三：Fork + Deploy to Cloudflare 按钮 ⭐ **强烈推荐**

这是最简单的部署方式，只需三步：

#### 第一步：Fork 本项目
1. 访问 https://github.com/keggin-CHN/fuck_njfu_lib
2. 点击右上角 **"Fork"** 按钮
3. 完成 Fork 到你的 GitHub 账号

#### 第二步：部署 Worker 后端
点击下方按钮一键部署（记得将URL中的 `你的用户名` 替换为你的 GitHub 用户名）：

[![Deploy to Cloudflare Workers](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/你的用户名/fuck_njfu_lib/tree/main/cloudflare/worker)

#### 第三步：部署 Pages 前端
使用 Cloudflare Dashboard 连接你 Fork 的仓库，详见：[完整部署指南](./ONE_CLICK_DEPLOY.md#方式三fork--deploy-to-cloudflare-按钮--强烈推荐)

**📖 详细图文教程**：
- [ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md) - 所有部署方式详细说明
- [DEPLOY_GUIDE.md](./DEPLOY_GUIDE.md) - 图文部署指南，含故障排查

---

### 其他部署方式

#### 方式一：GitHub Actions 自动部署

适合需要持续更新的用户。Fork 项目后，配置 GitHub Secrets，每次推送代码自动部署。

[查看配置教程](./ONE_CLICK_DEPLOY.md#方式一github-actions-自动部署) →

#### 方式二：本地脚本部署

适合开发者，使用自动化脚本快速部署：

```bash
cd cloudflare
./deploy.sh  # Linux/macOS
# 或
node deploy.js  # 跨平台
```

[查看详细说明](./ONE_CLICK_DEPLOY.md#方式二本地脚本部署) →

#### 方式四：完全手动部署（源码部署）

适合高级用户，完全控制部署过程，便于自定义和调试。

[查看源码部署教程](./ONE_CLICK_DEPLOY.md#方式四完全手动部署源码部署) →

---

## 手动部署步骤（高级用户）

如果你想完全控制部署过程，可以按照以下步骤手动部署。

### 一、准备工作

1. **注册Cloudflare账号**
   - 访问 https://dash.cloudflare.com/sign-up
   - 完成账号注册和邮箱验证

2. **安装Node.js和npm**
   ```bash
   # 检查是否已安装
   node --version
   npm --version
   
   # 如未安装，请访问 https://nodejs.org/ 下载安装
   ```

3. **安装Wrangler CLI**
   ```bash
   npm install -g wrangler
   
   # 验证安装
   wrangler --version
   ```

4. **登录Cloudflare账号**
   ```bash
   wrangler login
   ```
   这将打开浏览器进行授权。

### 二、部署Worker后端

1. **进入Worker目录**
   ```bash
   cd cloudflare/worker
   ```

2. **安装依赖**
   ```bash
   npm install
   ```

3. **配置环境变量（重要！）**
   
   设置图书馆账号密码作为Worker的秘钥：
   ```bash
   # 设置用户名
   wrangler secret put USERNAME
   # 提示输入后，输入你的学号
   
   # 设置统一认证密码
   wrangler secret put EDU_PASSWORD
   # 提示输入后，输入你的统一认证密码
   
   # 设置图书馆密码
   wrangler secret put LIB_PASSWORD
   # 提示输入后，输入你的图书馆密码
   ```
   
   **安全说明**：
   - 密码以加密形式存储在Cloudflare的安全环境中
   - 不会出现在代码或配置文件中
   - 只有Worker运行时可以访问这些秘钥

4. **修改Worker名称（可选）**
   
   编辑 `wrangler.toml` 文件：
   ```toml
   name = "library-info-worker"  # 修改为你想要的名称
   ```

5. **部署Worker**
   ```bash
   wrangler deploy
   ```
   
   部署成功后，会显示Worker的URL，例如：
   ```
   https://library-info-worker.your-subdomain.workers.dev
   ```
   
   **记住这个URL，后面配置前端时需要用到！**

6. **测试Worker API**
   ```bash
   # 测试健康检查
   curl https://your-worker-url.workers.dev/api/health
   
   # 测试流量接口
   curl https://your-worker-url.workers.dev/api/traffic
   
   # 测试座位接口
   curl https://your-worker-url.workers.dev/api/seats/summary?days_offset=0
   ```

### 三、部署Pages前端

1. **配置API地址**
   
   编辑 `pages/assets/js/config.js` 文件：
   ```javascript
   const API_BASE_URL = 'https://your-worker-url.workers.dev';
   ```
   将 `your-worker-url.workers.dev` 替换为上一步部署的Worker URL。

2. **初始化Git仓库（如果还没有）**
   ```bash
   cd ../pages
   git init
   git add .
   git commit -m "Initial commit"
   ```

3. **方式一：通过Wrangler部署（推荐）**
   ```bash
   # 在pages目录下
   wrangler pages deploy . --project-name=library-info
   ```
   
   首次部署会提示创建项目，按提示操作即可。

4. **方式二：通过Cloudflare Dashboard部署**
   
   a. 将代码推送到GitHub仓库：
   ```bash
   git remote add origin https://github.com/your-username/your-repo.git
   git push -u origin main
   ```
   
   b. 访问 Cloudflare Dashboard：
   - 进入 https://dash.cloudflare.com/
   - 选择 "Pages"
   - 点击 "Create a project"
   - 选择 "Connect to Git"
   - 选择你的GitHub仓库
   - 构建设置：
     - Build command: 留空（纯静态站点）
     - Build output directory: `/` 或留空
   - 点击 "Save and Deploy"

5. **获取Pages URL**
   
   部署完成后，会得到一个URL，例如：
   ```
   https://library-info.pages.dev
   ```

6. **配置自定义域名（可选）**
   
   在Cloudflare Dashboard的Pages项目设置中：
   - 进入 "Custom domains"
   - 添加你的域名
   - 按提示配置DNS记录

### 四、验证部署

1. **访问Pages网站**
   
   在浏览器中打开你的Pages URL，例如：
   ```
   https://library-info.pages.dev
   ```

2. **测试功能**
   - 点击"实时流量"，查看是否能正常显示数据
   - 点击"座位查询"，查看是否能正常加载座位信息
   - 点击某个区域的"查看详情"，测试详情弹窗

3. **检查错误**
   
   如果遇到问题，按F12打开浏览器开发者工具，查看Console中的错误信息：
   - 如果显示"网络请求失败"，检查 `config.js` 中的API_BASE_URL是否正确
   - 如果显示"认证失败"，检查Worker的环境变量是否正确设置

## 本地开发

### Worker本地开发

```bash
cd cloudflare/worker

# 安装依赖
npm install

# 创建 .dev.vars 文件存储本地环境变量
cat > .dev.vars << EOF
USERNAME=your-username
EDU_PASSWORD=your-edu-password
LIB_PASSWORD=your-lib-password
EOF

# 启动本地开发服务器
npm run dev
```

Worker将在 `http://localhost:8787` 运行。

### Pages本地开发

```bash
cd cloudflare/pages

# 修改 assets/js/config.js 中的API_BASE_URL为本地地址
# const API_BASE_URL = 'http://localhost:8787';

# 使用任何静态服务器，例如：
# Python 3
python -m http.server 8000

# Node.js (安装 http-server)
npx http-server -p 8000

# VS Code Live Server扩展
# 右键点击index.html，选择"Open with Live Server"
```

访问 `http://localhost:8000`

## API接口说明

### 1. 健康检查
```
GET /api/health
```

返回示例：
```json
{
  "success": true,
  "message": "服务正常运行",
  "timestamp": 1234567890
}
```

### 2. 获取实时流量
```
GET /api/traffic
```

返回示例：
```json
{
  "success": true,
  "current_count": 1234,
  "total_capacity": 2749,
  "remaining": 1515,
  "percentage": 44.9,
  "timestamp": 1234567890,
  "time": "14:30:25",
  "updated_at": "2024-01-15 14:30:25"
}
```

### 3. 获取座位摘要
```
GET /api/seats/summary?days_offset=0
```

参数：
- `days_offset`: 日期偏移，0=今天，1=明天

返回示例：
```json
{
  "success": true,
  "date": "20240115",
  "total": {
    "total": 2749,
    "available": 823,
    "occupied": 1926,
    "rate": 70.1
  },
  "floors": { ... },
  "areas": { ... }
}
```

### 4. 获取座位详情
```
GET /api/seats/detail?area=二层A区&days_offset=0
```

参数：
- `area`: 区域名称，如"二层A区"
- `days_offset`: 日期偏移，0=今天，1=明天

返回示例：
```json
{
  "success": true,
  "area": "二层A区",
  "date": "20240115",
  "seats": [
    {
      "devId": 100455344001,
      "devName": "001",
      "devStatus": 1,
      "isAvailable": false,
      "reservations": [
        {
          "startTime": "09:30",
          "endTime": "22:00",
          "status": "预约中"
        }
      ]
    }
  ]
}
```

### 5. 获取区域列表
```
GET /api/areas
```

返回示例：
```json
{
  "success": true,
  "areas": {
    "二层A区": { "roomId": 100455344, "floor": 2, "area": "A" },
    "二层B区": { "roomId": 100455346, "floor": 2, "area": "B" }
  }
}
```

## 环境变量说明

Worker需要以下环境变量（通过 `wrangler secret` 设置）：

| 变量名 | 说明 | 示例 |
|--------|------|------|
| USERNAME | 图书馆账号（学号） | 220123456 |
| EDU_PASSWORD | 统一认证系统密码 | your-password |
| LIB_PASSWORD | 图书馆系统密码 | your-lib-password |

**重要**：这些变量存储为加密的秘钥，不会出现在代码中。

## 更新和维护

### 更新Worker

```bash
cd cloudflare/worker

# 修改代码后重新部署
wrangler deploy
```

### 更新Pages

**方式一：使用Wrangler**
```bash
cd cloudflare/pages
wrangler pages deploy .
```

**方式二：通过Git推送（如果使用Git集成）**
```bash
git add .
git commit -m "Update pages"
git push
```

Cloudflare会自动检测推送并重新部署。

### 更新环境变量

```bash
cd cloudflare/worker

# 更新某个秘钥
wrangler secret put USERNAME
```

### 查看日志

```bash
cd cloudflare/worker

# 实时查看Worker日志
wrangler tail
```

## 故障排查

### 问题1：Worker部署成功但API返回错误

**可能原因**：环境变量未正确设置

**解决方法**：
```bash
# 重新设置环境变量
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD

# 重新部署
wrangler deploy
```

### 问题2：Pages无法访问Worker API

**可能原因**：CORS问题或API地址错误

**解决方法**：
1. 检查 `pages/assets/js/config.js` 中的 `API_BASE_URL` 是否正确
2. 确保Worker已正确部署且可访问
3. 检查浏览器控制台的错误信息

### 问题3：认证失败

**可能原因**：账号密码错误或已过期

**解决方法**：
1. 确认账号密码正确
2. 确认账号未被锁定或过期
3. 重新设置环境变量

### 问题4：数据加载缓慢

**可能原因**：首次认证需要时间

**解决方法**：
- Worker会缓存认证状态30分钟
- 首次访问或认证过期后会较慢，后续访问会快速响应
- 考虑添加KV存储来延长缓存时间

## 成本说明

使用Cloudflare Workers和Pages的免费套餐：

| 服务 | 免费额度 | 说明 |
|------|----------|------|
| Workers | 100,000 请求/天 | 超出后 $0.50/百万请求 |
| Pages | 无限请求 | 完全免费 |
| Workers KV | 100,000 读取/天 | 可选，用于缓存 |

对于个人使用，免费额度完全足够。

## 安全建议

1. **定期更换密码**：建议每3-6个月更换一次图书馆密码
2. **监控使用情况**：在Cloudflare Dashboard中监控Worker的请求量
3. **限制访问**：如果只希望特定人使用，可以在Worker中添加Token验证
4. **备份代码**：定期备份代码到Git仓库

## 许可证

MIT License

## 相关链接

- [Cloudflare Workers 文档](https://developers.cloudflare.com/workers/)
- [Cloudflare Pages 文档](https://developers.cloudflare.com/pages/)
- [Wrangler CLI 文档](https://developers.cloudflare.com/workers/wrangler/)

## 技术支持

如有问题，请检查：
1. Worker日志：`wrangler tail`
2. 浏览器开发者工具的Console和Network标签
3. Cloudflare Dashboard的分析和日志

---

**注意**：本系统仅供学习和个人使用，请遵守学校图书馆的相关规定。
