#!/usr/bin/env node

/**
 * 图书馆信息查询系统 - Cloudflare交互式部署脚本
 * 使用方法：node deploy.js
 */

import { exec } from 'child_process';
import { promisify } from 'util';
import * as readline from 'readline';
import * as fs from 'fs';
import * as path from 'path';

const execAsync = promisify(exec);

// 颜色代码
const colors = {
    reset: '\x1b[0m',
    red: '\x1b[31m',
    green: '\x1b[32m',
    yellow: '\x1b[33m',
    blue: '\x1b[34m',
    cyan: '\x1b[36m',
};

// 创建readline接口
const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

// Promise版本的question
function question(query) {
    return new Promise(resolve => rl.question(query, resolve));
}

// 打印带颜色的消息
function print(message, color = 'reset') {
    console.log(`${colors[color]}${message}${colors.reset}`);
}

// 检查命令是否存在
async function commandExists(command) {
    try {
        await execAsync(`which ${command}`);
        return true;
    } catch {
        return false;
    }
}

// 显示欢迎信息
function showWelcome() {
    console.clear();
    print('==================================================', 'cyan');
    print('      图书馆信息查询系统', 'cyan');
    print('      Cloudflare 交互式部署工具', 'cyan');
    print('==================================================', 'cyan');
    console.log();
}

// 步骤1: 检查前置条件
async function checkPrerequisites() {
    print('步骤 1/6: 检查前置条件...', 'blue');
    console.log();

    // 检查Node.js
    if (await commandExists('node')) {
        const { stdout } = await execAsync('node --version');
        print(`✓ Node.js 已安装: ${stdout.trim()}`, 'green');
    } else {
        print('✗ Node.js 未安装', 'red');
        print('请访问 https://nodejs.org/ 下载安装', 'yellow');
        process.exit(1);
    }

    // 检查npm
    if (await commandExists('npm')) {
        const { stdout } = await execAsync('npm --version');
        print(`✓ npm 已安装: ${stdout.trim()}`, 'green');
    } else {
        print('✗ npm 未安装', 'red');
        process.exit(1);
    }

    // 检查Wrangler
    if (!(await commandExists('wrangler'))) {
        print('! Wrangler 未安装，正在安装...', 'yellow');
        try {
            await execAsync('npm install -g wrangler');
            print('✓ Wrangler 安装完成', 'green');
        } catch (error) {
            print('✗ Wrangler 安装失败', 'red');
            print('请手动运行: npm install -g wrangler', 'yellow');
            process.exit(1);
        }
    } else {
        const { stdout } = await execAsync('wrangler --version');
        print(`✓ Wrangler 已安装: ${stdout.trim()}`, 'green');
    }

    console.log();
}

// 步骤2: 登录Cloudflare
async function loginCloudflare() {
    print('步骤 2/6: 登录 Cloudflare...', 'blue');
    console.log();

    try {
        await execAsync('wrangler whoami');
        print('✓ 已登录 Cloudflare', 'green');
    } catch {
        print('ℹ 需要登录 Cloudflare...', 'cyan');
        await question('按 Enter 键打开浏览器进行登录...');
        await execAsync('wrangler login');
        print('✓ 登录成功', 'green');
    }

    console.log();
}

// 步骤3: 设置环境变量
async function setupSecrets() {
    print('步骤 3/6: 配置环境变量...', 'blue');
    console.log();

    const workerDir = path.join(process.cwd(), 'worker');
    process.chdir(workerDir);

    // 检查是否已设置
    let secretsExist = false;
    try {
        const { stdout } = await execAsync('wrangler secret list');
        secretsExist = stdout.includes('USERNAME');
    } catch {
        secretsExist = false;
    }

    if (secretsExist) {
        const answer = await question('环境变量已存在，是否重新设置？(y/N): ');
        if (answer.toLowerCase() !== 'y') {
            print('✓ 使用现有环境变量', 'green');
            process.chdir('..');
            console.log();
            return;
        }
    }

    console.log();
    print('请输入以下信息（密码输入不会显示）：', 'yellow');
    console.log();

    // 获取用户名
    const username = await question('学号/用户名: ');
    
    // 获取密码（使用原生方式，不显示输入）
    print('统一认证密码: ', 'reset');
    process.stdin.setRawMode(true);
    process.stdin.resume();
    process.stdin.setEncoding('utf8');
    
    let eduPassword = '';
    await new Promise(resolve => {
        process.stdin.on('data', function(char) {
            if (char === '\n' || char === '\r' || char === '\u0004') {
                process.stdin.setRawMode(false);
                process.stdin.pause();
                console.log();
                resolve();
            } else if (char === '\u0003') {
                process.exit();
            } else if (char === '\u007f') {
                eduPassword = eduPassword.slice(0, -1);
            } else {
                eduPassword += char;
            }
        });
    });

    print('图书馆密码: ', 'reset');
    process.stdin.setRawMode(true);
    process.stdin.resume();
    
    let libPassword = '';
    await new Promise(resolve => {
        process.stdin.on('data', function(char) {
            if (char === '\n' || char === '\r' || char === '\u0004') {
                process.stdin.setRawMode(false);
                process.stdin.pause();
                console.log();
                resolve();
            } else if (char === '\u0003') {
                process.exit();
            } else if (char === '\u007f') {
                libPassword = libPassword.slice(0, -1);
            } else {
                libPassword += char;
            }
        });
    });

    console.log();
    print('正在设置环境变量...', 'cyan');

    // 设置秘钥
    try {
        await execAsync(`echo "${username}" | wrangler secret put USERNAME`);
        await execAsync(`echo "${eduPassword}" | wrangler secret put EDU_PASSWORD`);
        await execAsync(`echo "${libPassword}" | wrangler secret put LIB_PASSWORD`);
        print('✓ 环境变量设置完成', 'green');
    } catch (error) {
        print('✗ 环境变量设置失败', 'red');
        console.error(error.message);
        process.exit(1);
    }

    process.chdir('..');
    console.log();
}

// 步骤4: 部署Worker
async function deployWorker() {
    print('步骤 4/6: 部署 Worker...', 'blue');
    console.log();

    const workerDir = path.join(process.cwd(), 'worker');
    process.chdir(workerDir);

    // 安装依赖
    if (!fs.existsSync('node_modules')) {
        print('ℹ 安装 Worker 依赖...', 'cyan');
        await execAsync('npm install');
        print('✓ 依赖安装完成', 'green');
    }

    // 部署
    print('ℹ 部署 Worker...', 'cyan');
    const { stdout } = await execAsync('wrangler deploy');
    console.log(stdout);

    // 提取Worker URL
    const urlMatch = stdout.match(/https:\/\/[^\s]*workers\.dev/);
    let workerUrl = urlMatch ? urlMatch[0] : null;

    if (!workerUrl) {
        // 尝试从whoami获取
        try {
            const { stdout: whoami } = await execAsync('wrangler whoami');
            const subdomainMatch = whoami.match(/"subdomain":\s*"([^"]+)"/);
            if (subdomainMatch) {
                workerUrl = `https://library-info-worker.${subdomainMatch[1]}.workers.dev`;
            }
        } catch {}
    }

    if (!workerUrl) {
        print('! 无法自动获取 Worker URL', 'yellow');
        workerUrl = await question('请手动输入 Worker URL: ');
    }

    print(`✓ Worker 部署完成`, 'green');
    print(`ℹ Worker URL: ${workerUrl}`, 'cyan');

    process.chdir('..');
    console.log();
    
    return workerUrl;
}

// 步骤5: 配置Pages
async function configurePages(workerUrl) {
    print('步骤 5/6: 配置 Pages...', 'blue');
    console.log();

    const configFile = path.join(process.cwd(), 'pages/assets/js/config.js');
    
    let content = fs.readFileSync(configFile, 'utf8');
    content = content.replace(
        /const API_BASE_URL = '[^']*';/,
        `const API_BASE_URL = '${workerUrl}';`
    );
    fs.writeFileSync(configFile, content);

    print('✓ API 配置已更新', 'green');
    console.log();
}

// 步骤6: 部署Pages
async function deployPages() {
    print('步骤 6/6: 部署 Pages...', 'blue');
    console.log();

    const pagesDir = path.join(process.cwd(), 'pages');
    process.chdir(pagesDir);

    print('ℹ 部署 Pages...', 'cyan');
    const { stdout } = await execAsync('wrangler pages deploy . --project-name=library-info');
    console.log(stdout);

    // 提取Pages URL
    const urlMatch = stdout.match(/https:\/\/[^\s]*pages\.dev/);
    const pagesUrl = urlMatch ? urlMatch[0] : 'https://library-info.pages.dev';

    print('✓ Pages 部署完成', 'green');

    process.chdir('..');
    console.log();
    
    return pagesUrl;
}

// 显示完成信息
function showCompletion(workerUrl, pagesUrl) {
    console.log();
    print('==================================================', 'cyan');
    print('  🎉 部署完成！', 'green');
    print('==================================================', 'cyan');
    console.log();
    print(`✓ Worker URL: ${workerUrl}`, 'green');
    print(`✓ Pages URL:  ${pagesUrl}`, 'green');
    console.log();
    print('ℹ 现在可以访问你的网站：', 'cyan');
    print(`  ${pagesUrl}`, 'blue');
    console.log();
    print('ℹ 测试API：', 'cyan');
    print(`  curl ${workerUrl}/api/health`, 'blue');
    console.log();
    print('==================================================', 'cyan');
}

// 主函数
async function main() {
    try {
        showWelcome();
        await checkPrerequisites();
        await loginCloudflare();
        await setupSecrets();
        const workerUrl = await deployWorker();
        await configurePages(workerUrl);
        const pagesUrl = await deployPages();
        showCompletion(workerUrl, pagesUrl);
    } catch (error) {
        console.error();
        print('✗ 部署失败！', 'red');
        console.error(error.message);
        process.exit(1);
    } finally {
        rl.close();
    }
}

// 运行
main();
