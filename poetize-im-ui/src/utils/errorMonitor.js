/**
 * 生产环境错误监控工具
 * 提供详细的错误日志记录和分析功能
 */

class ErrorMonitor {
  constructor() {
    this.errors = [];
    this.maxErrors = 100; // 最多保存100个错误
    this.init();
  }

  init() {
    // 监听Vue错误
    this.setupVueErrorHandler();
    // 监听全局JavaScript错误
    this.setupGlobalErrorHandler();
    // 监听Promise错误
    this.setupPromiseErrorHandler();
    // 监听资源加载错误
    this.setupResourceErrorHandler();
  }

  setupVueErrorHandler() {
    // 这个方法会在main.js中被调用
  }

  setupGlobalErrorHandler() {
    window.addEventListener('error', (event) => {
      const errorInfo = {
        type: 'JavaScript Error',
        message: event.message,
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
        stack: event.error ? event.error.stack : null,
        timestamp: new Date().toISOString(),
        userAgent: navigator.userAgent,
        url: window.location.href
      };
      
      this.logError(errorInfo);
    });
  }

  setupPromiseErrorHandler() {
    window.addEventListener('unhandledrejection', (event) => {
      const errorInfo = {
        type: 'Unhandled Promise Rejection',
        message: event.reason ? event.reason.message || event.reason : 'Unknown promise rejection',
        stack: event.reason ? event.reason.stack : null,
        timestamp: new Date().toISOString(),
        userAgent: navigator.userAgent,
        url: window.location.href
      };
      
      this.logError(errorInfo);
    });
  }

  setupResourceErrorHandler() {
    window.addEventListener('error', (event) => {
      if (event.target !== window) {
        const errorInfo = {
          type: 'Resource Loading Error',
          message: `Failed to load ${event.target.tagName}: ${event.target.src || event.target.href}`,
          element: event.target.tagName,
          source: event.target.src || event.target.href,
          timestamp: new Date().toISOString(),
          userAgent: navigator.userAgent,
          url: window.location.href
        };
        
        this.logError(errorInfo);
      }
    }, true);
  }

  logError(errorInfo) {
    // 添加到错误列表
    this.errors.unshift(errorInfo);
    if (this.errors.length > this.maxErrors) {
      this.errors.pop();
    }

    // 控制台输出详细错误信息
    console.group(`🚨 ${errorInfo.type}`);
    console.error('错误信息:', errorInfo.message);
    if (errorInfo.filename) {
      console.error('文件位置:', `${errorInfo.filename}:${errorInfo.lineno}:${errorInfo.colno}`);
    }
    if (errorInfo.stack) {
      console.error('错误堆栈:', errorInfo.stack);
    }
    if (errorInfo.element) {
      console.error('元素类型:', errorInfo.element);
      console.error('资源地址:', errorInfo.source);
    }
    console.error('发生时间:', new Date(errorInfo.timestamp).toLocaleString());
    console.error('页面地址:', errorInfo.url);
    console.error('浏览器信息:', errorInfo.userAgent);
    console.groupEnd();

    // 存储到localStorage（可选）
    this.saveToStorage();
  }

  saveToStorage() {
    try {
      const errorData = {
        errors: this.errors.slice(0, 10), // 只保存最近10个错误
        lastUpdate: new Date().toISOString()
      };
      localStorage.setItem('app_errors', JSON.stringify(errorData));
    } catch (e) {
      console.warn('无法保存错误信息到localStorage:', e);
    }
  }

  getErrors() {
    return this.errors;
  }

  clearErrors() {
    this.errors = [];
    localStorage.removeItem('app_errors');
    console.log('错误日志已清空');
  }

  getErrorSummary() {
    const summary = {
      total: this.errors.length,
      byType: {},
      recent: this.errors.slice(0, 5)
    };

    this.errors.forEach(error => {
      summary.byType[error.type] = (summary.byType[error.type] || 0) + 1;
    });

    return summary;
  }

  // 手动记录错误
  recordError(error, context = '') {
    const errorInfo = {
      type: 'Manual Error',
      message: error.message || error,
      stack: error.stack,
      context: context,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href
    };
    
    this.logError(errorInfo);
  }
}

// 创建全局实例
const errorMonitor = new ErrorMonitor();

// 在控制台提供调试方法
window.errorMonitor = {
  getErrors: () => errorMonitor.getErrors(),
  clearErrors: () => errorMonitor.clearErrors(),
  getSummary: () => errorMonitor.getErrorSummary(),
  record: (error, context) => errorMonitor.recordError(error, context)
};

// 输出使用说明
console.log('%c错误监控已启用', 'color: #4CAF50; font-weight: bold;');
console.log('%c使用方法:', 'color: #2196F3; font-weight: bold;');
console.log('• errorMonitor.getErrors() - 获取所有错误');
console.log('• errorMonitor.getSummary() - 获取错误摘要');
console.log('• errorMonitor.clearErrors() - 清空错误日志');
console.log('• errorMonitor.record(error, context) - 手动记录错误');

export default errorMonitor;