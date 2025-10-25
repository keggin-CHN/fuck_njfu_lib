#!/bin/bash

# --- 配置信息 ---
GITHUB_REPO="https://github.com/keggin-CHN/fuck_njfu_lib.git"
INSTALL_DIR="/opt/fuck_njfu_lib"
SERVICE_NAME="fuck_njfu_lib"
APP_DIR_NAME="backend"
VENV_DIR=".venv"
# 关键修复：正确获取执行 sudo 的用户名
USER_WHO_RUNS=${SUDO_USER:-$(whoami)}
GROUP_WHO_RUNS=$(id -gn "$USER_WHO_RUNS")
# ----------------

# --- 辅助函数 ---
print_info() {
    echo -e "\e[34m[INFO]\e[0m $1"
}

print_success() {
    echo -e "\e[32m[SUCCESS]\e[0m $1"
}

print_error() {
    echo -e "\e[31m[ERROR]\e[0m $1" >&2
    exit 1
}

# --- 主逻辑 ---

# 1. 检查 root 权限
if [ "$(id -u)" -ne 0 ]; then
    print_error "此脚本需要以 root 权限运行。请使用 'sudo ./deploy.sh' 执行。"
fi

# 2. 安装系统依赖
print_info "正在更新软件包列表并安装依赖 (git, python3, python3-venv)..."
apt-get update -y &>/dev/null || print_error "更新软件包列表失败。"
apt-get install -y git python3 python3-venv || print_error "安装依赖失败。"

# 3. 克隆或更新项目代码
if [ -d "$INSTALL_DIR" ]; then
    print_info "项目目录 $INSTALL_DIR 已存在,正在拉取最新代码..."
    cd "$INSTALL_DIR" || print_error "无法进入目录 $INSTALL_DIR"
    git pull || print_error "从 Git 仓库拉取更新失败。"
else
    print_info "正在从 GitHub 克隆项目到 $INSTALL_DIR..."
    git clone "$GITHUB_REPO" "$INSTALL_DIR" || print_error "克隆 Git 仓库失败。"
fi

cd "$INSTALL_DIR" || print_error "无法进入项目目录 $INSTALL_DIR"

# 4. 创建虚拟环境并安装 Python 依赖
print_info "正在创建 Python 虚拟环境..."
python3 -m venv "$VENV_DIR" || print_error "创建虚拟环境失败。"

print_info "正在激活虚拟环境并安装项目依赖 (包括 gunicorn)..."
source "${VENV_DIR}/bin/activate"
pip install --upgrade pip &>/dev/null
# 确保从最新的 requirements.txt 安装
pip install -r "${APP_DIR_NAME}/requirements.txt" || print_error "安装 Python 依赖失败。"
deactivate

# 5. 修正文件权限
print_info "正在修正项目目录权限，所有者: ${USER_WHO_RUNS}:${GROUP_WHO_RUNS}"
chown -R "$USER_WHO_RUNS:$GROUP_WHO_RUNS" "$INSTALL_DIR"

# 6. 创建 systemd 服务
print_info "正在创建 systemd 服务 (使用 gunicorn)..."
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
APP_DIR="${INSTALL_DIR}/${APP_DIR_NAME}"
GUNICORN_EXEC="${INSTALL_DIR}/${VENV_DIR}/bin/gunicorn"

# 使用 cat 和 EOF 创建服务文件
cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Gunicorn instance to serve fuck_njfu_lib
After=network.target

[Service]
User=${USER_WHO_RUNS}
Group=${GROUP_WHO_RUNS}
WorkingDirectory=${APP_DIR}
# 关键修复：使用 gunicorn 启动
ExecStart=${GUNICORN_EXEC} --workers 3 --bind 0.0.0.0:5000 app:app
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

print_info "systemd 服务文件已成功创建于 ${SERVICE_FILE}"

# 7. 启动并启用服务
print_info "正在重载 systemd, 启用并启动服务..."
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}" # 使用 restart 确保应用最新的配置

# 8. 显示最终状态
print_info "等待服务启动..."
sleep 3
systemctl status "${SERVICE_NAME}" --no-pager

echo
if systemctl is-active --quiet "${SERVICE_NAME}"; then
    print_success "部署成功！"
    print_info "服务 '${SERVICE_NAME}' 已通过 gunicorn 启动并在后台运行。"
    print_info "您可以使用 'sudo systemctl status ${SERVICE_NAME}' 来查看其状态。"
    print_info "要查看实时日志，请运行 'sudo journalctl -u ${SERVICE_NAME} -f'"
else
    print_error "服务启动失败！请检查日志以获取详细信息。"
    print_info "运行 'sudo journalctl -u ${SERVICE_NAME}' 查看完整日志。"
fi
