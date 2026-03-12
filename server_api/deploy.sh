#!/bin/bash
# ============================================================
#  NJFU 图书馆预约 API — Ubuntu 一键部署脚本
#  用法: chmod +x deploy.sh && ./deploy.sh
# ============================================================
set -e

echo "========================================"
echo "  NJFU 图书馆预约 API 一键部署"
echo "========================================"

# --- 配置 ---
PYTHON_VERSION="3.12"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# 自动定位 main.py 所在目录（兼容 deploy.sh 与 main.py 不在同一目录的情况）
if [ -f "$SCRIPT_DIR/main.py" ]; then
    APP_DIR="$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/server_api/main.py" ]; then
    APP_DIR="$SCRIPT_DIR/server_api"
else
    echo "  错误：找不到 main.py，请确认文件结构正确"
    exit 1
fi
VENV_DIR="$APP_DIR/venv"
SERVICE_NAME="njfu-reserve-api"

# --- 1. 系统更新 + 基础软件包 ---
echo ""
echo "[1/6] 安装系统依赖..."
apt-get update -qq
apt-get install -y -qq software-properties-common curl wget git

# --- 2. 安装 Python (通过 deadsnakes PPA) ---
echo ""
echo "[2/6] 安装 Python ${PYTHON_VERSION}..."
if ! command -v python${PYTHON_VERSION} &> /dev/null; then
    apt-get install -y -qq python${PYTHON_VERSION} python${PYTHON_VERSION}-venv python${PYTHON_VERSION}-dev 2>/dev/null || true
fi

# 降级兜底：优先用 3.12，其次 3.11/3.10，最后用系统 python3
if command -v python${PYTHON_VERSION} &> /dev/null; then
    PYTHON_BIN="python${PYTHON_VERSION}"
    echo "  使用 Python ${PYTHON_VERSION}"
elif command -v python3.11 &> /dev/null; then
    PYTHON_BIN="python3.11"
    echo "  Python ${PYTHON_VERSION} 不可用，降级使用 python3.11"
elif command -v python3.10 &> /dev/null; then
    PYTHON_BIN="python3.10"
    echo "  Python ${PYTHON_VERSION} 不可用，降级使用 python3.10"
elif command -v python3 &> /dev/null; then
    PYTHON_BIN="python3"
    echo "  Python ${PYTHON_VERSION} 不可用，降级使用 $(python3 --version)"
else
    echo "  错误：找不到任何可用的 Python 3，请确认容器镜像包含 Python 3"
    exit 1
fi

# 确保对应版本的 venv 已安装
PYTHON_VER_TAG=$($PYTHON_BIN -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
apt-get install -y -qq python${PYTHON_VER_TAG}-venv 2>/dev/null || true

# --- 3. 配置 pip 清华源 ---
echo ""
echo "[3/6] 配置 pip 镜像源..."
# 测试清华源可达性，不可达则用官方源
if curl -sf --max-time 5 https://pypi.tuna.tsinghua.edu.cn/simple/pip/ > /dev/null 2>&1; then
    mkdir -p ~/.pip
    cat > ~/.pip/pip.conf << 'EOF'
[global]
index-url = https://pypi.tuna.tsinghua.edu.cn/simple
trusted-host = pypi.tuna.tsinghua.edu.cn
EOF
    echo "  清华源可达，已配置清华镜像"
else
    # 清华源不可达，移除旧配置，使用官方源
    rm -f ~/.pip/pip.conf
    echo "  清华源不可达，使用官方 PyPI"
fi

# --- 4. 创建虚拟环境 ---
echo ""
echo "[4/6] 创建 Python 虚拟环境..."
if [ ! -f "$VENV_DIR/bin/activate" ]; then
    # 目录不存在或不完整，清理后重建
    rm -rf "$VENV_DIR"
    $PYTHON_BIN -m venv "$VENV_DIR"
    echo "  虚拟环境创建于 $VENV_DIR"
else
    echo "  虚拟环境已存在，跳过"
fi

# --- 5. 安装依赖 ---
echo ""
echo "[5/6] 安装 Python 依赖..."
source "$VENV_DIR/bin/activate"
pip install --upgrade pip -q
pip install -r "$APP_DIR/requirements.txt" -q
echo "  依赖安装完成"

# --- 6. 生成 API Key (首次部署) ---
echo ""
echo "[6/6] 配置 API Key..."
KEY_FILE="$APP_DIR/.api_key"
if [ ! -f "$KEY_FILE" ]; then
    API_KEY=$(python -c "import secrets; print(secrets.token_urlsafe(32))")
    echo "$API_KEY" > "$KEY_FILE"
    chmod 600 "$KEY_FILE"
    echo "  已生成 API Key: $API_KEY"
    echo "  请将此 Key 填入 Android 客户端设置中"
else
    API_KEY=$(cat "$KEY_FILE")
    echo "  API Key 已存在: $API_KEY"
fi

# --- 7. 创建 systemd 服务 (可选) ---
echo ""
echo "========================================"
echo "  部署完成！"
echo "========================================"
echo ""
echo "启动方式:"
echo "  cd $APP_DIR"
echo "  source venv/bin/activate"
echo "  python main.py"
echo ""
echo "或后台运行:"
echo "  nohup $VENV_DIR/bin/python $APP_DIR/main.py > $APP_DIR/server.log 2>&1 &"
echo ""
echo "API 地址: http://$(hostname -I | awk '{print $1}'):21859"
echo "API Key:  $API_KEY"
echo "API 文档: http://$(hostname -I | awk '{print $1}'):21859/docs"
echo ""

# --- 启动服务（前台运行，由 MCSManager 管理进程生命周期）---
echo "========================================"
echo "  正在启动 API 服务器..."
echo "========================================"
exec "$VENV_DIR/bin/python" "$APP_DIR/main.py"
