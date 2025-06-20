const fs = require('fs');
const path = require('path');
const axios = require('axios');

/**
 * 渲染指定文章 ID 列表为静态 HTML
 * @param {string[]} ids  文章 ID 数组
 * @param {Object}  options 可选项 { baseUrl, outputRoot }
 */
async function renderIds(ids = [], options = {}) {
  if (!Array.isArray(ids) || ids.length === 0) {
    throw new Error('ids must be a non-empty array');
  }

  const BASE_URL = options.baseUrl || process.env.PRERENDER_BASE_URL || 'http://poetize-article-ssr:3000';
  const OUTPUT_ROOT = options.outputRoot || process.env.PRERENDER_OUTPUT || path.resolve(__dirname, '../dist/prerender/article');

  const langs = ['zh', 'en'];

  for (const id of ids) {
    for (const lang of langs) {
      const url = `${BASE_URL}/article/${id}?lang=${lang}`;
      console.log(`[prerender] fetch ${url}`);
      try {
        const resp = await axios.get(url, { timeout: 60000 });
        const html = resp.data;
        const dir = path.join(OUTPUT_ROOT, id.toString());
        fs.mkdirSync(dir, { recursive: true });
        const filename = lang === 'zh' ? 'index.html' : `index-${lang}.html`;
        const sanitized = html.replace(/<html([^>]*?)class="([^"]*?)gray-mode([^"]*?)"([^>]*)>/i, (match, p1, preClasses, postClasses, p4) => {
          // 去掉 gray-mode 并同时去除 filter style
          const classes = `${preClasses} ${postClasses}`.replace(/\s+/g, ' ').replace(/(^\s|\s$)/g, '').replace(/gray-mode/gi, '').trim();
          return `<html${p1}${classes ? ` class="${classes}"` : ''}${p4}>`;
        }).replace(/filter:\s*grayscale\(100%\);?/gi, '');
        fs.writeFileSync(path.join(dir, filename), sanitized, 'utf8');
        console.log(`[prerender] done -> ${dir}/${filename}`);
      } catch (e) {
        console.error(`[prerender] failed for ${id} lang ${lang}:`, e.message);
      }
    }
  }
}

module.exports = { renderIds }; 