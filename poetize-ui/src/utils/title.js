// 使用window.OriginTitile，这样其他组件可以更新它
window.OriginTitile = document.title;
let titleTime;
let enableDynamicTitle = true; // 默认启用动态标题

// 尝试从缓存获取网站配置
try {
  const cachedWebInfo = JSON.parse(localStorage.getItem('webInfo'));
  if (cachedWebInfo && cachedWebInfo.data) {
    // 更新网站标题
    if (cachedWebInfo.data.webTitle) {
      window.OriginTitile = cachedWebInfo.data.webTitle;
      document.title = window.OriginTitile;
    }
    // 检查是否启用动态标题
    if (cachedWebInfo.data.hasOwnProperty('enableDynamicTitle')) {
      enableDynamicTitle = cachedWebInfo.data.enableDynamicTitle;
    }
  }
} catch (e) {
  console.error("获取缓存配置失败:", e);
}

// 只有启用动态标题时才添加事件监听器
if (enableDynamicTitle) {
  document.addEventListener("visibilitychange", (function () {
    document.hidden ? (document.title = "w(ﾟДﾟ)w 不要走！再看看嘛！", clearTimeout(titleTime)) : (document.title = "♪(^∇^*)欢迎肥来！", titleTime = setTimeout((function () {
      document.title = window.OriginTitile
    }), 2e3))
  }));
}
