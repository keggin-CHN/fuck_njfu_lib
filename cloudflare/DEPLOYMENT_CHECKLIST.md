# 部署检查清单 ✅

使用本清单确保顺利完成部署。

## 前置准备

- [ ] 已注册Cloudflare账号
- [ ] 已安装Node.js和npm
- [ ] 已安装Wrangler CLI (`npm install -g wrangler`)
- [ ] 已登录Cloudflare (`wrangler login`)
- [ ] 已准备好图书馆账号和密码

## Worker部署

### 1. 安装依赖

- [ ] 进入worker目录：`cd cloudflare/worker`
- [ ] 运行：`npm install`
- [ ] 确认package.json中的依赖已安装

### 2. 配置环境变量

- [ ] 设置USERNAME：`wrangler secret put USERNAME`
- [ ] 设置EDU_PASSWORD：`wrangler secret put EDU_PASSWORD`
- [ ] 设置LIB_PASSWORD：`wrangler secret put LIB_PASSWORD`
- [ ] 确认所有密码输入正确

### 3. 部署Worker

- [ ] 运行：`wrangler deploy`
- [ ] 记录Worker URL（例如：`https://library-info-worker.xxx.workers.dev`）
- [ ] 测试健康检查：`curl https://your-worker-url/api/health`
- [ ] 测试流量接口：`curl https://your-worker-url/api/traffic`

### 4. 验证Worker

- [ ] Worker部署成功无错误
- [ ] API响应正常
- [ ] 认证功能正常
- [ ] 日志中无异常错误

## Pages部署

### 1. 配置API地址

- [ ] 进入pages目录：`cd cloudflare/pages`
- [ ] 编辑`assets/js/config.js`
- [ ] 将`API_BASE_URL`改为Worker URL
- [ ] 保存文件

### 2. 部署Pages

- [ ] 运行：`wrangler pages deploy . --project-name=library-info`
- [ ] 记录Pages URL（例如：`https://library-info.pages.dev`）
- [ ] 确认部署成功无错误

### 3. 验证Pages

- [ ] 访问Pages URL
- [ ] 首页正常显示
- [ ] 导航链接正常工作
- [ ] 流量页面可以加载数据
- [ ] 座位页面可以加载数据
- [ ] 详情弹窗正常工作

## 功能测试

### 实时流量页面

- [ ] 页面正常加载
- [ ] 显示在馆人数
- [ ] 显示总容量
- [ ] 显示占用率
- [ ] 进度条正常显示
- [ ] 刷新按钮正常工作
- [ ] 更新时间正确显示

### 座位查询页面

- [ ] 页面正常加载
- [ ] 总览统计正确显示
- [ ] 今天/明天按钮切换正常
- [ ] 楼层筛选正常工作
- [ ] 所有区域卡片正常显示
- [ ] 点击"查看详情"正常弹出模态框
- [ ] 模态框中座位列表正确显示
- [ ] 预约信息正确显示

## 性能检查

- [ ] 首次加载时间 < 3秒
- [ ] API响应时间 < 1秒
- [ ] 页面切换流畅
- [ ] 无明显卡顿
- [ ] 移动端显示正常

## 安全检查

- [ ] HTTPS正常启用
- [ ] 密码未出现在代码中
- [ ] 环境变量正确设置
- [ ] CORS配置正常
- [ ] 无敏感信息泄露

## 文档检查

- [ ] 已阅读README.md
- [ ] 了解所有API端点
- [ ] 知道如何查看日志
- [ ] 知道如何更新代码
- [ ] 知道如何排查问题

## 可选配置

### 自定义域名

- [ ] 在Cloudflare Dashboard配置自定义域名
- [ ] DNS记录已正确配置
- [ ] SSL证书已生效
- [ ] 访问自定义域名正常

### 监控和日志

- [ ] 在Dashboard中查看Worker分析
- [ ] 设置错误通知（可选）
- [ ] 定期检查日志
- [ ] 监控请求量

## 后续维护

### 定期检查

- [ ] 每周检查一次服务状态
- [ ] 每月检查一次请求量
- [ ] 定期更换密码
- [ ] 关注Cloudflare服务状态

### 代码更新

- [ ] 了解如何更新Worker代码
- [ ] 了解如何更新Pages代码
- [ ] 备份重要配置
- [ ] 保持依赖更新

## 问题排查

### Worker问题

```bash
# 查看实时日志
cd cloudflare/worker
wrangler tail

# 重新部署
wrangler deploy

# 重新设置环境变量
wrangler secret put USERNAME
wrangler secret put EDU_PASSWORD
wrangler secret put LIB_PASSWORD
```

### Pages问题

```bash
# 重新部署
cd cloudflare/pages
wrangler pages deploy .

# 检查配置文件
cat assets/js/config.js
```

### 常见错误

**错误：认证失败**
- [ ] 检查密码是否正确
- [ ] 重新设置环境变量
- [ ] 查看Worker日志

**错误：CORS问题**
- [ ] 检查API_BASE_URL是否正确
- [ ] 确保Worker已部署
- [ ] 检查浏览器控制台

**错误：数据加载失败**
- [ ] 检查Worker是否正常运行
- [ ] 查看网络请求状态
- [ ] 检查API响应内容

## 完成标记

- [ ] 所有上述步骤已完成
- [ ] 所有功能测试通过
- [ ] 文档已阅读
- [ ] 系统正常运行

## 部署信息记录

**部署日期**：__________________

**Worker URL**：__________________

**Pages URL**：__________________

**自定义域名**：__________________

**备注**：
__________________________________________________
__________________________________________________
__________________________________________________

---

恭喜完成部署！🎉

如有问题，请查看：
- [完整文档](./README.md)
- [快速指南](./QUICKSTART.md)
- [架构说明](./ARCHITECTURE.md)
