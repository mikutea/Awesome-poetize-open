/**
 * 复制Mermaid文件到public目录
 * 使文章编辑器可以使用本地Mermaid而不是CDN
 */

const fs = require('fs');
const path = require('path');

// 源文件路径
const sourcePath = path.join(__dirname, '../node_modules/mermaid/dist/mermaid.min.js');

// 目标文件路径
const targetPath = path.join(__dirname, '../public/libs/js/mermaid.min.js');

// 检查源文件是否存在
if (!fs.existsSync(sourcePath)) {
  console.error('[ERROR] 源文件不存在:', sourcePath);
  console.error('请先运行: npm install');
  process.exit(1);
}

// 确保目标目录存在
const targetDir = path.dirname(targetPath);
if (!fs.existsSync(targetDir)) {
  console.log('[INFO] 创建目标目录:', targetDir);
  fs.mkdirSync(targetDir, { recursive: true });
}

// 复制文件
try {
  fs.copyFileSync(sourcePath, targetPath);
  
  // 获取文件大小
  const stats = fs.statSync(targetPath);
  const fileSizeInKB = (stats.size / 1024).toFixed(2);
  
  console.log('[SUCCESS] Mermaid文件复制成功！');
  console.log(`   源: ${sourcePath}`);
  console.log(`   目标: ${targetPath}`);
  console.log(`   大小: ${fileSizeInKB} KB`);
  console.log('');
  console.log('[INFO] 后续步骤:');
  console.log('   1. 运行: npm run build');
  console.log('   2. 重启服务');
  console.log('   3. 测试文章编辑器的Mermaid预览功能');
} catch (error) {
  console.error('[ERROR] 复制失败:', error.message);
  process.exit(1);
}
