import {createApp} from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import {
  create,
  NAvatar,
  NInput,
  NIcon,
  NTag,
  NDivider,
  NButton,
  NDrawer,
  NCard,
  NTabs,
  NTabPane,
  NSwitch,
  NModal,
  NBadge,
  NPopover,
  NImage,
  NPopconfirm
} from 'naive-ui'

import {
  ElUpload,
  ElButton,
  ElRadioGroup,
  ElRadioButton
} from 'element-plus'
import 'element-plus/dist/index.css'

import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'
// 导入字体加载器
import { loadFonts } from './utils/font-loader'

import 'vfonts/FiraCode.css'
import './assets/css/index.css'
import './assets/css/color.css'
import './assets/css/animation.css'

const naive = create({
  components: [NAvatar, NInput, NIcon, NTag, NDivider, NButton,
    NDrawer, NCard, NTabs, NTabPane, NSwitch, NModal, NBadge,
    NPopover, NImage, NPopconfirm]
})

const app = createApp(App)
app.use(router)
app.use(store)
app.use(naive)

app.component(ElUpload.name, ElUpload)
app.component(ElButton.name, ElButton)
app.component(ElRadioGroup.name, ElRadioGroup)
app.component(ElRadioButton.name, ElRadioButton)

app.config.globalProperties.$http = http
app.config.globalProperties.$common = common
app.config.globalProperties.$constant = constant

// 创建事件总线
app.config.globalProperties.$bus = {
  // 简单的事件总线实现
  _events: {},
  $on(event, callback) {
    if (!this._events[event]) {
      this._events[event] = [];
    }
    this._events[event].push(callback);
    return this;
  },
  $off(event, callback) {
    if (!this._events[event]) return this;
    if (!callback) {
      this._events[event] = [];
      return this;
    }
    this._events[event] = this._events[event].filter(fn => fn !== callback);
    return this;
  },
  $emit(event, ...args) {
    if (!this._events[event]) return this;
    const cbs = [...this._events[event]];
    cbs.forEach(cb => cb(...args));
    return this;
  }
};

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    if (to.path === "/") {
      // 保存存储类型配置
      if (typeof to.query.defaultStoreType !== "undefined") {
        localStorage.setItem("defaultStoreType", to.query.defaultStoreType);
      }
      
      // 如果URL中包含用户信息和系统配置，保存到localStorage（开发环境跨端口）
      console.log('[IM路由] 检查URL中的userInfo参数:', to.query.userInfo ? '存在' : '不存在');
      if (to.query.userInfo) {
        try {
          console.log('[IM路由] userInfo参数长度:', to.query.userInfo.length);
          const userInfo = JSON.parse(decodeURIComponent(to.query.userInfo));
          console.log('[IM路由] 从URL接收到用户信息:', userInfo);
          store.commit("loadCurrentUser", userInfo);
          
          // 同时保存 accessToken 到 localStorage，供后续 HTTP 请求使用
          if (userInfo.accessToken) {
            localStorage.setItem("userToken", userInfo.accessToken);
            console.log('[IM路由] 已保存 accessToken 到 localStorage');
          }
          
          console.log('[IM路由] 用户信息已保存到store，当前store.state.currentUser:', store.state.currentUser);
        } catch (e) {
          console.error('[IM路由] 解析用户信息失败:', e);
          console.error('[IM路由] 原始userInfo参数:', to.query.userInfo);
        }
      } else {
        console.warn('[IM路由] URL中没有userInfo参数，将使用空的用户信息');
      }
      
      if (to.query.sysConfig) {
        try {
          const sysConfig = JSON.parse(decodeURIComponent(to.query.sysConfig));
          console.log('[IM路由] 从URL接收到系统配置');
          store.commit("loadSysConfig", sysConfig);
        } catch (e) {
          console.error('[IM路由] 解析系统配置失败:', e);
        }
      }
      
      // 检查 currentUser 是否成功加载
      console.log('[IM路由] 准备进入页面，检查当前用户状态:');
      console.log('[IM路由] store.state.currentUser:', store.state.currentUser);
      console.log('[IM路由] localStorage.currentUser:', localStorage.getItem("currentUser"));
      
      // 支持 token 和 userToken 两种参数名
      const urlToken = to.query.token || to.query.userToken;
      console.log('[IM路由] URL参数:', to.query);
      console.log('[IM路由] 获取到的token:', urlToken);
      console.log('[IM路由] localStorage中的userToken:', localStorage.getItem("userToken"));
      
      if (typeof urlToken !== "undefined") {
        console.log('[IM路由] 使用URL中的WebSocket token进行验证...');
        console.log('[IM路由] WebSocket Token:', urlToken);
        
        // 判断是否是WebSocket token（以ws_token_开头）
        if (urlToken.startsWith('ws_token_')) {
          console.log('[IM路由] 这是WebSocket token，使用validateWsToken接口验证');
          const xhr = new XMLHttpRequest();
          xhr.open('get', constant.baseURL + "/im/validateWsToken?wsToken=" + encodeURIComponent(urlToken), false);
          xhr.send();
          let result = JSON.parse(xhr.responseText);
          console.log('[IM路由] WebSocket token验证结果:', result);
          
          if (!common.isEmpty(result) && result.code === 200 && result.data === true) {
            console.log('[IM路由] WebSocket token验证成功');
            localStorage.setItem("wsToken", urlToken);
            
            // 用户信息已经通过URL参数传递并保存，直接进入
            console.log('[IM路由] 进入聊天室');
            next();
          } else {
            console.log('[IM路由] WebSocket token验证失败，跳回博客首页');
            window.location.href = constant.webBaseURL;
          }
        } else {
          // 普通用户token（兼容旧逻辑）
          console.log('[IM路由] 这是用户token，使用/user/token接口验证');
          const xhr = new XMLHttpRequest();
          xhr.open('post', constant.baseURL + "/user/token", false);
          xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
          xhr.send("userToken=" + urlToken);
          let result = JSON.parse(xhr.responseText);
          console.log('[IM路由] 用户token验证结果:', result);
          
          if (!common.isEmpty(result) && result.code === 200) {
            store.commit("loadCurrentUser", result.data);
            localStorage.setItem("userToken", result.data.accessToken);
            console.log('[IM路由] 用户token验证成功');
            next();
          } else {
            console.log('[IM路由] 用户token验证失败，跳回博客首页');
            window.location.href = constant.webBaseURL;
          }
        }
      } else if (Boolean(localStorage.getItem("userToken")) || Boolean(localStorage.getItem("wsToken"))) {
        console.log('[IM路由] 使用localStorage中的token，直接进入');
        next();
      } else {
        console.log('[IM路由] 没有token，跳回博客首页');
        window.location.href = constant.webBaseURL;
      }
    } else {
      if (Boolean(localStorage.getItem("userToken"))) {
        next();
      } else {
        console.log('[IM路由] 非首页且无token，跳回博客首页');
        window.location.href = constant.webBaseURL;
      }
    }
  } else {
    next();
  }
})

// 添加全局错误处理器
app.config.errorHandler = (err, vm, info) => {
  // 检查是否为appendChild错误
  if (err.message && (err.message.includes('appendChild') || err.message.includes('node type'))) {
    console.warn('非关键DOM操作错误已被捕获（不影响功能）:', err.message);
    // 不进一步处理，避免影响用户体验
    return;
  }
  
  // 其他错误正常记录
  console.error('应用错误:', err);
  console.error('错误信息:', info);
};

// 捕获未处理的Promise错误
window.addEventListener('unhandledrejection', event => {
  console.warn('未处理的Promise错误:', event.reason);
  // 如果是appendChild相关错误，防止进一步传播
  if (event.reason && event.reason.toString && 
      (event.reason.toString().includes('appendChild') || 
       event.reason.toString().includes('node type'))) {
    console.warn('非关键Promise错误已被捕获（不影响功能）');
    event.preventDefault();
  }
});

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

app.mount('#app')
