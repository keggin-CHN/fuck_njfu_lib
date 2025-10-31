#!/bin/bash

# 图书馆信息查询系统 - Cloudflare一键部署脚本
# 使用方法：./deploy.sh

set -e  # 遇到错误立即退出

echo "=================================="
echo "  图书馆信息查询系统"
echo "  Cloudflare 一键部署脚本"
echo "=================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查命令是否存在
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# 打印成功信息
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# 打印错误信息
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# 打印警告信息
print_warning() {
    echo -e "${YELLOW}! $1${NC}"
}

# 打印信息
print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# 步骤1: 检查前置条件
echo "步骤 1/5: 检查前置条件..."
echo ""

if ! command_exists node; then
    print_error "Node.js 未安装"
    echo "请访问 https://nodejs.org/ 下载安装 Node.js"
    exit 1
fi
print_success "Node.js 已安装: $(node --version)"

if ! command_exists npm; then
    print_error "npm 未安装"
    exit 1
fi
print_success "npm 已安装: $(npm --version)"

if ! command_exists wrangler; then
    print_warning "Wrangler 未安装，正在安装..."
    npm install -g wrangler
    print_success "Wrangler 安装完成"
else
    print_success "Wrangler 已安装: $(wrangler --version)"
fi

echo ""

# 步骤2: 登录Cloudflare
echo "步骤 2/5: 登录 Cloudflare..."
echo ""

if wrangler whoami >/dev/null 2>&1; then
    print_success "已登录 Cloudflare"
else
    print_info "需要登录 Cloudflare..."
    wrangler login
    print_success "登录成功"
fi

echo ""

# 步骤3: 部署Worker
echo "步骤 3/5: 部署 Worker..."
echo ""

cd worker

# 安装依赖
if [ ! -d "node_modules" ]; then
    print_info "安装 Worker 依赖..."
    npm install
    print_success "依赖安装完成"
fi

# 检查环境变量
print_info "检查环境变量..."
echo ""

SECRETS_SET=true

# 检查是否已设置秘钥
if ! wrangler secret list 2>/dev/null | grep -q "USERNAME"; then
    SECRETS_SET=false
fi

if [ "$SECRETS_SET" = false ]; then
    print_warning "需要设置环境变量（账号密码）"
    echo ""
    echo "请依次输入以下信息："
    echo ""
    
    echo -n "学号/用户名: "
    read -r USERNAME
    echo "$USERNAME" | wrangler secret put USERNAME
    
    echo -n "统一认证密码: "
    read -rs EDU_PASSWORD
    echo ""
    echo "$EDU_PASSWORD" | wrangler secret put EDU_PASSWORD
    
    echo -n "图书馆密码: "
    read -rs LIB_PASSWORD
    echo ""
    echo "$LIB_PASSWORD" | wrangler secret put LIB_PASSWORD
    
    print_success "环境变量设置完成"
else
    print_success "环境变量已存在"
fi

echo ""
print_info "部署 Worker..."
WORKER_OUTPUT=$(wrangler deploy 2>&1)
echo "$WORKER_OUTPUT"

# 提取Worker URL
WORKER_URL=$(echo "$WORKER_OUTPUT" | grep -o 'https://[^[:space:]]*workers.dev' | head -n 1)

if [ -z "$WORKER_URL" ]; then
    # 尝试从whoami获取
    ACCOUNT_SUBDOMAIN=$(wrangler whoami 2>/dev/null | grep "subdomain" | cut -d'"' -f4)
    if [ -n "$ACCOUNT_SUBDOMAIN" ]; then
        WORKER_URL="https://library-info-worker.${ACCOUNT_SUBDOMAIN}.workers.dev"
    else
        print_warning "无法自动获取 Worker URL"
        echo -n "请手动输入 Worker URL: "
        read -r WORKER_URL
    fi
fi

print_success "Worker 部署完成"
print_info "Worker URL: $WORKER_URL"

cd ..
echo ""

# 步骤4: 配置Pages
echo "步骤 4/5: 配置 Pages..."
echo ""

print_info "更新 API 配置..."

# 更新config.js
CONFIG_FILE="pages/assets/js/config.js"
sed -i.bak "s|const API_BASE_URL = '.*';|const API_BASE_URL = '${WORKER_URL}';|" "$CONFIG_FILE"
rm -f "$CONFIG_FILE.bak"

print_success "API 配置已更新"
echo ""

# 步骤5: 部署Pages
echo "步骤 5/5: 部署 Pages..."
echo ""

cd pages

print_info "部署 Pages..."
PAGES_OUTPUT=$(wrangler pages deploy . --project-name=library-info 2>&1)
echo "$PAGES_OUTPUT"

# 提取Pages URL
PAGES_URL=$(echo "$PAGES_OUTPUT" | grep -o 'https://[^[:space:]]*pages.dev' | head -n 1)

if [ -z "$PAGES_URL" ]; then
    PAGES_URL="https://library-info.pages.dev"
fi

print_success "Pages 部署完成"

cd ..
echo ""

# 完成
echo "=================================="
echo "  🎉 部署完成！"
echo "=================================="
echo ""
print_success "Worker URL: $WORKER_URL"
print_success "Pages URL:  $PAGES_URL"
echo ""
print_info "现在可以访问你的网站："
echo -e "${BLUE}${PAGES_URL}${NC}"
echo ""
print_info "测试API："
echo "curl ${WORKER_URL}/api/health"
echo ""
echo "=================================="
