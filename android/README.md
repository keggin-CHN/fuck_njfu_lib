# Android 客户端

该目录包含基于 WebView 的 Android 客户端工程，可将本项目快速封装成 APK。

## 功能概览

- 默认连接到 `http://10.0.2.2:5000`（Android 模拟器访问宿主机的回环地址）。
- 支持通过右上角菜单修改目标服务器地址，配置会保存在本地。
- 支持下拉刷新、加载进度指示以及常用的 WebView 调优。

## 先决条件

- JDK 17
- Android SDK (Compile SDK 34)
- Gradle 8.1+（如使用命令行构建）
- 推荐使用 Android Studio Ladybug 或更新版本

## 构建与运行

1. 在命令行或 Android Studio 中打开 `android/` 目录。
2. 如使用命令行构建，请创建 `local.properties` 指向 SDK：
   ```
   sdk.dir=/absolute/path/to/Android/Sdk
   ```
3. 执行构建命令：
   ```bash
   gradle assembleDebug    # 调试版 APK
   gradle assembleRelease  # 发行版 APK（需自行签名）
   ```
   使用 Android Studio 可以直接运行或执行 "Build > Build Bundle(s) / APK(s)"。
4. 构建产物位于 `app/build/outputs/apk/`。

## 自定义默认服务器

- 在构建前修改 `app/build.gradle` 中的 `DEFAULT_SERVER_URL`。
- 或者在应用运行后，点击右上角菜单中的「切换服务器地址」进行调整。

## 常见问题

- **白屏/连接失败**：请确认移动设备能够访问后端地址，并确保启用了 HTTP 明文流量或使用 HTTPS。
- **证书问题**：如需访问自签名 HTTPS 站点，请在系统层级导入证书或在服务器端配置受信任证书。

欢迎根据需要扩展原生功能，例如通知、后台任务等。