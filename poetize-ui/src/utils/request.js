import axios from "axios";
import constant from "./constant";
//处理url参数
import qs from "qs";

import router from "../router";
import { handleTokenExpire } from "./tokenExpireHandler";

// 缓存翻译配置，避免重复请求
let cachedTranslationConfig = null;
let configCacheTime = 0;
const CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存

// 获取翻译配置中的超时时间
async function getTranslationTimeout() {
  const now = Date.now();
  
  // 如果缓存有效，直接使用
  if (cachedTranslationConfig && (now - configCacheTime) < CACHE_DURATION) {
    return cachedTranslationConfig.timeout || 30;
  }
  
  try {
    const adminToken = localStorage.getItem('adminToken');
    const userToken = localStorage.getItem('userToken');
    const token = adminToken || userToken;
    
    const headers = {
      'User-Agent': 'axios'
    };
    if (token) {
      // 确保Authorization头存在并添加Bearer前缀
      headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }
    
    const response = await axios.get(constant.pythonBaseURL + '/api/translation/config', {
      timeout: 10000,
      headers: headers
    });
    
    if (response.data && response.data.code === 200 && response.data.data) {
      const timeout = response.data.data.llm?.timeout || 30;
      cachedTranslationConfig = { timeout };
      configCacheTime = now;
      return timeout;
    }
  } catch (error) {
    // 静默失败，使用默认值
  }
  
  // 如果获取失败，返回默认值
  return 30;
}

// 超时配置常量
const TIMEOUT_CONFIG = {
  DEFAULT: 60000,           // 默认60秒
  SEO: 30000,               // SEO请求30秒
  TRANSLATION: 120000,      // 翻译请求2分钟
  ARTICLE_SAVE: 300000,     // 文章保存最少5分钟
  ARTICLE_BUFFER: 30        // 文章保存缓冲时间（秒）
};

// 设置请求基本配置
axios.defaults.baseURL = constant.baseURL;
axios.defaults.timeout = TIMEOUT_CONFIG.DEFAULT;

/**
 * 根据URL路径设置超时时间
 * @param {Object} config - axios请求配置
 * @returns {Object} 处理后的配置
 */
function configureTimeout(config) {
  if (!config.url) return config;
  
  const url = config.url;
  const isDefaultTimeout = !config.timeout || config.timeout === TIMEOUT_CONFIG.DEFAULT;
  
  // SEO请求：短超时 + 重试机制
  if (url.includes('/seo/')) {
    config.timeout = TIMEOUT_CONFIG.SEO;
    config.retry = 3;
    config.retryDelay = 1000;
    
    // 防止请求卡住
    const source = axios.CancelToken.source();
    config.cancelToken = source.token;
    setTimeout(() => source.cancel('SEO请求超时自动取消'), TIMEOUT_CONFIG.SEO + 5000);
    
    return config;
  }
  
  // 文章保存/更新：动态超时（根据翻译配置）
  if (url.includes('/article/saveArticle') || url.includes('/article/updateArticle')) {
    if (isDefaultTimeout) {
      const cachedTimeout = cachedTranslationConfig?.timeout || 30;
      const dynamicTimeout = (cachedTimeout + TIMEOUT_CONFIG.ARTICLE_BUFFER) * 1000;
      config.timeout = Math.max(dynamicTimeout, TIMEOUT_CONFIG.ARTICLE_SAVE);
    }
    return config;
  }
  
  // 翻译API：长超时（AI处理需要更多时间）
  if (url.includes('/api/translation/')) {
    if (isDefaultTimeout) {
      config.timeout = TIMEOUT_CONFIG.TRANSLATION;
    }
    return config;
  }
  
  return config;
}

// 添加请求拦截器
axios.interceptors.request.use(function (config) {
  // 统一处理超时配置
  config = configureTimeout(config);

  // 如果是验证码相关的请求，不需要token
  if (config.url && config.url.includes('/captcha/')) {
    return config;
  }

  // 处理token
  const isAdmin = config.isAdmin || false;
  const token = isAdmin ? localStorage.getItem("adminToken") : localStorage.getItem("userToken");
  
  if (token) {
    // 确保Authorization头存在
    if (!config.headers) {
      config.headers = {};
    }
    // 添加Bearer前缀
    config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  }

  return config;
}, function (error) {
  return Promise.reject(error);
});

// 添加响应拦截器
axios.interceptors.response.use(function (response) {
  if (response.data !== null && response.data.hasOwnProperty("code") && response.data.code !== 200) {
    if (response.data.code === 300 || response.data.code === 401) {
      // token失效，使用统一的token过期处理逻辑
      const isAdminRequest = response.config.isAdmin || false;
      handleTokenExpire(isAdminRequest, router.currentRoute.fullPath, {
        showMessage: true
      });
    }
    return Promise.reject(new Error(response.data.message || '请求失败'));
  }
  return response;
}, function (error) {
  // 处理网络错误
  if (error.response) {
    // 服务器返回错误状态码
    if (error.response.status === 401 || error.response.status === 403) {
      // token相关错误，使用统一的token过期处理逻辑
      const isAdminRequest = error.config && error.config.isAdmin || false;
      handleTokenExpire(isAdminRequest, router.currentRoute.fullPath, {
        showMessage: true
      });
    }
  }
  return Promise.reject(error);
});

// 当data为URLSearchParams对象时设置为application/x-www-form-urlencoded;charset=utf-8
// 当data为普通对象时，会被设置为application/json;charset=utf-8

// 导出工具函数
export { getTranslationTimeout };

export default {
  post(url, params = {}, isAdmin = false, json = true) {
    if (params === null || typeof params !== 'object') {
      params = {};
    }
    let config = {
      isAdmin: isAdmin,
      headers: {}
    };
    
    const token = isAdmin ? localStorage.getItem("adminToken") : localStorage.getItem("userToken");
    
    if (token) {
      config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }

    // 如果不是json格式，将参数转换为URLSearchParams
    const data = json ? params : new URLSearchParams(params);

    return new Promise((resolve, reject) => {
      axios
        .post(url, data, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  get(url, params = {}, isAdmin = false) {
    // 防御：如果调用方传入 null / 非对象，转为空对象，避免 axios 1.x toFormData 报错
    if (params === null || typeof params !== 'object') {
      params = {};
    }
    // Axios 1.x 要求 headers 必须是纯对象，不能为 undefined。
    // 先生成基础配置，然后在有 token 时再补充 headers，避免 "target must be an object" 错误。
    const config = {
      params,
      isAdmin,
      headers: {}
    };

    const token = isAdmin
      ? localStorage.getItem("adminToken")
      : localStorage.getItem("userToken");

    if (token) {
      config.headers.Authorization = token.startsWith("Bearer ") ? token : `Bearer ${token}`;
    }

    return new Promise((resolve, reject) => {
      axios
        .get(url, config)
        .then((res) => resolve(res.data))
        .catch((err) => reject(err));
    });
  },

  upload(url, param, isAdmin = false, option) {
    const token = isAdmin ? localStorage.getItem("adminToken") : localStorage.getItem("userToken");
    let config = {
      headers: {"Content-Type": "multipart/form-data"},
      timeout: 60000
    };
    
    // 正确处理Authorization头，添加Bearer前缀
    if (token) {
      config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }
    if (typeof option !== "undefined") {
      config.onUploadProgress = progressEvent => {
        if (progressEvent.total > 0) {
          progressEvent.percent = progressEvent.loaded / progressEvent.total * 100;
        }
        option.onProgress(progressEvent);
      };
    }

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  uploadQiniu(url, param) {
    let config = {
      headers: {"Content-Type": "multipart/form-data"},
      timeout: 60000
    };

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
