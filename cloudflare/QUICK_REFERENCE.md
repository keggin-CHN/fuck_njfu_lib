# 快速参考卡片

## 🚀 部署命令

### 一键部署
```bash
# Bash (Linux/macOS)
./deploy.sh

# Node.js (跨平台)
node deploy.js

# npm命令
npm run deploy
```

### 分步部署
```bash
# 部署Worker
cd worker && wrangler deploy

# 部署Pages
cd pages && wrangler pages deploy .
```

## 🔧 开发命令

```bash
# 本地开发Worker
cd worker && wrangler dev

# 查看实时日志
cd worker && wrangler tail

# 测试API
curl https://your-worker.workers.dev/api/health
```

## 📝 环境变量

```bash
# 设置环境变量
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD

# 查看已设置的环境变量
wrangler secret list
```

## 🌐 API端点

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/traffic` | GET | 实时流量 |
| `/api/seats/summary?days_offset=0` | GET | 座位摘要 |
| `/api/seats/detail?area=区域名&days_offset=0` | GET | 座位详情 |
| `/api/areas` | GET | 区域列表 |

## 📁 重要文件

| 文件 | 说明 |
|------|------|
| `deploy.sh` | Bash部署脚本 |
| `deploy.js` | Node.js部署脚本 |
| `worker/wrangler.toml` | Worker配置 |
| `pages/assets/js/config.js` | API配置 |
| `.github/workflows/deploy.yml` | GitHub Actions |

## 🔑 获取凭证

### API Token
1. Dashboard → Profile → API Tokens
2. Create Token → Edit Cloudflare Workers
3. 复制保存

### Account ID
1. Dashboard → 选择域名
2. 右侧栏 → Account ID
3. 点击复制

### Worker子域名
```bash
wrangler whoami
```
查看 `subdomain` 字段

## 🐛 故障排查

### 查看日志
```bash
wrangler tail
```

### 重新部署
```bash
wrangler deploy --verbose
```

### 重新设置环境变量
```bash
echo "value" | wrangler secret put KEY_NAME
```

### 测试Worker
```bash
curl https://your-worker.workers.dev/api/health
```

## 📚 文档链接

- [一键部署](./ONE_CLICK_DEPLOY.md)
- [完整文档](./README.md)
- [快速开始](./QUICKSTART.md)
- [架构说明](./ARCHITECTURE.md)

## 💡 常用操作

### 更新Worker
```bash
cd worker
# 修改代码
wrangler deploy
```

### 更新Pages
```bash
cd pages
# 修改文件
wrangler pages deploy .
```

### 更新API地址
编辑 `pages/assets/js/config.js`：
```javascript
const API_BASE_URL = 'https://new-url.workers.dev';
```

### 查看部署状态
```bash
wrangler deployments list
```

## 🎯 快速测试

```bash
# 测试健康检查
curl https://your-worker.workers.dev/api/health

# 测试流量API
curl https://your-worker.workers.dev/api/traffic

# 测试座位API
curl "https://your-worker.workers.dev/api/seats/summary?days_offset=0"
```

## 📊 监控命令

```bash
# 实时日志
wrangler tail

# 部署历史
wrangler deployments list

# Worker信息
wrangler whoami

# 环境变量列表
wrangler secret list
```

## 🔄 更新流程

1. 修改代码
2. 提交到Git（可选）
3. 运行部署命令
4. 验证部署结果

## ⚡ 性能优化

- 使用KV存储缓存数据
- 配置缓存规则
- 优化代码逻辑
- 使用Web Workers

## 🔐 安全建议

- 定期更换密码
- 使用强密码
- 限制API Token权限
- 启用2FA认证
- 不要提交敏感信息

## 📞 获取帮助

- 📖 查看文档
- 🐛 提交Issue
- 💬 社区讨论
- 📧 联系支持

---

**打印此卡片** - 保存在手边随时查阅！
