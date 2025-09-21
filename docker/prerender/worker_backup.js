// 这是一个临时文件，用于保存修复后的代码段

// 在序列化后添加这段代码：
  
  // 处理 hreflang 链接（在序列化后直接操作字符串，避免转义问题）
  if (meta && typeof meta === 'object') {
    Object.keys(meta).forEach(key => {
      if (key.startsWith('hreflang_')) {
        const parts = key.split('_');
        if (parts.length >= 2) {
          const lang = parts[1];
          const href = meta[key];
          const linkHTML = `<link rel="alternate" hreflang="${lang}" href="${href}" />`;
          const titleMatch = html.match(/<title[^>]*>.*?<\/title>/i);
          if (titleMatch) {
            html = html.replace(titleMatch[0], `${linkHTML}\n  ${titleMatch[0]}`);
          }
        }
      }
    });
  }