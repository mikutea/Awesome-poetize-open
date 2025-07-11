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
  ElRadioButton,
  ElMessage
} from 'element-plus'
import 'element-plus/dist/index.css'

import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'
import errorMonitor from './utils/errorMonitor'

// 生产环境错误监控和日志记录
function setupErrorHandling(app) {
  // Vue全局错误处理器
  app.config.errorHandler = (err, instance, info) => {
    // 使用错误监控工具记录
    const errorInfo = {
      type: 'Vue Component Error',
      message: err.message,
      name: err.name,
      stack: err.stack,
      componentInfo: info,
      componentName: instance ? instance.$options.name || instance.$options.__name : 'Unknown',
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href
    };
    
    errorMonitor.recordError(errorInfo, `Vue组件错误 - ${info}`);
    
    // 发送错误到控制台（生产环境可见）
    console.group('🚨 Vue应用错误详情');
    console.error('错误信息:', err.message);
    console.error('错误类型:', err.name);
    console.error('组件信息:', info);
    console.error('组件名称:', errorInfo.componentName);
    console.error('错误堆栈:', err.stack);
    console.error('发生时间:', new Date().toLocaleString());
    console.groupEnd();
  };
  
  // 注意：全局JavaScript错误、Promise错误和资源加载错误
  // 已经由errorMonitor自动处理，无需重复添加监听器
  
  console.log('%c生产环境错误监控已启用', 'color: #4CAF50; font-weight: bold; font-size: 14px;');
  console.log('%c所有错误将被详细记录到控制台，便于生产环境调试', 'color: #2196F3;');
}

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

// 初始化错误监控工具
errorMonitor.init();

// 设置错误处理
setupErrorHandling(app)

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
app.config.globalProperties.$message = ElMessage

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    if (to.path === "/") {
      if (typeof to.query.defaultStoreType !== "undefined") {
        localStorage.setItem("defaultStoreType", to.query.defaultStoreType);
      }
      if (typeof to.query.userToken !== "undefined") {
        let userToken = to.query.userToken;
        const xhr = new XMLHttpRequest();
        xhr.open('post', "/user/token", false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.send("userToken=" + userToken);
        let result = JSON.parse(xhr.responseText);
        if (!common.isEmpty(result) && result.code === 200) {
          store.commit("loadCurrentUser", result.data);
          localStorage.setItem("userToken", result.data.accessToken);
          window.location.href = constant.imURL;
          next();
        } else {
          window.location.href = constant.webBaseURL;
        }
      } else if (Boolean(localStorage.getItem("userToken"))) {
        next();
      } else {
        window.location.href = constant.webBaseURL;
      }
    } else {
      if (Boolean(localStorage.getItem("userToken"))) {
        next();
      } else {
        window.location.href = constant.webBaseURL;
      }
    }
  } else {
    next();
  }
})

app.mount('#app')
