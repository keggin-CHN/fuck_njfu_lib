#!/bin/bash

# ==============================================================================
# fuck_njfu_lib 一键部署脚本 (适用于 Debian/Ubuntu)
# ==============================================================================

# --- 配置信息 ---
GITHUB_REPO="https://github.com/keggin-CHN/fuck_njfu_lib.git"
INSTALL_DIR="/opt/fuck_njfu_lib"
SERVICE_NAME="fuck_njfu_lib"
APP_DIR_NAME="backend"
APP_FILE="app.py"
VENV_DIR=".venv"
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

print_info "正在激活虚拟环境并安装项目依赖..."
source "${VENV_DIR}/bin/activate"
pip install --upgrade pip &>/dev/null
pip install -r "${APP_DIR_NAME}/requirements.txt" || print_error "安装 Python 依赖失败。"
deactivate

# 5. 创建 systemd 服务
print_info "正在创建 systemd 服务..."
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
APP_DIR="${INSTALL_DIR}/${APP_DIR_NAME}"
PYTHON_EXEC="${INSTALL_DIR}/${VENV_DIR}/bin/python"
APP_ENTRY="${APP_DIR}/${APP_FILE}"
LOG_FILE="${INSTALL_DIR}/service.log"
ERR_FILE="${INSTALL_DIR}/service.err"

# 使用 cat 和 EOF 创建服务文件
cat > "$SERVICE_FILE" << EOF
[Unit]
Description=fuck_njfu_lib Service
After=network.target

[Service]
User=$(logname)
Group=$(id -gn "$(logname)")
WorkingDirectory=${APP_DIR}
ExecStart=${PYTHON_EXEC} ${APP_ENTRY}
Restart=always
RestartSec=10
StandardOutput=file:${LOG_FILE}
StandardError=file:${ERR_FILE}

[Install]
WantedBy=multi-user.target
EOF

print_info "systemd 服务文件已成功创建于 ${SERVICE_FILE}"

# 6. 启动并启用服务
print_info "正在重载 systemd, 启用并启动服务..."
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"
systemctl start "${SERVICE_NAME}"

# 7. 显示最终状态
print_info "等待服务启动..."
sleep 3
systemctl status "${SERVICE_NAME}" --no-pager

echo
print_success "部署完成！"
print_info "服务 '${SERVICE_NAME}' 已启动并在后台运行。"
print_info "您可以使用 'sudo systemctl status ${SERVICE_NAME}' 来查看其状态。"
print_info "服务日志位于: ${LOG_FILE}"
print_info "错误日志位于: ${ERR_FILE}"