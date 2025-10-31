# 使用指南

> 从零开始，10分钟掌握所有功能

## 📋 目录

1. [快速部署](#快速部署)
2. [功能使用](#功能使用)
3. [命令参考](#命令参考)
4. [常见场景](#常见场景)
5. [故障排查](#故障排查)

---

## 🚀 快速部署

### 第一次使用？从这里开始

#### 步骤1: 准备环境

```bash
# 确保已安装Node.js
node --version  # 应该 >= 16

# 确保已安装npm
npm --version
```

#### 步骤2: 一键部署

```bash
cd cloudflare
./deploy.sh
```

**就这么简单！** 脚本会：
- ✅ 自动检查环境
- ✅ 引导你登录Cloudflare
- ✅ 帮你设置账号密码
- ✅ 自动部署Worker和Pages
- ✅ 显示访问地址

#### 步骤3: 访问网站

部署完成后，打开浏览器访问显示的Pages URL：
```
https://library-info.pages.dev
```

**恭喜！** 你的系统已经上线了！🎉

---

## 💡 功能使用

### 1. 实时流量监控

**功能**：查看图书馆当前在馆人数

**使用方法**：
1. 打开网站
2. 点击「实时流量」
3. 查看当前在馆人数、总容量、占用率
4. 点击「刷新」获取最新数据

**API调用**：
```bash
curl https://your-worker.workers.dev/api/traffic
```

**返回示例**：
```json
{
  "success": true,
  "current_count": 1234,
  "total_capacity": 2749,
  "percentage": 44.9,
  "updated_at": "2024-01-15 14:30:25"
}
```

### 2. 座位查询

**功能**：查看各楼层、各区域的座位占用情况

**使用方法**：
1. 打开网站
2. 点击「座位查询」
3. 选择日期（今天/明天）
4. 选择楼层筛选（可选）
5. 点击区域卡片查看详情

**支持的区域**：
- 二层A区、二层B区
- 三层A区、三层B区、三层C区、三楼夹层
- 四层A区、四层夹层
- 五层A区
- 六层A区
- 七层北侧、七层南侧

**API调用**：
```bash
# 获取摘要
curl "https://your-worker.workers.dev/api/seats/summary?days_offset=0"

# 获取详情
curl "https://your-worker.workers.dev/api/seats/detail?area=二层A区&days_offset=0"
```

### 3. 座位详情

**功能**：查看每个座位的具体预约情况

**使用方法**：
1. 在座位查询页面
2. 点击任意区域卡片的「查看详情」
3. 查看座位列表和预约时间段

**信息包含**：
- 座位号
- 占用状态（空闲/已占用）
- 预约时间段
- 预约状态

---

## 🔧 命令参考

### 部署命令

```bash
# 一键部署（推荐）
./deploy.sh

# Node.js部署
node deploy.js

# npm快捷命令
npm run deploy

# 仅部署Worker
npm run deploy:worker

# 仅部署Pages
npm run deploy:pages
```

### 开发命令

```bash
# 本地开发Worker
npm run dev:worker

# 查看实时日志
npm run logs

# 测试API
curl http://localhost:8787/api/health
```

### 管理命令

```bash
# 查看部署状态
wrangler deployments list

# 查看账户信息
wrangler whoami

# 查看环境变量
wrangler secret list

# 删除环境变量
wrangler secret delete KEY_NAME
```

---

## 📖 常见场景

### 场景1: 更新代码

```bash
# 1. 修改代码
vim worker/src/index.js

# 2. 重新部署
cd worker
wrangler deploy

# 3. 验证
curl https://your-worker.workers.dev/api/health
```

### 场景2: 更新前端

```bash
# 1. 修改前端文件
vim pages/index.html

# 2. 重新部署
cd pages
wrangler pages deploy .

# 3. 访问验证
open https://library-info.pages.dev
```

### 场景3: 更改密码

```bash
cd worker

# 重新设置环境变量
echo "new-password" | wrangler secret put EDU_PASSWORD

# 重新部署
wrangler deploy
```

### 场景4: 查看日志

```bash
cd worker

# 实时日志
wrangler tail

# 查看特定请求
wrangler tail --format pretty
```

### 场景5: 回滚部署

```bash
# 查看部署历史
wrangler deployments list

# 回滚到指定版本
wrangler rollback [deployment-id]
```

### 场景6: 本地测试

```bash
cd worker

# 启动本地开发服务器
wrangler dev

# 在另一个终端测试
curl http://localhost:8787/api/traffic
```

---

## 🐛 故障排查

### 问题1: 部署失败

**现象**：运行deploy.sh报错

**解决方法**：
```bash
# 检查Node.js版本
node --version  # 应该 >= 16

# 检查Wrangler安装
wrangler --version

# 重新安装Wrangler
npm install -g wrangler

# 重新登录
wrangler login

# 再次尝试部署
./deploy.sh
```

### 问题2: 无法获取数据

**现象**：前端显示"获取数据失败"

**排查步骤**：
```bash
# 1. 测试Worker API
curl https://your-worker.workers.dev/api/health

# 2. 检查Worker日志
cd worker
wrangler tail

# 3. 检查前端API配置
cat pages/assets/js/config.js

# 4. 验证环境变量
wrangler secret list
```

### 问题3: 认证失败

**现象**：API返回"认证失败"

**解决方法**：
```bash
cd worker

# 重新设置环境变量
echo "your-username" | wrangler secret put USERNAME
echo "your-edu-password" | wrangler secret put EDU_PASSWORD
echo "your-lib-password" | wrangler secret put LIB_PASSWORD

# 重新部署
wrangler deploy

# 查看日志验证
wrangler tail
```

### 问题4: 权限错误

**现象**：Linux/macOS上无法执行脚本

**解决方法**：
```bash
# 给脚本执行权限
chmod +x deploy.sh

# 或使用Node.js脚本
node deploy.js
```

### 问题5: API配置错误

**现象**：前端无法连接到Worker

**解决方法**：
```bash
# 1. 获取正确的Worker URL
cd worker
wrangler whoami
# 记下subdomain

# 2. 更新前端配置
vim pages/assets/js/config.js
# 修改 API_BASE_URL

# 3. 重新部署Pages
cd pages
wrangler pages deploy .
```

---

## 📚 学习路径

### 新手路线

1. **第1天**：使用deploy.sh一键部署
2. **第2天**：熟悉前端界面和功能
3. **第3天**：学习基本的wrangler命令
4. **第4天**：尝试修改前端样式
5. **第5天**：阅读Worker源码

### 进阶路线

1. **第1周**：理解Worker架构
2. **第2周**：学习Cloudflare平台特性
3. **第3周**：配置GitHub Actions
4. **第4周**：优化性能和缓存

### 高级路线

1. **第1月**：添加新功能
2. **第2月**：优化代码质量
3. **第3月**：实现高级特性
4. **第4月**：分享和贡献

---

## 🎯 最佳实践

### 部署建议

1. ✅ 使用一键部署脚本
2. ✅ 定期备份代码
3. ✅ 配置GitHub Actions
4. ✅ 监控部署日志
5. ✅ 测试后再部署

### 安全建议

1. 🔒 使用强密码
2. 🔒 定期更换密码
3. 🔒 不要提交敏感信息
4. 🔒 限制API Token权限
5. 🔒 启用2FA认证

### 性能建议

1. ⚡ 使用KV缓存
2. ⚡ 优化API调用
3. ⚡ 压缩资源文件
4. ⚡ 使用CDN加速
5. ⚡ 监控响应时间

---

## 📞 获取帮助

### 文档资源

- 📖 [完整文档](./README.md)
- ⚡ [快速开始](./QUICKSTART.md)
- 🚀 [一键部署](./ONE_CLICK_DEPLOY.md)
- 🏗️ [架构说明](./ARCHITECTURE.md)
- 📋 [快速参考](./QUICK_REFERENCE.md)

### 常见问题

查看 [ONE_CLICK_DEPLOY.md](./ONE_CLICK_DEPLOY.md) 的常见问题章节

### 在线资源

- [Cloudflare Workers文档](https://developers.cloudflare.com/workers/)
- [Cloudflare Pages文档](https://developers.cloudflare.com/pages/)
- [Wrangler CLI文档](https://developers.cloudflare.com/workers/wrangler/)

---

## 🎉 总结

通过本指南，你已经学会了：

- ✅ 如何部署系统
- ✅ 如何使用所有功能
- ✅ 如何执行常见操作
- ✅ 如何排查问题

**下一步**：

1. 实际部署系统
2. 探索所有功能
3. 阅读架构文档
4. 尝试自定义修改

---

**开始使用** → 运行 `./deploy.sh` 立即部署！
