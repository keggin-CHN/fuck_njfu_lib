# fuck_njfu_lib

南京林业大学的图书馆真的太垃圾了！——基于 Flask 的全栈图书馆预约与信息管理系统。

## 项目结构

```
backend/   # Flask 后端服务及调度任务
frontend/  # Jinja2 模板与静态资源
android/   # Android WebView 客户端（APK 打包）
```

## 后端快速启动

1. 创建虚拟环境并安装依赖：
   ```bash
   python -m venv .venv
   source .venv/bin/activate
   pip install -r backend/requirements.txt
   ```
2. 初始化数据库：
   ```bash
   python backend/init_db.py
   ```
3. 启动开发服务（默认监听 5000 端口）：
   ```bash
   python backend/app.py
   ```

部署生产环境可使用仓库根目录下的 `deploy.sh`。

## 构建可执行二进制

如需将后端打包成单个可执行文件，可按以下步骤操作：

1. 创建虚拟环境并安装依赖：
   ```bash
   python -m venv .venv
   source .venv/bin/activate
   pip install -r backend/requirements.txt
   pip install pyinstaller
   ```
2. 执行打包脚本：
   ```bash
   python build_executable.py
   ```

打包完成后会在 `dist/` 目录下生成二进制文件（Windows 上为 `fuck_njfu_lib.exe`，其他平台为 `fuck_njfu_lib`）。首次运行时代码会在可执行文件所在目录创建 `static/`、`logs/` 和 `instance/` 目录，以保存静态资源、日志文件和 SQLite 数据库。

## Android APK 打包

Android 客户端使用 WebView 将整个系统封装成原生应用，默认会连接到 `http://10.0.2.2:5000`（Android 模拟器访问本机服务使用的回环地址）。在应用内点击右上角菜单可切换到任意部署好的服务器地址。

### 构建步骤

1. 安装开发工具：
   - 建议使用 Android Studio Ladybug 或更新版本，或安装命令行版本的 Android SDK、`cmdline-tools` 和 JDK 17。
2. 进入 Android 项目目录：
   ```bash
   cd android
   ```
3. 如果使用命令行构建，请创建 `local.properties` 指向本机 SDK：
   ```
   sdk.dir=/path/to/Android/Sdk
   ```
4. 执行构建命令：
   ```bash
   # 调试包
   gradle assembleDebug

   # 或者使用发行包
   gradle assembleRelease
   ```
   如未安装全局 Gradle，可先运行 `gradle wrapper` 生成 `./gradlew`，之后使用 `./gradlew assembleDebug` / `./gradlew assembleRelease`。
   若使用 Android Studio，可直接导入 `android/` 目录并运行 `Build > Build Bundle(s) / APK(s)`。
5. 生成的 APK 位于：
   - `android/app/build/outputs/apk/debug/app-debug.apk`
   - `android/app/build/outputs/apk/release/app-release.apk`

> **提示**：发行包默认未签名，可使用 `apksigner` 或 Android Studio 的签名配置完成签名。

## 许可证

本项目基于 [MIT License](LICENSE) 发布。
