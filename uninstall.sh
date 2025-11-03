#!/bin/bash
# --- 配置信息 ---
INSTALL_DIR="/opt/fuck_njfu_lib"
SERVICE_NAME="fuck_njfu_lib"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

# --- 辅助函数 ---
print_info() {
    echo -e "\e[34m[INFO]\e[0m $1"
}

print_success() {
    echo -e "\e[32m[SUCCESS]\e[0m $1"
}

print_error() {
    echo -e "\e[31m[ERROR]\e[0m $1" >&2
}

# --- 主逻辑 ---

# 1. 检查权限
if [ "$(id -u)" -ne 0 ]; then
    print_error "此脚本需要以 root 权限运行。请使用 'sudo bash uninstall.sh'。"
    exit 1
fi

print_info "开始卸载 ${SERVICE_NAME} 服务..."

# 2. 停止并禁用 systemd 服务
if systemctl is-active --quiet "${SERVICE_NAME}"; then
    print_info "正在停止服务 '${SERVICE_NAME}'..."
    systemctl stop "${SERVICE_NAME}"
fi
if systemctl is-enabled --quiet "${SERVICE_NAME}"; then
    print_info "正在禁用服务 '${SERVICE_NAME}'..."
    systemctl disable "${SERVICE_NAME}"
fi

# 3. 删除 systemd 服务文件
if [ -f "$SERVICE_FILE" ]; then
    print_info "正在删除 systemd 服务文件: ${SERVICE_FILE}"
    rm -f "$SERVICE_FILE"
    print_info "正在重载 systemd 管理器配置..."
    systemctl daemon-reload
else
    print_info "服务文件不存在，跳过删除。"
fi

# 4. 删除安装目录
if [ -d "$INSTALL_DIR" ]; then
    print_info "正在删除安装目录: ${INSTALL_DIR}"
    rm -rf "$INSTALL_DIR"
else
    print_info "安装目录不存在，跳过删除。"
fi

print_success "服务 '${SERVICE_NAME}' 已成功卸载。"
print_info "所有相关文件（服务定义、安装目录）均已删除。"