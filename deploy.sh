#!/bin/bash
set -e
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' 
DEFAULT_PORT=5000
DEFAULT_USE_WARP=true
APP_PORT=$DEFAULT_PORT
USE_WARP=$DEFAULT_USE_WARP
INSTALL_DIR="/opt/library-reservation"
REPO_URL="https://github.com/keggin-CHN/fuck_njfu_lib.git"
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}
log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}
log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}
log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "此脚本需要root权限运行，请使用 sudo bash deploy.sh"
        exit 1
    fi
}
interactive_config() {
    echo ""
    echo "============================================"
    echo "          部署配置选项"
    echo "============================================"
    echo ""
    read -p "请输入应用端口 [默认: $DEFAULT_PORT]: " input_port
    if [ -n "$input_port" ]; then
        if [[ "$input_port" =~ ^[0-9]+$ ]] && [ "$input_port" -ge 1 ] && [ "$input_port" -le 65535 ]; then
            APP_PORT=$input_port
        else
            log_warn "端口无效，使用默认端口 $DEFAULT_PORT"
            APP_PORT=$DEFAULT_PORT
        fi
    fi
    log_info "应用端口: $APP_PORT"
    echo ""
    echo "--------------------------------------------"
    echo "WARP IP切换功能说明:"
    echo "  启用后，所有预约请求将通过Cloudflare WARP代理发出"
    echo "  您的出口IP会变成Cloudflare的IP，而非服务器原始IP"
    echo "  这可以帮助绕过学校对服务器IP的限制或标记"
    echo "--------------------------------------------"
    echo ""
    read -p "是否启用 WARP IP切换功能？[Y/n] (默认: Y): " input_warp
    case "$input_warp" in
        [nN][oO]|[nN])
            USE_WARP=false
            log_info "WARP IP切换: 已禁用"
            ;;
        *)
            USE_WARP=true
            log_info "WARP IP切换: 已启用"
            ;;
    esac
    echo ""
    echo "============================================"
    echo "配置确认:"
    echo "  应用端口: $APP_PORT"
    echo "  WARP IP切换: $([ "$USE_WARP" = true ] && echo '启用' || echo '禁用')"
    echo "============================================"
    echo ""
    read -p "确认以上配置并开始部署？[Y/n]: " confirm
    case "$confirm" in
        [nN][oO]|[nN])
            log_info "部署已取消"
            exit 0
            ;;
        *)
            log_info "开始部署..."
            ;;
    esac
}
get_system_info() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        VERSION=$VERSION_ID
    else
        log_error "无法识别操作系统"
        exit 1
    fi
    log_info "检测到系统: $OS $VERSION"
}
install_base_deps() {
    log_step "安装基础依赖..."
    apt-get update
    apt-get install -y \
        curl \
        wget \
        gnupg \
        lsb-release \
        software-properties-common \
        python3 \
        python3-pip \
        python3-venv \
        git
    log_info "基础依赖安装完成"
}
install_warp() {
    if [ "$USE_WARP" = false ]; then
        log_info "跳过 WARP 安装（已禁用）"
        return 0
    fi
    log_step "安装 Cloudflare WARP..."
    if command -v warp-cli &> /dev/null; then
        log_info "WARP 已安装，跳过安装步骤"
        return 0
    fi
    curl -fsSL https://pkg.cloudflareclient.com/pubkey.gpg | gpg --yes --dearmor --output /usr/share/keyrings/cloudflare-warp-archive-keyring.gpg
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/cloudflare-warp-archive-keyring.gpg] https://pkg.cloudflareclient.com/ $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/cloudflare-client.list
    apt-get update
    apt-get install -y cloudflare-warp
    log_info "WARP 安装完成"
}
configure_warp() {
    if [ "$USE_WARP" = false ]; then
        log_info "跳过 WARP 配置（已禁用）"
        return 0
    fi
    log_step "配置 WARP..."
    sleep 2
    WARP_STATUS=$(warp-cli --accept-tos status 2>/dev/null || echo "unregistered")
    if echo "$WARP_STATUS" | grep -q "Registration Missing"; then
        log_info "注册 WARP..."
        warp-cli --accept-tos registration new
        sleep 2
    else
        log_info "WARP 已注册"
    fi
    log_info "设置 WARP 为 proxy 模式..."
    warp-cli --accept-tos mode proxy
    log_info "连接 WARP..."
    warp-cli --accept-tos connect
    sleep 3
    WARP_STATUS=$(warp-cli --accept-tos status)
    if echo "$WARP_STATUS" | grep -q "Connected"; then
        log_info "WARP 连接成功！"
        log_info "测试 WARP 代理..."
        PROXY_IP=$(curl -s --proxy socks5h://127.0.0.1:40000 https://ifconfig.me --max-time 10 || echo "获取失败")
        REAL_IP=$(curl -s https://ifconfig.me --max-time 10 || echo "获取失败")
        log_info "服务器真实 IP: $REAL_IP"
        log_info "WARP 代理 IP: $PROXY_IP"
        if [ "$PROXY_IP" != "$REAL_IP" ] && [ "$PROXY_IP" != "获取失败" ]; then
            log_info "✓ WARP 代理工作正常，IP 已变更"
        else
            log_warn "WARP 代理可能未生效，请手动检查"
        fi
    else
        log_warn "WARP 连接状态: $WARP_STATUS"
        log_warn "请手动执行 'warp-cli connect' 进行连接"
    fi
}
setup_warp_autostart() {
    if [ "$USE_WARP" = false ]; then
        log_info "跳过 WARP 自启配置（已禁用）"
        return 0
    fi
    log_step "配置 WARP 开机自启..."
    systemctl enable warp-svc 2>/dev/null || true
    cat > /etc/systemd/system/warp-connect.service << 'EOF'
[Unit]
Description=Connect WARP on boot
After=warp-svc.service network-online.target
Wants=network-online.target
[Service]
Type=oneshot
ExecStartPre=/bin/sleep 5
ExecStart=/usr/bin/warp-cli --accept-tos connect
RemainAfterExit=yes
[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable warp-connect.service
    log_info "WARP 开机自启配置完成"
}
clone_project() {
    log_step "获取项目代码..."
    if [ -d "$INSTALL_DIR" ]; then
        log_info "检测到已有安装目录: $INSTALL_DIR"
        read -p "是否删除并重新安装？[y/N]: " reinstall
        case "$reinstall" in
            [yY][eE][sS]|[yY])
                log_info "删除旧安装..."
                rm -rf "$INSTALL_DIR"
                ;;
            *)
                log_info "保留现有安装，尝试更新..."
                cd "$INSTALL_DIR"
                git pull origin main || log_warn "更新失败，继续使用现有代码"
                return 0
                ;;
        esac
    fi
    log_info "从 GitHub 克隆项目..."
    git clone "$REPO_URL" "$INSTALL_DIR"
    if [ ! -d "$INSTALL_DIR" ]; then
        log_error "项目克隆失败"
        exit 1
    fi
    log_info "项目代码获取完成"
}
deploy_app() {
    log_step "部署图书馆预约应用..."
    APP_DIR="$INSTALL_DIR/backend"
    if [ ! -d "$APP_DIR" ]; then
        log_error "未找到 backend 目录，项目结构可能有问题"
        exit 1
    fi
    cd "$APP_DIR"
    REAL_USER=${SUDO_USER:-$USER}
    log_info "创建 Python 虚拟环境..."
    python3 -m venv venv
    log_info "安装 Python 依赖..."
    source venv/bin/activate
    pip install --upgrade pip
    pip install -r requirements.txt
    log_info "初始化数据库..."
    python init_db.py || true
    log_info "设置文件权限..."
    chown -R "$REAL_USER:$REAL_USER" "$INSTALL_DIR"
    if [ -f "$APP_DIR/library.db" ]; then
        chmod 664 "$APP_DIR/library.db"
        log_info "数据库文件权限已设置"
    fi
    chmod 775 "$APP_DIR"
    log_info "应用部署完成"
}
create_service() {
    log_step "创建系统服务..."
    APP_DIR="$INSTALL_DIR/backend"
    REAL_USER=${SUDO_USER:-$USER}
    if [ "$USE_WARP" = true ]; then
        AFTER_DEPS="network.target warp-svc.service"
        WARP_ENV="Environment=\"USE_WARP_PROXY=true\"\nEnvironment=\"WARP_PROXY_HOST=127.0.0.1\"\nEnvironment=\"WARP_PROXY_PORT=40000\""
    else
        AFTER_DEPS="network.target"
        WARP_ENV="Environment=\"USE_WARP_PROXY=false\""
    fi
    cat > /etc/systemd/system/library-reservation.service << EOF
[Unit]
Description=Library Reservation System
After=$AFTER_DEPS
[Service]
Type=simple
User=$REAL_USER
WorkingDirectory=$APP_DIR
Environment="PATH=$APP_DIR/venv/bin"
$(echo -e $WARP_ENV)
ExecStart=$APP_DIR/venv/bin/gunicorn -w 2 -b 0.0.0.0:$APP_PORT --timeout 120 app:app
Restart=always
RestartSec=10
[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable library-reservation.service
    log_info "系统服务创建完成"
}
start_services() {
    log_step "启动服务..."
    systemctl start library-reservation.service
    sleep 3
    if systemctl is-active --quiet library-reservation.service; then
        log_info "✓ 图书馆预约服务启动成功"
    else
        log_error "服务启动失败，请检查日志: journalctl -u library-reservation.service"
    fi
}
show_info() {
    echo ""
    echo "============================================"
    echo -e "${GREEN}部署完成！${NC}"
    echo "============================================"
    echo ""
    echo "服务状态检查命令:"
    echo "  WARP 状态: warp-cli status"
    echo "  应用状态: systemctl status library-reservation"
    echo ""
    echo "服务管理命令:"
    echo "  启动服务: systemctl start library-reservation"
    echo "  停止服务: systemctl stop library-reservation"
    echo "  重启服务: systemctl restart library-reservation"
    echo "  查看日志: journalctl -u library-reservation -f"
    echo ""
    echo "WARP 管理命令:"
    echo "  连接 WARP: warp-cli connect"
    echo "  断开 WARP: warp-cli disconnect"
    echo "  查看状态: warp-cli status"
    echo ""
    echo "应用访问地址: http://服务器IP:$APP_PORT"
    echo ""
    if [ "$USE_WARP" = true ]; then
        echo "WARP IP切换: 已启用"
        echo "  所有预约请求的出口IP已变更为Cloudflare IP"
    else
        echo "WARP IP切换: 已禁用"
        echo "  预约请求将使用服务器原始IP"
    fi
    echo ""
    echo "安装目录: $INSTALL_DIR"
    echo ""
    echo "============================================"
}
main() {
    echo "============================================"
    echo "图书馆预约系统一键部署脚本"
    echo "============================================"
    echo ""
    check_root
    get_system_info
    if [ "$OS" != "ubuntu" ]; then
        log_warn "此脚本针对 Ubuntu 优化，其他系统可能需要手动调整"
        read -p "是否继续？(y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    interactive_config
    install_base_deps
    clone_project
    install_warp
    configure_warp
    setup_warp_autostart
    deploy_app
    create_service
    start_services
    show_info
}
main "$@"