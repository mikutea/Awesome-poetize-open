#!/bin/bash
## 作者: LeapYa
## 修改时间: 2025-07-01
## 描述: Poetize 博客系统自动迁移脚本
## 版本: 0.1.0

# 定义颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 函数
info() { echo -e "${BLUE}[信息]${NC} $1"; }
success() { echo -e "${GREEN}[成功]${NC} $1"; }
error() { echo -e "${RED}[失败]${NC} $1"; }
warning() { echo -e "${YELLOW}[警告]${NC} $1"; }

# 全局变量
TARGET_IP=""
TARGET_USER=""
TARGET_PASSWORD=""
DB_ROOT_PASSWORD=""
DB_USER_PASSWORD=""
BACKUP_DIR=""
IS_CHINA_ENV=false
CURRENT_DIR=$(dirname "$(pwd)")
# 检查必要工具
check_prerequisites() {
    info "检查迁移前置条件..."
    CURRENT_DIR=$(dirname "$(pwd)")
    # 检查必要命令
    local missing_tools=()
    
    if ! command -v sshpass &> /dev/null; then
        missing_tools+=("sshpass")
    fi
    
    if ! command -v ssh &> /dev/null; then
        missing_tools+=("ssh")
    fi
    
    if ! command -v scp &> /dev/null; then
        missing_tools+=("scp")
    fi
    
    if [ ${#missing_tools[@]} -gt 0 ]; then
        warning "缺少必要工具: ${missing_tools[*]}"
        info "正在尝试自动安装缺少的工具..."
        
        # 自动安装缺少的工具
        if command -v apt-get &> /dev/null; then
            info "检测到Ubuntu/Debian系统，使用apt-get安装..."
            sudo apt-get update -qq
            sudo apt-get install -y sshpass openssh-client
        elif command -v yum &> /dev/null; then
            info "检测到CentOS/RHEL系统，使用yum安装..."
            sudo yum install -y sshpass openssh-clients
        elif command -v dnf &> /dev/null; then
            info "检测到Fedora系统，使用dnf安装..."
            sudo dnf install -y sshpass openssh-clients
        elif command -v pacman &> /dev/null; then
            info "检测到Arch Linux系统，使用pacman安装..."
            sudo pacman -S --noconfirm openssh sshpass
        elif command -v zypper &> /dev/null; then
            info "检测到openSUSE系统，使用zypper安装..."
            sudo zypper install -y openssh sshpass
        elif command -v apk &> /dev/null; then
            info "检测到Alpine Linux系统，使用apk安装..."
            sudo apk add --update-cache openssh sshpass
        else
            error "无法识别的包管理器，请手动安装以下工具: ${missing_tools[*]}"
            info "安装命令参考:"
            info "Ubuntu/Debian: sudo apt-get install sshpass openssh-client"
            info "CentOS/RHEL: sudo yum install sshpass openssh-clients"
            info "Fedora: sudo dnf install sshpass openssh-clients"
            info "Arch Linux: sudo pacman -S openssh sshpass"
            info "openSUSE: sudo zypper install openssh sshpass"
            exit 1
        fi
        
        # 重新检查工具是否安装成功
        local still_missing=()
        for tool in "${missing_tools[@]}"; do
            if ! command -v "$tool" &> /dev/null; then
                still_missing+=("$tool")
            fi
        done
        
        if [ ${#still_missing[@]} -gt 0 ]; then
            error "以下工具安装失败: ${still_missing[*]}"
            error "请手动安装这些工具后重新运行脚本"
            exit 1
        else
            success "所有必要工具安装成功"
        fi
    fi
    
    # 检查数据库凭据文件
    if [ ! -f ".config/db_credentials.txt" ]; then
        error "数据库凭据文件 .config/db_credentials.txt 不存在"
        error "请确保在项目根目录运行此脚本，并且数据库已正确配置"
        exit 1
    fi
    
    # 检查py/data目录
    if [ ! -d "py/data" ]; then
        error "配置目录 py/data 不存在"
        exit 1
    fi
    
    # 检查docker-compose是否运行
    if ! docker ps | grep -q "poetize-mariadb"; then
        error "数据库容器未运行，请先启动服务: docker-compose up -d"
        exit 1
    fi
    
    success "前置条件检查通过"
}

# 获取用户输入
get_user_input() {
    info "请输入目标服务器信息:"
    
    # 获取目标服务器IP
    while [ -z "$TARGET_IP" ]; do
        read -p "目标服务器IP地址: " TARGET_IP
        if [ -z "$TARGET_IP" ]; then
            warning "IP地址不能为空，请重新输入"
        fi
    done
    
    # 获取目标服务器用户名
    read -p "目标服务器用户名 (默认: root): " TARGET_USER
    if [ -z "$TARGET_USER" ]; then
        TARGET_USER="root"
    fi
    
    # 获取目标服务器密码
    while [ -z "$TARGET_PASSWORD" ]; do
        read -s -p "目标服务器密码: " TARGET_PASSWORD
        echo
        if [ -z "$TARGET_PASSWORD" ]; then
            warning "密码不能为空，请重新输入"
        fi
    done
    
    success "目标服务器信息获取完成"
    info "目标服务器: $TARGET_USER@$TARGET_IP"
}

# 读取数据库凭据
read_db_credentials() {
    info "读取数据库凭据..."
    
    DB_ROOT_PASSWORD=$(grep "数据库ROOT密码:" .config/db_credentials.txt | cut -d':' -f2 | tr -d ' ')
    DB_USER_PASSWORD=$(grep "数据库poetize用户密码:" .config/db_credentials.txt | cut -d':' -f2 | tr -d ' ')
    
    if [ -z "$DB_ROOT_PASSWORD" ] || [ -z "$DB_USER_PASSWORD" ]; then
        error "无法读取数据库密码"
        exit 1
    fi
    
    success "数据库凭据读取成功"
}

# 备份数据库
backup_database() {
    info "开始备份数据库..."
    
    # 创建临时备份目录
    BACKUP_DIR="migration_temp_$(date +%Y%m%d_%H%M%S)"
    sudo mkdir -p "$BACKUP_DIR"
    
    # 备份数据库
    info "正在导出数据库到 $BACKUP_DIR/poetry.sql..."
    
    sudo docker exec poetize-mariadb mariadb-dump \
        -u root \
        -p"$DB_ROOT_PASSWORD" \
        --single-transaction \
        --routines \
        --triggers \
        --databases poetize > "$BACKUP_DIR/poetry.sql"
    
    if [ $? -eq 0 ]; then
        success "数据库备份成功: $BACKUP_DIR/poetry.sql"
    else
        error "数据库备份失败"
        exit 1
    fi
}

# 测试SSH连接
test_ssh_connection() {
    info "测试SSH连接到目标服务器..."
    
    # 测试SSH连接
    if sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$TARGET_USER@$TARGET_IP" "echo 'SSH连接测试成功'" &>/dev/null; then
        success "SSH连接测试成功"
    else
        error "SSH连接失败，请检查IP地址、用户名和密码"
        exit 1
    fi
    
    # 检查sudo权限（如果不是root用户）
    if [ "$TARGET_USER" != "root" ]; then
        info "检查sudo权限..."
        if sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "echo '$TARGET_PASSWORD' | sudo -S echo 'sudo权限检查成功'" &>/dev/null; then
            success "sudo权限检查通过"
        else
            error "用户 $TARGET_USER 没有sudo权限，请使用root用户或具有sudo权限的用户"
            exit 1
        fi
    fi
}

# 检测目标服务器环境
detect_target_environment() {
    info "检测目标服务器网络环境..."
    
    # 检测是否能访问Google（判断是否为国内环境）
    if sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "curl -s --connect-timeout 5 --max-time 10 https://www.google.com >/dev/null 2>&1"; then
        IS_CHINA_ENV=false
        success "检测到国外网络环境，将使用GitHub仓库"
    else
        IS_CHINA_ENV=true
        success "检测到国内网络环境，将使用Gitee仓库"
    fi
}

# 在目标服务器上拉取代码
pull_code_on_target() {
    info "在目标服务器上拉取项目代码..."
    
    local git_url
    if [ "$IS_CHINA_ENV" = true ]; then
        git_url="https://gitee.com/leapya/poetize.git"
    else
        git_url="https://github.com/LeapYa/Awesome-poetize-open.git"
    fi
    
    info "使用仓库地址: $git_url"

    # 在目标服务器上执行命令
    local ssh_cmd="
        # 安装必要工具
        if command -v apt-get &>/dev/null; then
            sudo apt-get update && sudo apt-get install -y git curl
        elif command -v yum &>/dev/null; then
            sudo yum install -y git curl
        elif command -v dnf &>/dev/null; then
            sudo dnf install -y git curl
        elif command -v zypper &>/dev/null; then
            sudo zypper install -y git curl
        elif command -v pacman &>/dev/null; then
            sudo pacman -S --needed git curl
        elif command -v apk &>/dev/null; then
            sudo apk add --update-cache git curl
        else
            echo 'ERROR: 不支持的包管理器，请手动安装git和curl'
            exit 1
        fi

        # 进入项目目录
        if [ -d $CURRENT_DIR ] then
            cd $CURRENT_DIR
        else
            mkdir -p $CURRENT_DIR
            cd $CURRENT_DIR
        fi
        
        # 如果目录已存在，先备份
        if [ -d 'Awesome-poetize-open' ]; then
            mv Awesome-poetize-open Awesome-poetize-open.backup.\$(date +%Y%m%d_%H%M%S)
        fi
        
        # 克隆项目
        git clone $git_url
        
        # 如果是Gitee仓库，重命名目录
        if [ -d 'poetize' ]; then
            mv poetize Awesome-poetize-open
        fi
        
        # 检查是否成功
        if [ -d 'Awesome-poetize-open' ] && [ -f 'Awesome-poetize-open/deploy.sh' ]; then
            echo 'SUCCESS: 项目代码拉取成功'
        else
            echo 'ERROR: 项目代码拉取失败'
            exit 1
        fi
    "
    
    if [ "$TARGET_USER" = "root" ]; then
        sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "$ssh_cmd"
    else
        sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "echo '$TARGET_PASSWORD' | sudo -S bash -c \"$ssh_cmd\""
    fi
    
    if [ $? -eq 0 ]; then
        success "项目代码拉取成功"
    else
        error "项目代码拉取失败"
        exit 1
    fi
}

# 传输文件到目标服务器
transfer_files() {
    info "传输备份文件到目标服务器..."
    
    local target_path
    target_path="$CURRENT_DIR/Awesome-poetize-open"
    
    # 传输数据库备份文件
    info "传输数据库备份文件..."
    sshpass -p "$TARGET_PASSWORD" scp -o StrictHostKeyChecking=no "$BACKUP_DIR/poetry.sql" "$TARGET_USER@$TARGET_IP:$target_path/poetize-server/sql/poetry.sql"
    
    if [ $? -eq 0 ]; then
        success "数据库备份文件传输成功"
    else
        error "数据库备份文件传输失败"
        exit 1
    fi
    
    # 传输数据库凭据文件
    info "传输数据库凭据文件..."
    if [ "$TARGET_USER" = "root" ]; then
        sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "mkdir -p $target_path/.config"
    else
        sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "echo '$TARGET_PASSWORD' | sudo -S mkdir -p $target_path/.config"
    fi
    sshpass -p "$TARGET_PASSWORD" scp -o StrictHostKeyChecking=no ".config/db_credentials.txt" "$TARGET_USER@$TARGET_IP:$target_path/.config/db_credentials.txt"
    
    if [ $? -eq 0 ]; then
        success "数据库凭据文件传输成功"
    else
        error "数据库凭据文件传输失败"
        exit 1
    fi
    
    # 传输py/data配置目录
    info "传输Python配置文件..."
    sshpass -p "$TARGET_PASSWORD" scp -r -o StrictHostKeyChecking=no "py/data" "$TARGET_USER@$TARGET_IP:$target_path/py/"
    
    if [ $? -eq 0 ]; then
        success "Python配置文件传输成功"
        
        # 如果不是root用户，需要确保文件权限正确
        if [ "$TARGET_USER" != "root" ]; then
            info "设置文件权限..."
            sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "echo '$TARGET_PASSWORD' | sudo -S chown -R $TARGET_USER:$TARGET_USER $target_path"
            if [ $? -eq 0 ]; then
                success "文件权限设置成功"
            else
                warning "文件权限设置失败，可能需要手动调整"
            fi
        fi
    else
        error "Python配置文件传输失败"
        exit 1
    fi
}

# 在目标服务器上执行部署
deploy_on_target() {
    info "在目标服务器上开始部署..."
    
    local target_path
    target_path="$CURRENT_DIR/Awesome-poetize-open"
    
    info "正在目标服务器上执行部署脚本，这可能需要一些时间..."
    info "部署过程中可能需要您的交互输入（如域名配置、HTTPS设置等）"
    
    # 设置部署脚本执行权限
    if [ "$TARGET_USER" = "root" ]; then
        sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "cd $target_path && chmod +x deploy.sh"
    else
        sshpass -p "$TARGET_PASSWORD" ssh -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "echo '$TARGET_PASSWORD' | sudo -S bash -c 'cd $target_path && chmod +x deploy.sh'"
    fi
    
    # 执行部署脚本（支持交互式操作）
    echo -e "${YELLOW}[提示]${NC} 即将连接到目标服务器执行部署脚本，请根据提示进行交互操作"
    echo -e "${YELLOW}[提示]${NC} 如果需要退出，请按 Ctrl+C"
    echo ""
    
    if [ "$TARGET_USER" = "root" ]; then
        sshpass -p "$TARGET_PASSWORD" ssh -t -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "cd $target_path && echo '开始执行部署脚本...' && ./deploy.sh"
    else
        sshpass -p "$TARGET_PASSWORD" ssh -t -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_IP" "cd $target_path && echo '开始执行部署脚本...' && sudo ./deploy.sh"
    fi
    
    local exit_code=$?
    if [ $exit_code -eq 0 ]; then
        success "目标服务器部署完成"
    elif [ $exit_code -eq 130 ]; then
        warning "部署被用户中断"
    else
        warning "部署过程中可能出现了一些问题，请检查目标服务器状态"
    fi
}

# 清理临时文件
cleanup() {
    if [ -n "$BACKUP_DIR" ] && [ -d "$BACKUP_DIR" ]; then
        info "清理临时文件..."
        rm -rf "$BACKUP_DIR"
        success "临时文件清理完成"
    fi
}

# 显示迁移总结
show_summary() {
    printf "\n"
    printf "${GREEN}%80s${NC}\n" | tr ' ' '='
    printf "${GREEN}%s${NC}\n" "$(printf '%*s' $(((80-20)/2)) '')Poetize 迁移完成！$(printf '%*s' $(((80-20)/2)) '')"
    printf "${GREEN}%80s${NC}\n" | tr ' ' '='
    printf "\n"
    
    printf "${BLUE}迁移信息${NC}\n"
    printf "${BLUE}%s${NC}\n" "$(printf '%*s' 8 '' | tr ' ' '-')"
    printf "  源服务器: %s\n" "$(hostname)"
    printf "  目标服务器: %s@%s\n" "$TARGET_USER" "$TARGET_IP"
    printf "  网络环境: %s\n" "$([ "$IS_CHINA_ENV" = true ] && echo '国内环境 (使用Gitee)' || echo '国外环境 (使用GitHub)')"
    printf "\n"
    
    printf "${BLUE}迁移内容${NC}\n"
    printf "${BLUE}%s${NC}\n" "$(printf '%*s' 8 '' | tr ' ' '-')"
    printf "  ✓ 数据库数据\n"
    printf "  ✓ 数据库凭据\n"
    printf "  ✓ Python配置文件\n"
    printf "  ✓ 项目代码\n"
    printf "\n"
    
    printf "${BLUE}访问信息${NC}\n"
    printf "${BLUE}%s${NC}\n" "$(printf '%*s' 8 '' | tr ' ' '-')"
    printf "  网站地址: ${GREEN}http://%s${NC}\n" "$TARGET_IP"
    printf "  管理后台: ${GREEN}http://%s/admin${NC}\n" "$TARGET_IP"
    printf "  聊天室: ${GREEN}http://%s/im${NC}\n" "$TARGET_IP"
    printf "\n"
    
    printf "${YELLOW}注意事项${NC}\n"
    printf "${YELLOW}%s${NC}\n" "$(printf '%*s' 8 '' | tr ' ' '-')"
    printf "  1. 如需配置域名和HTTPS，请在目标服务器上重新运行部署脚本\n"
    printf "  2. 默认管理员账号: Sara, 密码: aaa\n"
    printf "  3. 请及时修改管理员密码确保安全\n"
    printf "  4. 如有问题，请检查目标服务器的Docker容器状态\n"
    printf "\n"
    
    printf "${GREEN}%80s${NC}\n" | tr ' ' '='
}

# 主函数
main() {
  printf "${GREEN}██████╗  ██████╗ ███████╗████████╗██╗███████╗███████╗${NC}\n"
  printf "${GREEN}██╔══██╗██╔═══██╗██╔════╝╚══██╔══╝██║╚══███╔╝██╔════╝${NC}\n"
  printf "${GREEN}██████╔╝██║   ██║█████╗     ██║   ██║  ███╔╝ █████╗${NC}\n"
  printf "${GREEN}██╔═══╝ ██║   ██║██╔══╝     ██║   ██║ ███╔╝  ██╔══╝${NC}\n"
  printf "${GREEN}██║     ╚██████╔╝███████╗   ██║   ██║███████╗███████╝${NC}\n"
  printf "${GREEN}╚═╝      ╚═════╝ ╚══════╝   ╚═╝   ╚═╝╚══════╝╚══════╝${NC}\n"
    echo -e "${BLUE}博客迁移工具====================================================${NC}"
    echo ""
    
    # 设置错误处理
    set -e
    trap cleanup EXIT
    
    # 执行迁移步骤
    check_prerequisites
    get_user_input
    read_db_credentials
    backup_database
    test_ssh_connection
    detect_target_environment
    pull_code_on_target
    transfer_files
    deploy_on_target
    
    # 显示总结
    show_summary
}

# 运行主函数
main "$@"