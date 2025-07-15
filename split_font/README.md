# 中文字体子集化工具

这个工具用于将中文字体文件切分为多个子集，以便在Web开发中实现字体优化。

## 切分策略

将原始字体完整地切分为4个部分：

1. **base**: 英文、数字和常用符号（ASCII 32-126）以及中文标点符号和全角符号
2. **level1**: 国家一级常用汉字
3. **level2**: 国家二级常用汉字
4. **other**: 原始字体中其余的所有字符

## 依赖项

- Python 3.6+
- fonttools
- brotli

## 安装依赖

```bash
pip install -r requirements.txt
```

## 使用方法

1. 将您的字体文件命名为 `font.ttf` 并放在项目根目录下
2. 运行脚本：

```bash
python font_subset.py
```

3. 脚本将在 `font_chunks` 目录中生成以下文件：

- `font.base.woff2` - 包含ASCII字符和中文标点符号的字体文件 (~9KB)
- `font.level1.woff2` - 包含一级汉字的字体文件 (~830KB)
- `font.level2.woff2` - 包含二级汉字的字体文件 (~756KB)
- `font.other.woff2` - 包含原始字体中其他字符的字体文件 (~199KB)
- `unicode_ranges.json` - 包含各子集Unicode字符范围的JSON文件，用于CSS @font-face的unicode-range属性或JavaScript动态加载

## 优势

大幅减少网络请求的数据量，减小网站带宽开销，提升首屏加载速度
