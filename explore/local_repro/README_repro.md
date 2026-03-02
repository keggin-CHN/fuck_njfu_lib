# 本地复现说明（二层A区可视化座位图）

## 已完成内容

1. 资源下载脚本：`../10_download_seat_assets.py`
2. 已抓取资源目录：`./assets/`
3. 已抓取数据文件：
   - `./data/seat_reserve_full.json`（接口原始返回）
   - `./data/seat_positions.json`（复现用简化数据）
   - `./data/seat_positions.js`（`file://` 直开优先加载）
   - `./data/map_meta.json` / `./data/map_meta.js`（背景图元信息）
   - `./data/room_bg.jpg`（背景底图，含桌子/卫生间/楼道等视觉元素）
4. 本地复现页面：`./replay_seat_map.html`

## 一键重新抓取（可选）

在仓库根目录执行：

```bash
python explore/10_download_seat_assets.py --room-id 100455344 --out explore/local_repro
```

## 启动本地预览

### 方式A：直接双击打开（已兼容 file://）
- 直接打开 `replay_seat_map.html` 即可显示座位图（页面会优先读取 `./data/seat_positions.js`）。
- 若背景图抓取成功，会自动叠加 `./data/room_bg.jpg`，可看到桌子/卫生间/楼道等底图信息。

### 方式B：本地静态服务（推荐调试）
在仓库根目录执行：

```bash
python -m http.server 8000 --directory explore/local_repro
```

然后浏览器访问：

```text
http://127.0.0.1:8000/replay_seat_map.html
```

## 复现逻辑

- 使用接口字段 `coordinate`（例如 `82.770270,89.178357`）作为原始坐标。
- 按线上实现直接采用 `left/top = coordinate%` 绝对定位渲染座位点。
- `occupied=true` 渲染为黄色；`occupied=false` 渲染为绿色。
- 默认高亮目标座位：`2F-A409`，可在页面顶部输入框切换并定位。
- 页面会优先加载背景图元信息（`./data/map_meta.js`），若 `bg.saved=true` 则在同一 `grid` 容器中叠加 `./data/room_bg.jpg`。
- 桌子/卫生间等设施不再做几何推断，直接以背景图为准。
- 顶部保留“显示楼道(推断)”用于辅助观察（蓝色带状蒙层，不是原始馆方图元）。
- 新增对齐微调参数（顶部工具栏）：
  - `X偏移(%)`、`Y偏移(%)`：整体平移座位层；
  - `X缩放`、`Y缩放`：按轴缩放座位坐标；
  - `中心锚点`：切换座位点锚点（左上角 / 中心点）；
  - 参数自动保存到浏览器 `localStorage`，刷新后会保留。

## 与线上页面的关系（研究结论）

- 线上页面核心是基于 `coordinate` 做绝对定位渲染（在已下载 chunk 中可看到 `left/top = coordinate.split(',') + '%'` 的逻辑）。
- 卫生间/楼道/桌椅等“非座位点信息”主要来自背景图配置接口 `ic-web/sysInfo(sysType=2, sysValue=<roomId>, sysKind=16)`。
- 本地复现页现已覆盖“背景底图 + 座位点位拓扑 + 座位状态着色 + 目标座位定位”四层核心视觉。
- 由于线上还包含缩放拖拽、状态筛选、多语言/时段文案等逻辑，本地版刻意只保留研究相关核心层。

## 限制说明

- 若某次抓取时背景图接口未返回有效 `content`，页面仍可显示座位点，但无法精确显示卫生间/楼道/桌椅底图。
- “显示楼道（推断）”是几何拓扑估计，不保证与馆方原始矢量图完全一致。
- 不同时间抓取的背景图可能有裁边或版本差异，少量偏差可通过顶部微调参数手动校准。