import constant from "./constant";
import CryptoJS from 'crypto-js';
import store from '../store';
import {ElMessage} from "element-plus";
import { getDefaultAvatar, getAvatarUrl } from './default-avatar';

export default {
  /**
   * 获取默认头像
   */
  getDefaultAvatar,
  
  /**
   * 获取头像URL（带默认头像回退）
   */
  getAvatarUrl,
  
  /**
   * 判断设备
   */
  mobile() {
    let flag = navigator.userAgent.match(/(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i);
    return flag && flag.length && flag.length > 0;
  },

  /**
   * 判断是否为空
   */
  isEmpty(value) {
    if (typeof value === "undefined" || value === null || (typeof value === "string" && value.trim() === "") || (Array.prototype.isPrototypeOf(value) && value.length === 0) || (Object.prototype.isPrototypeOf(value) && Object.keys(value).length === 0)) {
      return true;
    } else {
      return false;
    }
  },

  /**
   * 加密
   */
  encrypt(plaintText) {
    let options = {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7
    };
    let key = CryptoJS.enc.Utf8.parse(constant.cryptojs_key);
    let encryptedData = CryptoJS.AES.encrypt(plaintText, key, options);
    return encryptedData.toString().replace(/\//g, "_").replace(/\+/g, "-");
  },

  /**
   * 解密
   */
  decrypt(encryptedBase64Str) {
    let val = encryptedBase64Str.replace(/\-/g, '+').replace(/_/g, '/');
    let options = {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7
    };
    let key = CryptoJS.enc.Utf8.parse(constant.cryptojs_key);
    let decryptedData = CryptoJS.AES.decrypt(val, key, options);
    return CryptoJS.enc.Utf8.stringify(decryptedData);
  },

  /**
   * 表情包转换
   */
  faceReg(content) {
    content = content.replace(/\[[^\[^\]]+\]/g, (word) => {
      let index = constant.emojiList.indexOf(word.replace("[", "").replace("]", ""));
      if (index > -1) {
        let url = store.state.sysConfig['webStaticResourcePrefix'] + "emoji/q" + (index + 1) + ".gif";
        return '<img loading="lazy" style="vertical-align: middle;width: 32px;height: 32px" src="' + url + '" title="' + word + '"/>';
      } else {
        return word;
      }
    });
    return content;
  },

  /**
   * 图片转换
   */
  pictureReg(content) {
    content = content.replace(/\[[^\[^\]]+\]/g, (word) => {
      let index = word.indexOf(",");
      if (index > -1) {
        let arr = word.replace("[", "").replace("]", "").split(",");
        return '<img loading="lazy" style="border-radius: 5px;width: 100%;max-width: 250px" src="' + arr[1] + '" title="' + arr[0] + '"/>';
      } else {
        return word;
      }
    });
    return content;
  },

  /**
   * 字符串转换为时间戳
   */
  getDateTimeStamp(dateStr) {
    return Date.parse(dateStr.replace(/-/gi, "/"));
  },

  getDateDiff(dateStr) {
    let publishTime = Date.parse(dateStr.replace(/-/gi, "/")) / 1000,
      d_seconds,
      d_minutes,
      d_hours,
      d_days,
      timeNow = Math.floor(new Date().getTime() / 1000),
      d,
      date = new Date(publishTime * 1000),
      Y = date.getFullYear(),
      M = date.getMonth() + 1,
      D = date.getDate(),
      H = date.getHours(),
      m = date.getMinutes(),
      s = date.getSeconds();
    //小于10的在前面补0
    if (M < 10) {
      M = '0' + M;
    }
    if (D < 10) {
      D = '0' + D;
    }
    if (H < 10) {
      H = '0' + H;
    }
    if (m < 10) {
      m = '0' + m;
    }
    if (s < 10) {
      s = '0' + s;
    }
    d = timeNow - publishTime;
    d_days = Math.floor(d / 86400);
    d_hours = Math.floor(d / 3600);
    d_minutes = Math.floor(d / 60);
    d_seconds = Math.floor(d);
    if (d_days > 0 && d_days < 3) {
      return d_days + '天前';
    } else if (d_days <= 0 && d_hours > 0) {
      return d_hours + '小时前';
    } else if (d_hours <= 0 && d_minutes > 0) {
      return d_minutes + '分钟前';
    } else if (d_seconds < 60) {
      if (d_seconds <= 0) {
        return '刚刚发表';
      } else {
        return d_seconds + '秒前';
      }
    } else if (d_days >= 3 && d_days < 30) {
      return M + '-' + D + ' ' + H + ':' + m;
    } else if (d_days >= 30) {
      return Y + '-' + M + '-' + D + ' ' + H + ':' + m;
    }
  },

  /**
   * 格式化聊天消息时间
   * 当天的消息：只显示时间（22:37）
   * 昨天或往后（今年）：显示月日（10月28日）
   * 前年的消息：显示年月日（2024年10月28日）
   */
  formatChatTime(dateInput) {
    if (!dateInput) return '';
    
    // 处理不同类型的输入
    let messageDate;
    if (dateInput instanceof Date) {
      // 已经是 Date 对象
      messageDate = dateInput;
    } else if (typeof dateInput === 'number') {
      // 时间戳
      messageDate = new Date(dateInput);
    } else if (typeof dateInput === 'string') {
      // 字符串，替换 - 为 / 以兼容 iOS
      messageDate = new Date(dateInput.replace(/-/gi, "/"));
    } else {
      // 其他类型，尝试直接转换
      messageDate = new Date(dateInput);
    }
    
    // 检查日期是否有效
    if (isNaN(messageDate.getTime())) {
      console.warn('formatChatTime: 无效的日期格式', dateInput);
      return '';
    }
    
    const now = new Date();
    
    // 获取今天的开始时间（00:00:00）
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    
    // 获取消息日期的年月日
    const messageYear = messageDate.getFullYear();
    const messageMonth = messageDate.getMonth() + 1;
    const messageDay = messageDate.getDate();
    const messageHours = String(messageDate.getHours()).padStart(2, '0');
    const messageMinutes = String(messageDate.getMinutes()).padStart(2, '0');
    
    const currentYear = now.getFullYear();
    
    // 如果是今天的消息，只显示时:分
    if (messageDate >= todayStart) {
      return `${messageHours}:${messageMinutes}`;
    }
    
    // 如果是今年的消息，显示月日
    if (messageYear === currentYear) {
      return `${messageMonth}月${messageDay}日`;
    }
    
    // 如果是往年的消息，显示年月日
    return `${messageYear}年${messageMonth}月${messageDay}日`;
  },

  /**
   * 保存资源
   */
  saveResource(that, type, path, size, mimeType, originalName, storeType) {
    let resource = {
      type: type,
      path: path,
      size: size,
      mimeType: mimeType,
      storeType: storeType,
      originalName: originalName
    };

    that.$http.post(that.$constant.baseURL + "/resource/saveResource", resource)
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }
}
