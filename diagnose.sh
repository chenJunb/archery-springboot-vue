#!/bin/bash
# 射箭计时系统 - 启动诊断脚本
# 用于检查和修复启动时的常见问题

echo "================================"
echo "射箭计时系统 - 启动诊断工具"
echo "================================"
echo ""

# 检查 Java 版本
echo "【检查 Java 环境】"
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1)
    echo "✅ Java 已安装"
    echo "$java_version" | head -2
else
    echo "❌ Java 未安装或未在 PATH 中"
    echo "请安装 Java 11+ 并配置环境变量"
    exit 1
fi

echo ""
echo "【检查 Maven 环境】"
if command -v mvn &> /dev/null; then
    echo "✅ Maven 已安装"
    mvn --version | head -1
else
    echo "❌ Maven 未安装或未在 PATH 中"
    echo "请安装 Maven 3.6+ 并配置环境变量"
    exit 1
fi

echo ""
echo "【检查 Node.js 环境】"
if command -v node &> /dev/null; then
    echo "✅ Node.js 已安装"
    node --version
else
    echo "❌ Node.js 未安装或未在 PATH 中"
    echo "请安装 Node.js 16+ 并配置环境变量"
    exit 1
fi

echo ""
echo "【检查 npm 环境】"
if command -v npm &> /dev/null; then
    echo "✅ npm 已安装"
    npm --version
else
    echo "❌ npm 未安装或未在 PATH 中"
    exit 1
fi

echo ""
echo "【检查后端依赖】"
cd backend
if [ -f "pom.xml" ]; then
    echo "✅ pom.xml 存在"
    echo "验证 Maven 项目结构..."
    if [ -d "src/main/java" ]; then
        echo "✅ src/main/java 目录存在"
    else
        echo "❌ src/main/java 目录不存在"
    fi
    if [ -d "src/main/resources" ]; then
        echo "✅ src/main/resources 目录存在"
    else
        echo "❌ src/main/resources 目录不存在"
    fi
else
    echo "❌ pom.xml 不存在"
    exit 1
fi

echo ""
echo "【检查前端依赖】"
cd ../frontend
if [ -f "package.json" ]; then
    echo "✅ package.json 存在"
    if [ -d "node_modules" ]; then
        echo "✅ node_modules 存在"
    else
        echo "⚠️  node_modules 不存在，需要运行 'npm install'"
    fi
    if [ -f "vite.config.js" ] || [ -f "vue.config.js" ]; then
        echo "✅ 构建配置文件存在"
    else
        echo "⚠️  未找到构建配置文件"
    fi
else
    echo "❌ package.json 不存在"
    exit 1
fi

echo ""
echo "【检查必要的文件】"
cd ..
if [ -f "frontend/src/services/globalWebSocketService.js" ]; then
    echo "✅ globalWebSocketService.js 存在"
else
    echo "❌ globalWebSocketService.js 不存在"
fi

if [ -f "backend/src/main/resources/application.yml" ]; then
    echo "✅ application.yml 存在"
else
    echo "❌ application.yml 不存在"
fi

echo ""
echo "================================"
echo "诊断完成！"
echo "================================"
echo ""
echo "后续步骤："
echo "1. 如果有错误，请按照提示安装缺失的工具"
echo "2. 启动后端：cd backend && mvn clean package && java -jar target/archery-timer-*.jar"
echo "3. 启动前端：cd frontend && npm install && npm run dev"
echo "4. 打开浏览器访问 http://localhost:3000"
