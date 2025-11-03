GITHUB_REPO="https://github.com/keggin-CHN/fuck_njfu_lib.git"
INSTALL_DIR="/opt/fuck_njfu_lib"
SERVICE_NAME="fuck_njfu_lib"
APP_DIR_NAME="backend"
VENV_DIR=".venv"
APP_PORT=5000 # 定义端口变量
REAL_USER=${SUDO_USER}
REAL_GROUP=$(id -gn "${REAL_USER}")

print_info() {
    echo -e "\e[34m[INFO]\e[0m $1"
}

print_success() {
    echo -e "\e[32m[SUCCESS]\e[0m $1"
}

print_error() {
    echo -e "\e[31m[ERROR]\e[0m $1" >&2
}

is_port_in_use() {
    fuser -n tcp "$1" >/dev/null 2>&1
}

find_free_port() {
    for i in $(seq 1 50); do
        candidate=$(shuf -i 10000-60000 -n 1)
        if ! is_port_in_use "$candidate"; then
            echo "$candidate"
            return 0
        fi
    done
    echo ""
    return 1
}

if [ "$(id -u)" -ne 0 ]; then
    print_error "此脚本需要以 root 权限运行。请使用 'sudo bash deploy.sh'。"
    exit 1
fi
if [ -z "$SUDO_USER" ]; then
    print_error "请不要直接在 root shell 中运行此脚本。"
    print_error "请先切换回普通用户 (如 'keggin'), 然后再使用 'sudo bash deploy.sh' 执行。"
    exit 1
fi

print_info "正在更新软件包列表并安装依赖 (git, python3, python3-venv, psmisc)..."
apt-get update -y &>/dev/null || print_error "更新软件包列表失败。"
apt-get install -y git python3 python3-venv psmisc || print_error "安装依赖失败。"

print_info "请选择端口模式："
echo "  1) 清理并使用固定端口 5000"
echo "  2) 自动选择一个未被占用的随机端口"
read -p "请输入选择 (1/2) [默认: 1]: " PORT_MODE
PORT_MODE=${PORT_MODE:-1}

if [ "$PORT_MODE" = "1" ]; then
    APP_PORT=5000
    DO_CLEAN_PORT=1
    print_info "已选择模式 1：将清理并使用端口 5000。"
elif [ "$PORT_MODE" = "2" ]; then
    APP_PORT=$(find_free_port)
    if [ -z "$APP_PORT" ]; then
        print_error "未能找到可用端口，请稍后重试。"
        exit 1
    fi
    DO_CLEAN_PORT=0
    print_info "已选择模式 2：随机选择可用端口 ${APP_PORT}。"
else
    print_error "无效选择: ${PORT_MODE}"
    exit 1
fi
print_info "正在停止旧服务..."
systemctl stop "${SERVICE_NAME}" >/dev/null 2>&1 || true

if [ "${DO_CLEAN_PORT}" = "1" ]; then
    print_info "正在清理端口 ${APP_PORT}..."
    fuser -k -n tcp "${APP_PORT}" >/dev/null 2>&1 || true
    sleep 2 # 等待端口释放

    if fuser -n tcp "${APP_PORT}" >/dev/null 2>&1; then
        print_error "无法清理端口 ${APP_PORT}，仍有进程在占用它。"
        fuser -v -n tcp "${APP_PORT}"
        exit 1
    fi
    print_success "端口 ${APP_PORT} 已清理干净。"
else
    if fuser -n tcp "${APP_PORT}" >/dev/null 2>&1; then
        print_error "随机选择的端口 ${APP_PORT} 被占用，请重试。"
        exit 1
    fi
    print_success "随机端口 ${APP_PORT} 可用，无需清理。"
fi


if [ -d "$INSTALL_DIR" ]; then
    print_info "项目目录 $INSTALL_DIR 已存在,正在拉取最新代码..."
    cd "$INSTALL_DIR" || exit 1
    git pull || print_error "从 Git 仓库拉取更新失败。"
else
    print_info "正在从 GitHub 克隆项目到 $INSTALL_DIR..."
    git clone "$GITHUB_REPO" "$INSTALL_DIR" || print_error "克隆 Git 仓库失败。"
fi
cd "$INSTALL_DIR" || exit 1

print_info "正在创建 Python 虚拟环境..."
python3 -m venv "$VENV_DIR" || print_error "创建虚拟环境失败。"
source "${VENV_DIR}/bin/activate"
pip install --upgrade pip &>/dev/null
pip install -r "${APP_DIR_NAME}/requirements.txt" || print_error "安装 Python 依赖失败。"

print_info "正在初始化数据库..."
cd "${APP_DIR_NAME}" || exit 1
python init_db.py || print_error "数据库初始化失败。"
cd .. # 返回项目根目录
deactivate

print_info "正在修正项目目录权限，所有者: ${REAL_USER}:${REAL_GROUP}"
chown -R "${REAL_USER}:${REAL_GROUP}" "$INSTALL_DIR"

print_info "正在创建 systemd 服务 (使用 gunicorn)..."
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
APP_DIR="${INSTALL_DIR}/${APP_DIR_NAME}"
GUNICORN_EXEC="${INSTALL_DIR}/${VENV_DIR}/bin/gunicorn"

cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Gunicorn instance to serve fuck_njfu_lib
After=network.target

[Service]
User=${REAL_USER}
Group=${REAL_GROUP}
WorkingDirectory=${APP_DIR}
ExecStart=${GUNICORN_EXEC} --workers 3 --worker-class gevent --bind 0.0.0.0:${APP_PORT} app:app
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

print_info "正在重载 systemd, 启用并启动服务..."
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}"

print_info "等待服务启动..."
sleep 3
if systemctl is-active --quiet "${SERVICE_NAME}"; then
    print_success "部署成功！"
    print_info "服务 '${SERVICE_NAME}' 已通过 gunicorn 启动并在后台运行。"
    print_info "您可以使用 'sudo systemctl status ${SERVICE_NAME}' 来查看其状态。"
    print_info "要查看实时日志，请运行 'sudo journalctl -u ${SERVICE_NAME} -f'"
else
    print_error "服务启动失败！请检查日志以获取详细信息。"
    print_info "运行 'sudo journalctl -u ${SERVICE_NAME}' 查看完整日志。"
    exit 1
fi
