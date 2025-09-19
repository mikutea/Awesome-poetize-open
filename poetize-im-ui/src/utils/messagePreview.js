/**
 * 消息预览工具函数
 * 用于处理聊天消息的预览显示，正确处理表情符号和文本混合内容
 */

/**
 * 获取消息预览文本（正确处理表情符号和文本混合内容）
 * @param {string} content - 消息内容（可能包含HTML标签）
 * @returns {string} 预览文本
 */
export function getMessagePreview(content) {
  if (!content) return '';
  
  // 创建临时DOM元素来解析HTML
  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = content;
  
  let previewText = '';
  
  // 遍历所有子节点，正确处理文本和表情符号
  function processNode(node) {
    if (node.nodeType === Node.TEXT_NODE) {
      // 文本节点，直接添加文本内容
      previewText += node.textContent;
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      if (node.tagName === 'IMG') {
        // 图片节点，检查是否是表情符号
        const title = node.getAttribute('title');
        const src = node.getAttribute('src');
        
        if (src && src.includes('emoji/q') && title) {
          // 表情符号，使用title属性
          previewText += title;
        } else {
          // 普通图片
          previewText += '[图片]';
        }
      } else {
        // 其他HTML元素，递归处理子节点
        for (let child of node.childNodes) {
          processNode(child);
        }
      }
    }
  }
  
  // 处理所有子节点
  for (let child of tempDiv.childNodes) {
    processNode(child);
  }
  
  // 清理多余的空白字符
  previewText = previewText.replace(/\s+/g, ' ').trim();
  
  // 如果没有提取到任何内容，检查是否是纯图片消息
  if (!previewText && content.includes('<img')) {
    previewText = '[图片]';
  }
  
  // 截取前10个字符（增加显示长度以更好展示混合内容）
  return previewText.length > 10 ? previewText.substr(0, 10) + '...' : previewText;
}

/**
 * 检查消息是否包含表情符号
 * @param {string} content - 消息内容
 * @returns {boolean} 是否包含表情符号
 */
export function hasEmoji(content) {
  if (!content) return false;
  return content.includes('emoji/q') || /\[.*?\]/.test(content);
}

/**
 * 检查消息是否为纯图片消息
 * @param {string} content - 消息内容
 * @returns {boolean} 是否为纯图片消息
 */
export function isImageMessage(content) {
  if (!content) return false;
  
  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = content;
  
  // 检查是否只包含图片标签，没有其他文本内容
  const textContent = tempDiv.textContent || tempDiv.innerText || '';
  const hasImages = content.includes('<img');
  
  return hasImages && textContent.trim() === '';
}

/**
 * 获取消息中的表情符号列表
 * @param {string} content - 消息内容
 * @returns {Array<string>} 表情符号列表
 */
export function getEmojisFromMessage(content) {
  if (!content) return [];
  
  const emojis = [];
  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = content;
  
  const images = tempDiv.querySelectorAll('img');
  images.forEach(img => {
    const src = img.getAttribute('src');
    const title = img.getAttribute('title');
    
    if (src && src.includes('emoji/q') && title) {
      emojis.push(title);
    }
  });
  
  return emojis;
}

export default {
  getMessagePreview,
  hasEmoji,
  isImageMessage,
  getEmojisFromMessage
};