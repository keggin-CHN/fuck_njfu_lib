# 系统架构说明

本文档详细说明了Cloudflare版图书馆信息查询系统的架构设计。

## 整体架构

```
┌─────────────────┐
│   用户浏览器    │
└────────┬────────┘
         │
         ├──────────────────────────┐
         │                          │
         ▼                          ▼
┌─────────────────┐        ┌──────────────────┐
│ Cloudflare Pages│        │Cloudflare Worker │
│   (静态前端)     │        │   (API后端)      │
└─────────────────┘        └────────┬─────────┘
                                    │
                                    ├────────────────────┐
                                    │                    │
                                    ▼                    ▼
                          ┌───────────────┐    ┌──────────────┐
                          │ 统一认证系统   │    │ 图书馆系统   │
                          │    (CAS)      │    │  (座位预约)  │
                          └───────────────┘    └──────────────┘
```

## 技术栈

### 前端 (Cloudflare Pages)
- **HTML5 + CSS3**：页面结构和样式
- **Bootstrap 5**：响应式UI框架
- **原生JavaScript**：无需打包，直接运行
- **Bootstrap Icons**：图标库

### 后端 (Cloudflare Worker)
- **JavaScript (ES Modules)**：Worker运行时
- **Web Crypto API**：密码加密
- **Fetch API**：HTTP请求
- **无状态设计**：每个请求独立处理

## 模块说明

### Worker模块

#### 1. index.js - 主入口
- 路由分发
- 请求处理
- 认证器缓存管理
- 错误处理

#### 2. auth.js - 认证模块
- `LibraryAuthenticator`类：封装认证逻辑
- 两级认证流程：
  1. 统一认证系统（CAS）
  2. 图书馆系统认证
- 认证状态验证
- 公钥获取和密码加密

#### 3. traffic.js - 流量监控
- 页面爬取和解析
- 数据提取（在馆人数、总容量）
- 计算占用率

#### 4. seats.js - 座位查询
- 区域配置管理
- 座位数据获取
- 统计分析（总数、空闲、占用）
- 楼层汇总

#### 5. utils.js - 工具函数
- HTTP请求封装
- URL构建
- 加密函数（AES、RSA）
- 日期处理
- CORS处理

### Pages模块

#### 1. index.html - 首页
- 系统介绍
- 功能导航
- 使用说明

#### 2. traffic.html - 流量页面
- 实时数据展示
- 占用率可视化
- 刷新功能

#### 3. seats.html - 座位页面
- 日期选择（今天/明天）
- 楼层筛选
- 区域卡片展示
- 座位详情弹窗

#### 4. JavaScript文件
- `config.js`：API配置
- `traffic.js`：流量页面逻辑
- `seats.js`：座位页面逻辑

## 数据流程

### 流量查询流程

```
1. 用户访问 traffic.html
   ↓
2. JavaScript发起请求 /api/traffic
   ↓
3. Worker接收请求
   ↓
4. 检查认证缓存
   ├─ 有效 → 使用缓存
   └─ 无效 → 重新认证
   ↓
5. 使用认证凭证访问流量监控页面
   ↓
6. 解析HTML提取数据
   ↓
7. 计算占用率等指标
   ↓
8. 返回JSON数据
   ↓
9. 前端渲染显示
```

### 座位查询流程

```
1. 用户访问 seats.html
   ↓
2. JavaScript发起请求 /api/seats/summary
   ↓
3. Worker接收请求
   ↓
4. 检查认证缓存
   ├─ 有效 → 使用缓存
   └─ 无效 → 重新认证
   ↓
5. 循环查询所有区域
   ├─ 区域1 → API请求 → 解析数据
   ├─ 区域2 → API请求 → 解析数据
   └─ ...
   ↓
6. 汇总统计
   ├─ 按区域统计
   ├─ 按楼层汇总
   └─ 计算总计
   ↓
7. 返回JSON数据
   ↓
8. 前端渲染显示
```

### 认证流程

```
1. 创建认证器实例
   ↓
2. 第一级认证（CAS统一认证）
   ├─ 获取初始ticket
   ├─ 访问登录页面获取表单参数
   ├─ AES加密密码
   ├─ 提交登录表单
   ├─ 处理302重定向
   └─ 获取最终ticket
   ↓
3. 第二级认证（图书馆系统）
   ├─ 获取RSA公钥和nonce
   ├─ RSA加密密码
   ├─ 提交登录请求
   └─ 获取token和accNo
   ↓
4. 缓存认证状态（30分钟）
```

## 安全设计

### 1. 密码保护
- 使用Cloudflare Secrets存储
- 环境变量加密
- 不在代码中出现
- 传输时加密（AES-256、RSA）

### 2. 认证缓存
- 内存缓存，不持久化
- 30分钟自动过期
- 自动验证有效性
- 失效后自动重新认证

### 3. 请求安全
- HTTPS强制加密
- CORS配置
- 无状态设计
- 请求独立验证

### 4. 前端安全
- CSP头配置
- XSS防护
- CSRF防护
- 安全的HTTP头

## 性能优化

### 1. 认证缓存
- 避免重复认证
- 减少外部请求
- 提高响应速度

### 2. 边缘计算
- Cloudflare全球边缘节点
- 低延迟响应
- 自动负载均衡

### 3. 静态资源CDN
- Pages自动CDN加速
- 资源缓存
- 压缩传输

### 4. 并发控制
- 座位查询串行处理
- 避免请求过载
- 错误重试机制

## 可扩展性

### 1. 添加新API
在 `worker/src/index.js` 中添加新路由：
```javascript
if (path === '/api/new-endpoint') {
  // 处理逻辑
  return jsonResponse(data);
}
```

### 2. 添加新页面
1. 创建新HTML文件
2. 创建对应的JavaScript文件
3. 调用Worker API
4. 更新导航链接

### 3. 添加KV存储
在 `wrangler.toml` 中配置：
```toml
kv_namespaces = [
  { binding = "CACHE", id = "your-kv-namespace-id" }
]
```

在Worker中使用：
```javascript
await env.CACHE.put('key', 'value', { expirationTtl: 3600 });
const value = await env.CACHE.get('key');
```

### 4. 添加定时任务
在 `wrangler.toml` 中配置：
```toml
[triggers]
crons = ["0 */5 * * *"]  # 每5小时运行一次
```

在Worker中实现：
```javascript
export default {
  async scheduled(event, env, ctx) {
    // 定时任务逻辑
  }
}
```

## 限制和约束

### Worker限制
- CPU时间：10ms（免费）/ 50ms（付费）
- 内存：128MB
- 请求大小：100MB
- 响应大小：100MB

### Pages限制
- 文件大小：25MB单文件
- 项目大小：20,000文件
- 部署频率：500次/月（免费）

### 免费额度
- Worker：100,000请求/天
- Pages：无限制
- KV：100,000读取/天

## 监控和日志

### 1. 实时日志
```bash
wrangler tail
```

### 2. Dashboard分析
- 请求数统计
- 错误率
- CPU时间
- 带宽使用

### 3. 自定义日志
在Worker中使用 `console.log()`：
```javascript
console.log('认证成功', { username, timestamp });
```

## 故障恢复

### 1. 认证失败
- 自动重试（最多3次）
- 缓存失效自动重新认证
- 错误信息返回给前端

### 2. 网络错误
- 请求超时处理
- 错误捕获和日志
- 用户友好的错误提示

### 3. 数据异常
- 解析失败处理
- 默认值返回
- 异常日志记录

## 最佳实践

### 1. 开发
- 使用本地环境开发
- 配置 `.dev.vars` 进行测试
- 使用 `wrangler tail` 调试

### 2. 部署
- 先部署Worker，再部署Pages
- 测试所有API端点
- 检查前端API配置

### 3. 维护
- 定期更新依赖
- 监控请求量和错误率
- 定期备份代码

### 4. 安全
- 定期更换密码
- 不在公开仓库提交秘钥
- 使用 `.gitignore` 保护敏感文件

## 未来改进

### 1. 短期
- [ ] 添加请求速率限制
- [ ] 实现响应缓存
- [ ] 优化错误处理
- [ ] 添加更多统计指标

### 2. 中期
- [ ] 使用KV存储持久化缓存
- [ ] 添加定时任务自动采集
- [ ] 实现数据可视化图表
- [ ] 支持多用户查询

### 3. 长期
- [ ] 使用Durable Objects存储历史数据
- [ ] 实现WebSocket实时推送
- [ ] 添加数据分析功能
- [ ] 开发移动端APP

## 参考资料

- [Cloudflare Workers 文档](https://developers.cloudflare.com/workers/)
- [Cloudflare Pages 文档](https://developers.cloudflare.com/pages/)
- [Web Crypto API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Crypto_API)
- [Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
