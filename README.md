# NJFU-lib-seat-reservation

在线 Demo: https://lib.keggin.me

维护信息：本项目运行于日本 Azure 微软云服务器，长期续费维护，欢迎自便使用；

浏览器手机版套壳下载： https://github.com/keggin-CHN/fuck_njfu_lib/releases/tag/mobile

如果本项目对你有帮助，请点个 Star ⭐。(づ｡◕‿‿◕｡)づ


BUG问题可提交issue，也可联系本人: admin@mail.keggin.me




## ⚠️ 使用声明

**仅用于学习与技术研究，禁止用于任何商业用途。**

## 主要功能

- 自动预约
  - 每日定时分时段为管理员与普通用户自动预约目标座位。
- 预防迟到（迟到保护）
  - 开始前约20分钟检测是否已签到；未签到则自动取消原预约并顺延约1小时重新预约。
- 自动寻座
  - 当目标座位在设定时段内已被占用且开启“自动寻座”时，系统提供同区域可用座位候选或自动分配完成预约。
- 消息推送
  - 支持企业微信机器人，Telegram Bot，钉钉机器人，飞书机器人；预约成功/失败、失败原因、设置变更等实时推送并展示详细时间段与座位信息。
- 实时流量监控
  - 24/7 每5分钟采集一次在馆人数，提供图表展示、对外API与CSV导出。
- 用户管理
  - 支持多用户管理，管理员可查看、操作用户与历史记录。
- Web界面
  - 提供登录、座位选择、状态查看、管理员面板等友好的网页界面。

## 部署指南

我们提供两种部署方式：**一键部署脚本（推荐）** 和 **手动部署**。

### 一键部署 (推荐)

此方法仅适用于所有基于 Debian 的 Linux 发行版 (如 Ubuntu, Debian)。请选择以下任一命令执行：

- **使用 `wget`**:

  ```bash
  wget -O deploy.sh https://raw.githubusercontent.com/keggin-CHN/fuck_njfu_lib/main/deploy.sh && sudo bash deploy.sh
  ```

- **使用 `curl`**:

  ```bash
  curl -o deploy.sh https://raw.githubusercontent.com/keggin-CHN/fuck_njfu_lib/main/deploy.sh && sudo bash deploy.sh
  ```

服务启动后，通过以下命令来管理：

- **查看状态**: `sudo systemctl status fuck_njfu_lib`
- **查看实时日志**: `sudo journalctl -u fuck_njfu_lib -f`
- **停止服务**: `sudo systemctl stop fuck_njfu_lib`
- **重启服务**: `sudo systemctl restart fuck_njfu_lib`

### 一键卸载

我们同样提供了一键卸载脚本，用于彻底清除本服务及其所有相关文件。

- **使用 `wget`**:

  ```bash
  wget -O uninstall.sh https://raw.githubusercontent.com/keggin-CHN/fuck_njfu_lib/main/uninstall.sh && sudo bash uninstall.sh
  ```

- **使用 `curl`**:

  ```bash
  curl -o uninstall.sh https://raw.githubusercontent.com/keggin-CHN/fuck_njfu_lib/main/uninstall.sh && sudo bash uninstall.sh
  ```

### 手动部署

如果您使用的是其他操作系统，或者想要更精细地控制部署过程，请遵循以下步骤。

#### 1. 克隆项目

```bash
git clone https://github.com/keggin-CHN/fuck_njfu_lib.git
cd fuck_njfu_lib
```

#### 2. 创建虚拟环境并安装依赖

```bash
cd backend
python3 -m venv .venv

# Linux / macOS
source .venv/bin/activate
# Windows
.venv\Scripts\activate

pip install -r requirements.txt
```


#### 3. 运行应用

可以通过两种方式运行后端应用：

- **开发模式 (用于测试)**:

  ```bash
  flask run --host=0.0.0.0 --port=5000
  ```

- **生产模式 (推荐)**:
  使用 `gunicorn` 作为 WSGI 服务器来运行应用，性能更佳。

  ```bash
  gunicorn --workers 3 --bind 0.0.0.0:5000 app:app
  ```

## 访问应用

部署成功后，通过浏览器访问 `http://<您的服务器IP>:5000` 即可打开 Web 界面。

## 致谢

本项目的灵感来源与参考如下：

- https://github.com/uglyBoy111/library-app-deploy
- https://github.com/kiusiudeng/NJFU-hacks
