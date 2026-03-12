#!/bin/bash
# ============================================================
#  综合启动脚本：Uptime Kuma + NJFU 图书馆预约 API
#  适用于 MCSManager 无交互容器环境
#  用法: chmod +x start.sh && ./start.sh
# ============================================================
set +e  # 单步错误不退出，让兜底逻辑处理

echo "=========================================="
echo "  综合部署启动"
echo "  1. Uptime Kuma (监控面板)"
echo "  2. NJFU 图书馆预约 API"
echo "=========================================="
echo "[$(date '+%H:%M:%S')] 脚本开始执行..."

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NPM_MIRROR="https://registry.npmmirror.com"
KUMA_PID=""

# ============================================================
# 工具函数
# ============================================================
find_node() {
    for p in "/usr/bin/node" "/usr/local/bin/node" "$(which node 2>/dev/null)"; do
        [ -x "$p" ] && echo "$p" && return 0
    done
    return 1
}

install_nodejs() {
    echo "[$(date '+%H:%M:%S')] 安装 Node.js..."
    apt-get update -qq
    apt-get install -y -qq nodejs npm build-essential 2>/dev/null || true
}

# ============================================================
# [1/2] Uptime Kuma
# ============================================================
echo ""
echo "=========================================="
echo "[1/2] 启动 Uptime Kuma"
echo "=========================================="

KUMA_DIR="$SCRIPT_DIR/uptime-kuma-master"

if [ ! -d "$KUMA_DIR" ]; then
    echo "[$(date '+%H:%M:%S')] ⚠ 未找到 uptime-kuma-master 目录，跳过 Uptime Kuma"
else
    # 确保 Node.js 可用
    NODE_CMD=$(find_node)
    if [ -z "$NODE_CMD" ]; then
        install_nodejs
        NODE_CMD=$(find_node)
        [ -z "$NODE_CMD" ] && NODE_CMD="node"
    fi
    NPM_CMD="$(dirname "$NODE_CMD")/npm"
    [ ! -x "$NPM_CMD" ] && NPM_CMD="npm"

    echo "[$(date '+%H:%M:%S')] Node.js: $($NODE_CMD -v 2>/dev/null)"

    cd "$KUMA_DIR"

    # 配置 npm 镜像
    $NPM_CMD config set registry "$NPM_MIRROR" 2>/dev/null || true

    # 安装依赖（如未安装）
    if [ ! -d "node_modules" ]; then
        echo "[$(date '+%H:%M:%S')] 安装 Uptime Kuma 依赖..."
        $NPM_CMD ci --registry "$NPM_MIRROR" 2>/dev/null || \
        $NPM_CMD install --registry "$NPM_MIRROR" || true
    else
        echo "[$(date '+%H:%M:%S')] node_modules 已存在，跳过安装"
    fi

    # 构建前端（如未构建）
    if [ ! -d "dist" ]; then
        echo "[$(date '+%H:%M:%S')] 构建 Uptime Kuma 前端..."
        $NPM_CMD run build --registry "$NPM_MIRROR" || true
    else
        echo "[$(date '+%H:%M:%S')] dist 已存在，跳过构建"
    fi

    mkdir -p ./data

    # 后台启动 Uptime Kuma（PORT 变量限制在子进程内，不污染后续环境）
    PORT=3000 NODE_ENV=production DATA_DIR="./data" \
    "$NODE_CMD" server/server.js > "$SCRIPT_DIR/uptime-kuma.log" 2>&1 &
    KUMA_PID=$!
    sleep 2

    if kill -0 "$KUMA_PID" 2>/dev/null; then
        echo "[$(date '+%H:%M:%S')] ✓ Uptime Kuma 已启动 (PID: $KUMA_PID, 端口: 3000)"
        echo "[$(date '+%H:%M:%S')]   日志: $SCRIPT_DIR/uptime-kuma.log"
    else
        echo "[$(date '+%H:%M:%S')] ✗ Uptime Kuma 启动失败，请查看: $SCRIPT_DIR/uptime-kuma.log"
        KUMA_PID=""
    fi

    cd "$SCRIPT_DIR"
fi

# ============================================================
# [2/2] NJFU 图书馆预约 API
# ============================================================
echo ""
echo "=========================================="
echo "[2/2] 部署 NJFU 图书馆预约 API"
echo "=========================================="

PYTHON_VERSION="3.12"

# 定位 main.py
if [ -f "$SCRIPT_DIR/server_api/main.py" ]; then
    APP_DIR="$SCRIPT_DIR/server_api"
elif [ -f "$SCRIPT_DIR/main.py" ]; then
    APP_DIR="$SCRIPT_DIR"
else
    echo "[$(date '+%H:%M:%S')] ✗ 找不到 main.py，请确认文件结构"
    [ -n "$KUMA_PID" ] && kill "$KUMA_PID" 2>/dev/null
    exit 1
fi
VENV_DIR="$APP_DIR/venv"
echo "[$(date '+%H:%M:%S')] APP_DIR: $APP_DIR"

# --- 系统依赖 ---
echo ""
echo "[$(date '+%H:%M:%S')] [1/5] 安装系统依赖..."
apt-get update -qq
apt-get install -y -qq software-properties-common curl wget git

# --- Python ---
echo ""
echo "[$(date '+%H:%M:%S')] [2/5] 检测 Python..."
if ! command -v python${PYTHON_VERSION} &>/dev/null; then
    apt-get install -y -qq python${PYTHON_VERSION} python${PYTHON_VERSION}-venv python${PYTHON_VERSION}-dev 2>/dev/null || true
fi

if command -v python${PYTHON_VERSION} &>/dev/null; then
    PYTHON_BIN="python${PYTHON_VERSION}"
elif command -v python3.11 &>/dev/null; then
    PYTHON_BIN="python3.11"
elif command -v python3.10 &>/dev/null; then
    PYTHON_BIN="python3.10"
elif command -v python3 &>/dev/null; then
    PYTHON_BIN="python3"
else
    echo "[$(date '+%H:%M:%S')] ✗ 找不到 Python 3"
    [ -n "$KUMA_PID" ] && kill "$KUMA_PID" 2>/dev/null
    exit 1
fi

PYTHON_VER_TAG=$($PYTHON_BIN -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
apt-get install -y -qq python${PYTHON_VER_TAG}-venv 2>/dev/null || true
echo "[$(date '+%H:%M:%S')] 使用 $PYTHON_BIN (${PYTHON_VER_TAG})"

# --- pip 镜像 ---
echo ""
echo "[$(date '+%H:%M:%S')] [3/5] 配置 pip 镜像源..."
if curl -sf --max-time 5 https://pypi.tuna.tsinghua.edu.cn/simple/pip/ >/dev/null 2>&1; then
    mkdir -p ~/.pip
    cat > ~/.pip/pip.conf << 'PIPEOF'
[global]
index-url = https://pypi.tuna.tsinghua.edu.cn/simple
trusted-host = pypi.tuna.tsinghua.edu.cn
PIPEOF
    echo "[$(date '+%H:%M:%S')] 清华源可达，已配置"
else
    rm -f ~/.pip/pip.conf
    echo "[$(date '+%H:%M:%S')] 清华源不可达，使用官方 PyPI"
fi

# --- 虚拟环境 ---
echo ""
echo "[$(date '+%H:%M:%S')] [4/5] 创建虚拟环境..."
if [ ! -f "$VENV_DIR/bin/activate" ]; then
    rm -rf "$VENV_DIR"
    $PYTHON_BIN -m venv "$VENV_DIR"
    echo "[$(date '+%H:%M:%S')] 虚拟环境创建于 $VENV_DIR"
else
    echo "[$(date '+%H:%M:%S')] 虚拟环境已存在"
fi

# --- 安装依赖 ---
echo ""
echo "[$(date '+%H:%M:%S')] [5/5] 安装 Python 依赖..."
source "$VENV_DIR/bin/activate"
pip install --upgrade pip -q
pip install -r "$APP_DIR/requirements.txt" -q
echo "[$(date '+%H:%M:%S')] 依赖安装完成"

# --- API Key ---
KEY_FILE="$APP_DIR/.api_key"
if [ ! -f "$KEY_FILE" ]; then
    API_KEY=$(python -c "import secrets; print(secrets.token_urlsafe(32))")
    echo "$API_KEY" > "$KEY_FILE"
    chmod 600 "$KEY_FILE"
    echo "[$(date '+%H:%M:%S')] 已生成 API Key: $API_KEY"
else
    API_KEY=$(cat "$KEY_FILE")
    echo "[$(date '+%H:%M:%S')] API Key 已存在: $API_KEY"
fi

# --- 汇总 ---
echo ""
echo "=========================================="
echo "  所有服务启动完毕"
echo "=========================================="
[ -n "$KUMA_PID" ] && echo "  Uptime Kuma : http://$(hostname -I | awk '{print $1}'):3000  (PID: $KUMA_PID)"
echo "  NJFU API    : http://$(hostname -I | awk '{print $1}'):21859"
echo "  API Key     : $API_KEY"
echo "  API 文档    : http://$(hostname -I | awk '{print $1}'):21859/docs"
echo "=========================================="
echo ""

# 退出时清理后台进程
cleanup() {
    echo ""
    echo "[$(date '+%H:%M:%S')] 收到停止信号，正在关闭所有服务..."
    [ -n "$KUMA_PID" ] && kill "$KUMA_PID" 2>/dev/null && echo "[$(date '+%H:%M:%S')] Uptime Kuma 已停止"
    exit 0
}
trap cleanup INT TERM

# 前台运行 NJFU API（MCSManager 靠此进程判断实例存活）
echo "[$(date '+%H:%M:%S')] 启动 NJFU API 服务器（前台）..."
"$VENV_DIR/bin/python" "$APP_DIR/main.py"

# API 进程退出后，一并关闭 Uptime Kuma
[ -n "$KUMA_PID" ] && kill "$KUMA_PID" 2>/dev/null
echo "[$(date '+%H:%M:%S')] 所有服务已退出"
