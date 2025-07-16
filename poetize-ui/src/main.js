import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'
import mavonEditor from 'mavon-editor'
// 导入字体加载器
import { loadFonts } from './utils/font-loader'

//引入js
// 优化Live2D加载逻辑，功能关闭时完全不加载任何资源
  window.addEventListener('load', () => {
    setTimeout(() => {
      // 检查看板娘是否启用
     const checkWaifuEnabled = () => {
       try {
         // 从本地存储获取配置
         const webInfoStr = localStorage.getItem('webInfo');
         if (webInfoStr) {
           const webInfoData = JSON.parse(webInfoStr);
           // 只检查新格式
           if (webInfoData.data) {
             return webInfoData.data.enableWaifu === true;
           }
         }
         return store.state.webInfo.enableWaifu === true;
       } catch (e) {
         console.error('检查看板娘状态出错:', e);
         return false;
       }
     };
      
    // 只在启用状态下加载看板娘的CSS和JS
      if (checkWaifuEnabled()) {
      console.log('看板娘功能已启用，开始加载资源');
      
      // 1. 加载CSS文件
      const loadCss = (url) => {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = url;
        document.head.appendChild(link);
      };
      
      loadCss(constant.live2d_path + 'waifu.css');
      
      // 2. 动态加载JS
        import('./utils/live2d').then(() => {
          console.log('看板娘加载成功');
          
          // 注册刷新页面时的事件处理
          window.addEventListener('pageshow', (event) => {
            // 如果是从缓存加载的页面，检查看板娘状态
            if (event.persisted) {
              console.log('页面从缓存恢复，检查看板娘状态');
              // 触发看板娘检查事件
              document.dispatchEvent(new Event('checkWaifu'));
            }
          });
          
          // 监听路由变化，确保在切换页面时看板娘正常显示
          const checkWaifuOnRouteChange = () => {
            setTimeout(() => {
              document.dispatchEvent(new Event('checkWaifu'));
            }, 1000);
          };
          
          // 监听路由变化和历史记录变化
          window.addEventListener('popstate', checkWaifuOnRouteChange);
          
          // 监听vue-router的路由变化
          if (router.afterEach) {
            router.afterEach((to, from) => {
              if (to.path !== from.path) {
                checkWaifuOnRouteChange();
              }
            });
          }
        }).catch(error => {
          console.warn('看板娘加载失败，但不影响主要功能:', error);
        });
      } else {
      console.log('看板娘功能已禁用，跳过所有相关资源的加载');
      }
  }, 500); // 将延迟从2000毫秒减少到500毫秒，加快页面加载
  });

import './utils/title'
//引入css
import './assets/css/animation.css'
import './assets/css/index.css'
import './assets/css/tocbot.css'
import './assets/css/color.css'
import './assets/css/markdown-highlight.css'
import './assets/css/font-awesome.min.css'
import 'mavon-editor/dist/css/index.css'

import {vueBaberrage} from 'vue-baberrage'
import AsyncNotification from './components/common/AsyncNotification.vue'

Vue.use(ElementUI)
Vue.use(vueBaberrage)
Vue.use(mavonEditor)

// 全局注册异步通知组件
Vue.component('AsyncNotification', AsyncNotification)

Vue.prototype.$http = http
Vue.prototype.$common = common
Vue.prototype.$constant = constant

// 创建事件总线
Vue.prototype.$bus = new Vue();

// 创建全局通知实例（用于非组件环境调用）
let globalNotificationInstance = null;
Vue.prototype.$notify = {
  // 获取或创建通知实例
  getInstance() {
    if (!globalNotificationInstance) {
      // 如果还没有实例，返回一个占位对象
      return {
        addNotification: () => console.warn('通知组件尚未初始化'),
        updateNotificationByTaskId: () => console.warn('通知组件尚未初始化'),
        removeNotification: () => console.warn('通知组件尚未初始化'),
        clearAllNotifications: () => console.warn('通知组件尚未初始化')
      };
    }
    return globalNotificationInstance;
  },
  
  // 设置全局实例
  setInstance(instance) {
    globalNotificationInstance = instance;
  },
  
  // 便捷方法
  loading(title, message, taskId) {
    const notificationId = this.getInstance().addNotification({
      title,
      message,
      type: 'loading',
      duration: 0,
      taskId
    });
    
    // 如果有taskId，自动启动轮询
    if (taskId && this.getInstance().startPolling) {
      console.log('自动启动轮询，任务ID:', taskId);
      this.getInstance().startPolling(taskId);
    }
    
    return notificationId;
  },
  
  success(title, message, duration = 2000) {
    return this.getInstance().addNotification({
      title,
      message,
      type: 'success',
      duration
    });
  },
  
  error(title, message, duration = 5000) {
    return this.getInstance().addNotification({
      title,
      message,
      type: 'error',
      duration
    });
  },
  
  info(title, message, duration = 3000) {
    return this.getInstance().addNotification({
      title,
      message,
      type: 'info',
      duration
    });
  },
  
  updateByTaskId(taskId, updates) {
    return this.getInstance().updateNotificationByTaskId(taskId, updates);
  }
};

// 添加全局错误处理
Vue.config.errorHandler = (err, vm, info) => {
  // 检查是否为appendChild错误
  if (err.message && (err.message.includes('appendChild') || err.message.includes('node type'))) {
    console.warn('非关键DOM操作错误已被捕获（不影响功能）:', err.message);
    // 不进一步处理，避免影响用户体验
    return;
  }
  
  // 对看板娘特定错误进行处理
  if (err.message && err.message.includes('live2d')) {
    console.warn('看板娘相关错误已捕获，不影响系统使用');
    return;
  }
  
  // 其他错误正常记录
  console.error('Vue错误:', err);
  console.error('错误信息:', info);
};

// 捕获未处理的Promise错误
window.addEventListener('unhandledrejection', event => {
  console.warn('未处理的Promise错误:', event.reason);
  
  // 处理多种可能的非关键错误
  const errorStr = event.reason && event.reason.toString ? event.reason.toString() : '';
  
  // 处理DOM相关错误
  if (errorStr.includes('appendChild') || errorStr.includes('node type')) {
    console.warn('非关键DOM操作Promise错误已捕获（不影响功能）');
    event.preventDefault();
    return;
  }
  
  // 处理看板娘错误
  if (errorStr.includes('live2d')) {
    console.warn('看板娘相关Promise错误已捕获，不影响系统使用');
    event.preventDefault();
  }
});

Vue.config.productionTip = false

// 灰度模式：全局开关与侦听
function applyGrayMode() {
  try {
    const enable = store.state.webInfo && store.state.webInfo.enableGrayMode;
    const rootEl = document.documentElement;
    if (enable) {
      rootEl.classList.add('gray-mode');
      // 保险：构建后若 gray-mode 样式被裁剪，直接设置滤镜
      rootEl.style.filter = 'grayscale(100%)';
    } else {
      rootEl.classList.remove('gray-mode');
      rootEl.style.filter = '';
    }
  } catch (e) {
    console.warn('灰度模式应用失败:', e);
  }
}

// 初次进入时尝试应用
applyGrayMode();

// 监听 webInfo 的灰度开关变化
store.watch(
  (state) => state.webInfo && state.webInfo.enableGrayMode,
  () => {
    // 当开关变化时重新应用
    applyGrayMode();
  },
  { immediate: false }
);

// 初始加载字体
if (store.state.sysConfig) {
  loadFonts(store.state.sysConfig).catch(err => {
    console.error('加载字体失败:', err);
  });
}

// 监听系统配置变化，重新加载字体
store.watch(
  (state) => state.sysConfig,
  (newConfig) => {
    if (newConfig) {
      console.log('系统配置更新，重新加载字体...');
      loadFonts(newConfig).catch(err => {
        console.error('加载字体失败:', err);
      });
    }
  },
  { deep: true }
);

const app = new Vue({
  router,
  store,
  render: h => h(App)
})

// 通过检查 window.PRERENDER_DATA 来判断是否需要注水
if (window.PRERENDER_DATA) {
  // 注水模式：平滑接管已有的DOM
  app.$mount('#app', true)
  console.log('客户端注水模式启动')
} else {
  // 正常挂载模式：用于开发环境或非预渲染页面
  app.$mount('#app')
  console.log('客户端正常挂载模式启动')
}

// Vue应用挂载完成后触发事件，用于防止FOUC
app.$nextTick(() => {
  // 标记Vue应用已完成挂载
  window.dispatchEvent(new CustomEvent('app-mounted', {
    detail: {
      timestamp: Date.now(),
      prerendered: !!window.PRERENDER_DATA,
      mountType: window.PRERENDER_DATA ? 'hydration' : 'normal'
    }
  }));
  
  // 添加样式切换类，确保平滑过渡
  const appElement = document.getElementById('app');
  if (appElement) {
    appElement.classList.add('vue-mounted');
  }
  
  // 处理图片加载状态（包括预渲染和正常页面）
  const handleAllImages = () => {
    const images = document.querySelectorAll('img');
    images.forEach(img => {
      // 立即处理已完成加载的图片
      if (img.complete && img.naturalWidth > 0) {
        img.classList.add('loaded');
      } else if (img.src.startsWith('data:') || img.src.startsWith('blob:')) {
        // data URL 和 blob URL 立即标记为已加载
        img.classList.add('loaded');
      } else if (img.src) {
        // 为未完成加载的图片添加事件监听
        img.addEventListener('load', function() {
          this.classList.add('loaded');
        }, { once: true });
        img.addEventListener('error', function() {
          this.classList.add('loaded');
          console.warn('Vue应用中图片加载失败:', this.src);
        }, { once: true });
        
        // 设置超时确保图片不会永远隐藏
        setTimeout(() => {
          if (!img.classList.contains('loaded')) {
            img.classList.add('loaded');
            console.warn('Vue应用中图片加载超时，强制显示:', img.src);
          }
        }, 3000); // 3秒超时（比预渲染的短一些）
      } else {
        // 没有src的图片直接标记为已加载
        img.classList.add('loaded');
      }
    });
  };
  
  // 立即处理当前图片
  handleAllImages();
  
  // 监听动态添加的图片
  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.type === 'childList') {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType === 1) { // 元素节点
            if (node.tagName === 'IMG') {
              // 新添加的img元素
              const img = node;
              if (img.complete && img.naturalWidth > 0) {
                img.classList.add('loaded');
              } else if (img.src && !img.src.startsWith('data:') && !img.src.startsWith('blob:')) {
                img.addEventListener('load', function() {
                  this.classList.add('loaded');
                }, { once: true });
                img.addEventListener('error', function() {
                  this.classList.add('loaded');
                }, { once: true });
              } else {
                img.classList.add('loaded');
              }
            } else {
              // 检查新添加元素内部的img
              const nestedImages = node.querySelectorAll ? node.querySelectorAll('img') : [];
              nestedImages.forEach(img => {
                if (!img.classList.contains('loaded')) {
                  if (img.complete && img.naturalWidth > 0) {
                    img.classList.add('loaded');
                  } else if (img.src && !img.src.startsWith('data:') && !img.src.startsWith('blob:')) {
                    img.addEventListener('load', function() {
                      this.classList.add('loaded');
                    }, { once: true });
                    img.addEventListener('error', function() {
                      this.classList.add('loaded');
                    }, { once: true });
                  } else {
                    img.classList.add('loaded');
                  }
                }
              });
            }
          }
        });
      }
    });
  });
  
  // 开始观察DOM变化
  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
  
  if (window.PRERENDER_DATA) {
    console.log('预渲染页面客户端接管完成，FOUC防护已激活');
  }
});
