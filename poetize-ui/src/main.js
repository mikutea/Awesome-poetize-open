import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'
import mavonEditor from 'mavon-editor'
//引入js
// 使用try-catch包裹live2d加载，防止加载失败
try {
  // 确保CSS文件加载
  const loadCss = (url) => {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = url;
    document.head.appendChild(link);
  };
  
  // 确保基本的看板娘样式已加载
  loadCss(constant.live2d_path + 'waifu.css');
  
  // 延迟加载live2d，确保核心内容优先加载
  window.addEventListener('load', () => {
    setTimeout(() => {
      // 检查看板娘是否启用
      const checkWaifuEnabled = () => {
        try {
          // 从本地存储获取配置
          const webInfoStr = localStorage.getItem('webInfo');
          if (webInfoStr) {
            const webInfoData = JSON.parse(webInfoStr);
            // 处理两种可能的数据格式
            if (webInfoData.data && webInfoData.data.enableWaifu !== undefined) {
              return webInfoData.data.enableWaifu === true;
            } else if (webInfoData.enableWaifu !== undefined) {
              return webInfoData.enableWaifu === true;
            }
          }
          return store.state.webInfo.enableWaifu === true;
        } catch (e) {
          console.error('检查看板娘状态出错:', e);
          return false;
        }
      };
      
      // 只在启用状态下加载看板娘
      if (checkWaifuEnabled()) {
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
        console.log('看板娘功能已禁用，跳过加载');
      }
    }, 2000); // 延迟2秒加载
  });
} catch (err) {
  console.warn('看板娘初始化失败，但不影响主要功能:', err);
}
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

Vue.use(ElementUI)
Vue.use(vueBaberrage)
Vue.use(mavonEditor)

Vue.prototype.$http = http
Vue.prototype.$common = common
Vue.prototype.$constant = constant

// 创建事件总线
Vue.prototype.$bus = new Vue();

// 添加全局错误处理
Vue.config.errorHandler = (err, vm, info) => {
  console.error('Vue错误:', err);
  // 对特定错误进行处理
  if (err.message && err.message.includes('live2d')) {
    console.warn('看板娘相关错误已捕获，不影响系统使用');
  }
};

// 捕获未处理的Promise错误
window.addEventListener('unhandledrejection', event => {
  console.warn('未处理的Promise错误:', event.reason);
  // 忽略特定错误
  if (event.reason && event.reason.toString().includes('live2d')) {
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
