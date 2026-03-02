# Android App 版本 1.1.3 更新说明

## 版本信息
- **版本号**: 1.1.3
- **版本代码**: 10
- **更新日期**: 2026-03-01

## 主要更新

### 1. 可视化选座背景图支持
为可视化选座功能添加了真实的楼层平面图背景，显示桌子、楼道、卫生间等实际布局。

#### 技术实现
- **数据获取**: 从座位查询 API 的 `sysInfo.contentPath` 字段获取背景图 URL
- **异步加载**: 使用 AsyncTask 异步下载背景图，避免阻塞 UI 线程
- **统一缩放**: 背景图与座位点、桌子等元素共享同一变换矩阵，确保双指缩放时所有元素同步缩放
- **内存优化**: 使用 Bitmap 过滤和抗锯齿渲染提升显示质量

#### 修改文件
1. **SeatQuery.java**
   - 新增 `RoomBackground` 内部类存储背景图信息
   - `QueryResult` 添加 `background` 字段
   - 新增 `getRoomBackground()` 方法从 API 获取背景图数据

2. **SeatMapView.java**
   - 添加 `backgroundBitmap` 和 `bitmapPaint` 成员变量
   - 新增 `setBackground()` 方法接收背景图数据
   - 新增 `loadBackgroundImage()` 异步加载背景图
   - 在 `onDraw()` 中渲染背景图层（位于座位点和桌子之下）

3. **VisualSeatActivity.java**
   - 查询成功后调用 `seatMapView.setBackground(result.background)` 设置背景图

### 2. 双指缩放优化
确保所有可视化元素（背景图、座位点、桌子、房间边框）在双指缩放时保持同步：
- 所有绘制操作在同一个 `canvas.save()`/`canvas.restore()` 块内
- 共享相同的 `translate` 和 `scale` 变换
- 背景图使用虚拟画布坐标系 (1082x700) 进行缩放映射

### 3. 版本号更新
- `versionCode`: 9 → 10
- `versionName`: "1.1.1" → "1.1.3"

## 技术细节

### 背景图渲染流程
```
1. 用户选择区域和日期，点击查询
2. SeatQuery.querySeats() 调用 getRoomBackground() 获取背景图 URL
3. QueryResult 包含 background 对象返回给 Activity
4. Activity 调用 seatMapView.setBackground(background)
5. SeatMapView 异步下载背景图并缓存为 Bitmap
6. onDraw() 时在虚拟画布坐标系内绘制背景图
```

### 坐标系统
- **虚拟画布**: 1082x700 像素（与网页版一致）
- **座位坐标**: 百分比坐标 (0-100%)，转换为虚拟画布坐标
- **背景图**: 拉伸填充整个虚拟画布 (0,0) 到 (1082,700)
- **缩放中心**: 视图中心，所有元素统一缩放

### 性能考虑
- 背景图异步加载，不阻塞主线程
- 使用 Bitmap 过滤提升缩放质量
- 单次加载后缓存，切换座位时无需重新下载

## 兼容性
- 最低 Android 版本: API 21 (Android 5.0)
- 目标 Android 版本: API 34 (Android 14)
- 需要网络权限访问背景图资源

## 已知限制
- 背景图加载失败时会静默降级为纯色背景
- 不同楼层的背景图尺寸可能不同，统一拉伸到虚拟画布
- 首次加载背景图需要网络连接

## 测试建议
1. 测试所有 12 个区域的背景图加载
2. 验证双指缩放时背景图与座位点对齐
3. 测试网络异常时的降级行为
4. 验证内存占用是否合理（大背景图）