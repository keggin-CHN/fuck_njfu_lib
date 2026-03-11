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
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_DIR="$APP_DIR/venv"
SERVICE_NAME="njfu-reserve-api"

# --- 1. 系统更新 + 基础软件包 ---
echo ""
echo "[1/6] 安装系统依赖..."
sudo apt-get update -qq
sudo apt-get install -y -qq software-properties-common curl wget git

# --- 2. 安装 Python (通过 deadsnakes PPA) ---
echo ""
echo "[2/6] 安装 Python ${PYTHON_VERSION}..."
if ! command -v python${PYTHON_VERSION} &> /dev/null; then
    sudo add-apt-repository -y ppa:deadsnakes/ppa
    sudo apt-get update -qq
    sudo apt-get install -y -qq python${PYTHON_VERSION} python${PYTHON_VERSION}-venv python${PYTHON_VERSION}-dev
    echo "  Python ${PYTHON_VERSION} 安装完成"
else
    echo "  Python ${PYTHON_VERSION} 已存在，跳过"
fi

# --- 3. 配置 pip 清华源 ---
echo ""
echo "[3/6] 配置 pip 清华镜像源..."
mkdir -p ~/.pip
cat > ~/.pip/pip.conf << 'EOF'
[global]
index-url = https://pypi.tuna.tsinghua.edu.cn/simple
trusted-host = pypi.tuna.tsinghua.edu.cn
EOF
echo "  清华源已配置"

# --- 4. 创建虚拟环境 ---
echo ""
echo "[4/6] 创建 Python 虚拟环境..."
if [ ! -d "$VENV_DIR" ]; then
    python${PYTHON_VERSION} -m venv "$VENV_DIR"
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
echo "API 地址: http://$(hostname -I | awk '{print $1}'):8000"
echo "API Key:  $API_KEY"
echo "API 文档: http://$(hostname -I | awk '{print $1}'):8000/docs"
echo ""

# --- 询问是否创建 systemd 服务 ---
read -p "是否创建 systemd 服务以开机自启？[y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    sudo tee /etc/systemd/system/${SERVICE_NAME}.service > /dev/null << EOF
[Unit]
Description=NJFU Library Reserve API
After=network.target

[Service]
Type=simple
User=$(whoami)
WorkingDirectory=$APP_DIR
ExecStart=$VENV_DIR/bin/python $APP_DIR/main.py
Restart=always
RestartSec=5
Environment=API_KEY=$API_KEY

[Install]
WantedBy=multi-user.target
EOF
    sudo systemctl daemon-reload
    sudo systemctl enable ${SERVICE_NAME}
    sudo systemctl start ${SERVICE_NAME}
    echo ""
    echo "systemd 服务已创建并启动"
    echo "  查看状态: sudo systemctl status ${SERVICE_NAME}"
    echo "  查看日志: sudo journalctl -u ${SERVICE_NAME} -f"
fi
