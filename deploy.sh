#!/bin/bash
set -euo pipefail

GITHUB_REPO="https://github.com/keggin-CHN/fuck_njfu_lib.git"
INSTALL_DIR="/opt/fuck_njfu_lib"
SERVICE_NAME="fuck_njfu_lib"
BACKEND_DIR="backend"
FRONTEND_DIR="frontend"
GO_VERSION="1.24.9"
NODE_VERSION="18"
BRANCH="main"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

print_info() {
  echo -e "\e[34m[INFO]\e[0m $1"
}

print_success() {
  echo -e "\e[32m[SUCCESS]\e[0m $1"
}

print_error() {
  echo -e "\e[31m[ERROR]\e[0m $1" >&2
}

ensure_root() {
  if [[ "$(id -u)" -ne 0 ]]; then
      print_error "此脚本需要 root 权限，请使用 sudo 运行"
      exit 1
  fi
  if [[ -z "${SUDO_USER:-}" ]]; then
      print_error "请勿直接使用 root 用户执行脚本，请切换到普通用户后使用 sudo"
      exit 1
  fi
}

ensure_packages() {
  print_info "安装基础依赖 (curl git build-essential unzip)..."
  apt-get update -y >/dev/null
  apt-get install -y curl git build-essential unzip >/dev/null
}

ensure_go() {
  if command -v go >/dev/null 2>&1; then
    local current
    current=$(go version | awk '{print $3}' | sed 's/go//')
    if [[ "$current" == ${GO_VERSION}* ]]; then
      print_info "检测到 Go ${current}, 跳过安装"
      return
    fi
  fi
  print_info "安装 Go ${GO_VERSION}..."
  curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz" -o /tmp/go.tar.gz
  rm -rf /usr/local/go
  tar -C /usr/local -xzf /tmp/go.tar.gz
  rm -f /tmp/go.tar.gz
  if ! grep -q '/usr/local/go/bin' /etc/profile.d/go.sh 2>/dev/null; then
    echo 'export PATH=/usr/local/go/bin:$PATH' > /etc/profile.d/go.sh
  fi
  export PATH=/usr/local/go/bin:$PATH
}

ensure_node() {
  if command -v node >/dev/null 2>&1; then
    local current
    current=$(node -v | tr -d 'v')
    if [[ ${current%%.*} -ge ${NODE_VERSION} ]]; then
      print_info "检测到 Node.js v${current}, 跳过安装"
      return
    fi
  fi
  print_info "安装 Node.js ${NODE_VERSION}..."
  curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | bash - >/dev/null
  apt-get install -y nodejs >/dev/null
}

clone_or_update_repo() {
  if [[ -d "${INSTALL_DIR}/.git" ]]; then
    print_info "检测到已有仓库，拉取最新代码..."
    git -C "${INSTALL_DIR}" fetch --all
    git -C "${INSTALL_DIR}" reset --hard "origin/${BRANCH}"
  else
    print_info "克隆项目到 ${INSTALL_DIR}"
    rm -rf "${INSTALL_DIR}"
    git clone --branch "${BRANCH}" "${GITHUB_REPO}" "${INSTALL_DIR}"
  fi
}

build_frontend() {
  print_info "构建前端..."
  pushd "${INSTALL_DIR}/${FRONTEND_DIR}" >/dev/null
  npm install >/dev/null
  npm run build >/dev/null
  popd >/dev/null

  rm -rf "${INSTALL_DIR}/${BACKEND_DIR}/public"
  mkdir -p "${INSTALL_DIR}/${BACKEND_DIR}/public"
  cp -r "${INSTALL_DIR}/${FRONTEND_DIR}/dist"/* "${INSTALL_DIR}/${BACKEND_DIR}/public/"
}

build_backend() {
  print_info "编译后端..."
  export PATH=/usr/local/go/bin:$PATH
  pushd "${INSTALL_DIR}/${BACKEND_DIR}" >/dev/null
  go mod tidy >/dev/null
  go build -o fucknjfu-server ./cmd/server
  mkdir -p data
  popd >/dev/null
}

configure_service() {
  print_info "配置 systemd 服务..."
  cat > "${SERVICE_FILE}" <<SERVICE
[Unit]
Description=Fuck NJFU Library Go Service
After=network.target

[Service]
Type=simple
User=${SUDO_USER}
WorkingDirectory=${INSTALL_DIR}/${BACKEND_DIR}
ExecStart=${INSTALL_DIR}/${BACKEND_DIR}/fucknjfu-server
Environment=APP_PORT=8080
Environment=DATABASE_PATH=${INSTALL_DIR}/${BACKEND_DIR}/data/app.db
Environment=FRONTEND_DIST=${INSTALL_DIR}/${BACKEND_DIR}/public
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
SERVICE

  systemctl daemon-reload
  systemctl enable "${SERVICE_NAME}" >/dev/null
  systemctl restart "${SERVICE_NAME}"
}

fix_permissions() {
  print_info "设置文件权限 (属主 ${SUDO_USER})..."
  chown -R "${SUDO_USER}:${SUDO_USER}" "${INSTALL_DIR}"
}

main() {
  ensure_root
  ensure_packages
  ensure_go
  ensure_node
  clone_or_update_repo
  build_frontend
  build_backend
  fix_permissions
  configure_service
  print_success "部署完成！服务已启动，使用 systemctl status ${SERVICE_NAME} 查看状态。"
}

main "$@"
