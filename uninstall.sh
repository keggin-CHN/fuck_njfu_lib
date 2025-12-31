#!/bin/bash
# ===================================================
# 图书馆预约系统卸载脚本
# ===================================================
NEW_INSTALL_DIR="/opt/library-reservation"
NEW_SERVICE_NAME="library-reservation"
NEW_SERVICE_FILE="/etc/systemd/system/${NEW_SERVICE_NAME}.service"
OLD_INSTALL_DIR="/opt/fuck_njfu_lib"
OLD_SERVICE_NAME="fuck_njfu_lib"
OLD_SERVICE_FILE="/etc/systemd/system/${OLD_SERVICE_NAME}.service"
print_info() {
    echo -e "\e[34m[INFO]\e[0m $1"
}

print_success() {
    echo -e "\e[32m[SUCCESS]\e[0m $1"
}

print_error() {
    echo -e "\e[31m[ERROR]\e[0m $1" >&2
}

print_warning() {
    echo -e "\e[33m[WARNING]\e[0m $1"
}
uninstall_service() {
    local service_name=$1
    local service_file=$2
    local install_dir=$3
    
    print_info "检查服务: ${service_name}..."
    
    # 停止并禁用服务
    if systemctl is-active --quiet "${service_name}" 2>/dev/null; then
        print_info "正在停止服务 '${service_name}'..."
        systemctl stop "${service_name}"
    fi
    
    if systemctl is-enabled --quiet "${service_name}" 2>/dev/null; then
        print_info "正在禁用服务 '${service_name}'..."
        systemctl disable "${service_name}"
    fi
    
    # 删除 systemd 服务文件
    if [ -f "$service_file" ]; then
        print_info "正在删除 systemd 服务文件: ${service_file}"
        rm -f "$service_file"
        systemctl daemon-reload
    fi
    
    # 删除安装目录
    if [ -d "$install_dir" ]; then
        print_info "正在删除安装目录: ${install_dir}"
        rm -rf "$install_dir"
    fi
}

# --- 主逻辑 ---

# 1. 检查权限
if [ "$(id -u)" -ne 0 ]; then
    print_error "此脚本需要以 root 权限运行。请使用 'sudo bash uninstall.sh'。"
    exit 1
fi

echo ""
echo "=========================================="
echo "   图书馆预约系统卸载脚本"
echo "=========================================="
echo ""

# 2. 确认卸载
read -p "确定要卸载图书馆预约系统吗？这将删除所有数据和配置。[y/N]: " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    print_info "卸载已取消。"
    exit 0
fi

print_info "开始卸载图书馆预约系统..."

# 3. 卸载新版服务
if [ -f "$NEW_SERVICE_FILE" ] || [ -d "$NEW_INSTALL_DIR" ]; then
    print_info "检测到新版安装 (library-reservation)..."
    uninstall_service "$NEW_SERVICE_NAME" "$NEW_SERVICE_FILE" "$NEW_INSTALL_DIR"
fi

# 4. 卸载旧版服务（向后兼容）
if [ -f "$OLD_SERVICE_FILE" ] || [ -d "$OLD_INSTALL_DIR" ]; then
    print_info "检测到旧版安装 (fuck_njfu_lib)..."
    uninstall_service "$OLD_SERVICE_NAME" "$OLD_SERVICE_FILE" "$OLD_INSTALL_DIR"
fi

# 5. 询问是否卸载 WARP
echo ""
read -p "是否同时卸载 Cloudflare WARP？[y/N]: " uninstall_warp
if [[ "$uninstall_warp" =~ ^[Yy]$ ]]; then
    print_info "正在卸载 Cloudflare WARP..."
    
    # 断开 WARP 连接
    if command -v warp-cli &> /dev/null; then
        warp-cli disconnect 2>/dev/null || true
    fi
    
    # 卸载 WARP
    if command -v apt-get &> /dev/null; then
        apt-get remove -y cloudflare-warp 2>/dev/null || true
        apt-get autoremove -y 2>/dev/null || true
    elif command -v yum &> /dev/null; then
        yum remove -y cloudflare-warp 2>/dev/null || true
    fi
    
    print_success "Cloudflare WARP 已卸载。"
else
    print_info "保留 Cloudflare WARP。"
fi

# 6. 清理完成
echo ""
print_success "=========================================="
print_success "   卸载完成！"
print_success "=========================================="
echo ""
print_info "已删除的内容："
print_info "  - systemd 服务配置"
print_info "  - 应用程序目录和数据"
if [[ "$uninstall_warp" =~ ^[Yy]$ ]]; then
    print_info "  - Cloudflare WARP"
fi
echo ""
