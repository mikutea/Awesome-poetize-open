const express = require('express');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');
const axios = require('axios');
const MarkdownIt = require('markdown-it');
const hljs = require('highlight.js');
const cheerio = require('cheerio');
const { decode: decodeHtmlEntities } = require('html-entities');

const app = express();
app.use(bodyParser.json());

// ===== 日志系统和监控 =====
class Logger {
  constructor() {
    this.logLevel = process.env.LOG_LEVEL || 'info';
    this.levels = { error: 0, warn: 1, info: 2, debug: 3 };
    
    // 日志文件配置
    this.logDir = '/app/dist/logs';
    this.ensureLogDirectory();
    
    // 内存日志缓存（用于实时查看）
    this.memoryLogs = [];
    this.maxMemoryLogs = 500;
    
    // 日志清理配置
    this.logRetentionDays = parseInt(process.env.LOG_RETENTION_DAYS) || 30; // 默认保留30天
    this.cleanupInterval = 24 * 60 * 60 * 1000; // 每天清理一次
    
    // 启动定时清理
    this.startLogCleanup();
  }

  ensureLogDirectory() {
    try {
      if (!fs.existsSync(this.logDir)) {
        fs.mkdirSync(this.logDir, { recursive: true });
      }
    } catch (error) {
      console.error('Failed to create log directory:', error);
    }
  }

  formatLog(level, message, meta = {}) {
    const timestamp = new Date().toISOString();
    const logEntry = {
      timestamp,
      level,
      message,
      service: 'prerender-worker',
      requestId: meta.requestId || 'unknown',
      ...meta
    };
    return logEntry;
  }

  writeToFile(logEntry) {
    try {
      const today = new Date().toISOString().split('T')[0];
      const logFile = path.join(this.logDir, `prerender-${today}.log`);
      const logLine = JSON.stringify(logEntry) + '\n';
      fs.appendFileSync(logFile, logLine);
    } catch (error) {
      console.error('Failed to write log to file:', error);
    }
  }

  addToMemory(logEntry) {
    this.memoryLogs.unshift(logEntry);
    if (this.memoryLogs.length > this.maxMemoryLogs) {
      this.memoryLogs = this.memoryLogs.slice(0, this.maxMemoryLogs);
    }
  }

  shouldLog(level) {
    return this.levels[level] <= this.levels[this.logLevel];
  }

  log(level, message, meta = {}) {
    if (!this.shouldLog(level)) return;
    
    const logEntry = this.formatLog(level, message, meta);
    
    // 写入文件
    this.writeToFile(logEntry);
    
    // 添加到内存
    this.addToMemory(logEntry);
    
    // 控制台输出
    const logString = JSON.stringify(logEntry);
    switch (level) {
      case 'error':
        console.error(logString);
        break;
      case 'warn':
        console.warn(logString);
        break;
      case 'info':
        console.info(logString);
        break;
      case 'debug':
        console.log(logString);
        break;
    }
  }

  error(message, meta = {}) {
    this.log('error', message, meta);
  }

  warn(message, meta = {}) {
    this.log('warn', message, meta);
  }

  info(message, meta = {}) {
    this.log('info', message, meta);
  }

  debug(message, meta = {}) {
    this.log('debug', message, meta);
  }

  // 获取内存中的日志
  getMemoryLogs(limit = 100) {
    return this.memoryLogs.slice(0, limit);
  }

  // 获取日志文件列表
  getLogFiles() {
    try {
      const files = fs.readdirSync(this.logDir)
        .filter(file => file.startsWith('prerender-') && file.endsWith('.log'))
        .map(file => {
          const filePath = path.join(this.logDir, file);
          const stats = fs.statSync(filePath);
          return {
            name: file,
            path: filePath,
            size: stats.size,
            modified: stats.mtime
          };
        })
        .sort((a, b) => b.modified - a.modified);
      return files;
    } catch (error) {
      console.error('Failed to get log files:', error);
      return [];
    }
  }

  // 读取日志文件
  readLogFile(filename, lines = 1000) {
    try {
      const filePath = path.join(this.logDir, filename);
      if (!fs.existsSync(filePath)) {
        return [];
      }
      
      const content = fs.readFileSync(filePath, 'utf8');
      const logLines = content.trim().split('\n').filter(line => line.trim());
      
      // 解析日志行
      const logs = logLines.slice(-lines).map(line => {
        try {
          return JSON.parse(line);
        } catch (e) {
          return {
            timestamp: new Date().toISOString(),
            level: 'error',
            message: `Failed to parse log line: ${line}`,
            service: 'prerender-worker'
          };
        }
      }).reverse(); // 最新的在前面
      
      return logs;
    } catch (error) {
      console.error('Failed to read log file:', error);
      return [];
    }
  }

  // 启动定时日志清理
  startLogCleanup() {
    // 立即执行一次清理
    this.cleanupOldLogs();
    
    // 设置定时清理
    setInterval(() => {
      this.cleanupOldLogs();
    }, this.cleanupInterval);
    
    console.log(`日志清理已启动，保留${this.logRetentionDays}天的日志文件，每天清理一次`);
  }

  // 清理过期日志文件
  cleanupOldLogs() {
    try {
      const files = fs.readdirSync(this.logDir)
        .filter(file => file.startsWith('prerender-') && file.endsWith('.log'));

      const now = new Date();
      const cutoffDate = new Date(now.getTime() - (this.logRetentionDays * 24 * 60 * 60 * 1000));
      
      let deletedCount = 0;
      let totalSize = 0;

      files.forEach(file => {
        const filePath = path.join(this.logDir, file);
        const stats = fs.statSync(filePath);
        
        // 检查文件是否过期
        if (stats.mtime < cutoffDate) {
          try {
            totalSize += stats.size;
            fs.unlinkSync(filePath);
            deletedCount++;
            console.log(`已删除过期日志文件: ${file} (${(stats.size / 1024).toFixed(1)}KB)`);
          } catch (deleteError) {
            console.error(`删除日志文件失败 ${file}:`, deleteError.message);
          }
        }
      });

      if (deletedCount > 0) {
        console.log(`日志清理完成: 删除了${deletedCount}个文件，释放${(totalSize / 1024 / 1024).toFixed(1)}MB空间`);
        
        // 记录清理日志
        this.log('info', '定时日志清理完成', {
          deletedFiles: deletedCount,
          freedSpace: `${(totalSize / 1024 / 1024).toFixed(1)}MB`,
          retentionDays: this.logRetentionDays
        });
      }
    } catch (error) {
      console.error('清理日志文件时发生错误:', error);
      this.log('error', '日志清理失败', { error: error.message });
    }
  }

  // 手动清理日志文件（用于API调用）
  manualCleanup(retentionDays = null) {
    const originalRetention = this.logRetentionDays;
    if (retentionDays && retentionDays > 0) {
      this.logRetentionDays = retentionDays;
    }
    
    try {
      this.cleanupOldLogs();
      return {
        success: true,
        message: `手动清理完成，保留${this.logRetentionDays}天的日志`
      };
    } catch (error) {
      return {
        success: false,
        message: `手动清理失败: ${error.message}`
      };
    } finally {
      // 恢复原始设置
      this.logRetentionDays = originalRetention;
    }
  }

  // 获取日志磁盘使用情况
  getLogDiskUsage() {
    try {
      const files = fs.readdirSync(this.logDir)
        .filter(file => file.startsWith('prerender-') && file.endsWith('.log'));

      let totalSize = 0;
      let fileCount = 0;
      const oldestFile = { name: '', date: new Date() };
      const newestFile = { name: '', date: new Date(0) };

      files.forEach(file => {
        const filePath = path.join(this.logDir, file);
        const stats = fs.statSync(filePath);
        
        totalSize += stats.size;
        fileCount++;
        
        if (stats.mtime < oldestFile.date) {
          oldestFile.name = file;
          oldestFile.date = stats.mtime;
        }
        
        if (stats.mtime > newestFile.date) {
          newestFile.name = file;
          newestFile.date = stats.mtime;
        }
      });

      return {
        totalSize,
        totalSizeMB: (totalSize / 1024 / 1024).toFixed(1),
        fileCount,
        oldestFile: oldestFile.name || 'N/A',
        newestFile: newestFile.name || 'N/A',
        retentionDays: this.logRetentionDays
      };
    } catch (error) {
      console.error('获取日志磁盘使用情况失败:', error);
      return {
        totalSize: 0,
        totalSizeMB: '0',
        fileCount: 0,
        oldestFile: 'N/A',
        newestFile: 'N/A',
        retentionDays: this.logRetentionDays,
        error: error.message
      };
    }
  }
}

const logger = new Logger();

// ===== 服务状态监控 =====
class ServiceMonitor {
  constructor() {
    this.stats = {
      startTime: new Date(),
      totalRequests: 0,
      successfulRenders: 0,
      failedRenders: 0,
      articlesRendered: 0,
      pagesRendered: 0,
      averageRenderTime: 0,
      lastRenderTime: null,
      errors: [],
      currentTasks: new Map(),
      recentTasks: [], // 添加最近完成的任务历史
      systemHealth: {
        memoryUsage: {},
        uptime: 0,
        templateStatus: 'unknown'
      }
    };
    
    // 定期更新系统信息
    setInterval(() => {
      this.updateSystemHealth();
    }, 30000); // 每30秒更新一次
    
    this.updateSystemHealth();
  }

  updateSystemHealth() {
    this.stats.systemHealth.memoryUsage = process.memoryUsage();
    this.stats.systemHealth.uptime = process.uptime();
    
    // 检查模板状态
    const templatePath = path.resolve('/app/dist/index.html');
    this.stats.systemHealth.templateStatus = fs.existsSync(templatePath) ? 'available' : 'missing';
  }

  recordRequest(type = 'unknown') {
    this.stats.totalRequests++;
    logger.debug('Request recorded', { type, total: this.stats.totalRequests });
  }

  recordRenderStart(taskId, type, params = {}) {
    const task = {
      id: taskId,
      type,
      params,
      startTime: new Date(),
      status: 'running'
    };
    this.stats.currentTasks.set(taskId, task);
    logger.info('Render task started', { 
      taskId, 
      type, 
      params, 
      currentTaskCount: this.stats.currentTasks.size,
      totalRequests: this.stats.totalRequests 
    });
    
    // 调试：打印当前所有任务
    console.log('=== 任务开始 ===');
    console.log(`任务ID: ${taskId}`);
    console.log(`类型: ${type}`);
    console.log(`参数:`, params);
    console.log(`当前运行中任务数: ${this.stats.currentTasks.size}`);
    console.log('==================');
  }

  recordRenderSuccess(taskId, details = {}) {
    const task = this.stats.currentTasks.get(taskId);
    if (task) {
      const duration = new Date() - task.startTime;
      this.stats.successfulRenders++;
      this.stats.lastRenderTime = new Date();
      
      // 更新平均渲染时间
      const totalTime = this.stats.averageRenderTime * (this.stats.successfulRenders - 1) + duration;
      this.stats.averageRenderTime = Math.round(totalTime / this.stats.successfulRenders);
      
      if (task.type === 'article') {
        this.stats.articlesRendered += details.count || 1;
      } else {
        // 页面渲染应该记录实际成功的页面数量
        this.stats.pagesRendered += details.count || 1;
      }
      
      // 添加到任务历史
      const completedTask = {
        taskId: task.id,
        type: task.type,
        status: 'completed',
        startTime: task.startTime.toISOString(),
        endTime: new Date().toISOString(),
        duration: `${duration}ms`,
        params: task.params,
        details
      };
      
      // 确保recentTasks数组存在
      if (!this.stats.recentTasks) {
        this.stats.recentTasks = [];
      }
      this.stats.recentTasks.push(completedTask);
      
      // 只保留最近20个完成的任务
      if (this.stats.recentTasks.length > 20) {
        this.stats.recentTasks = this.stats.recentTasks.slice(-20);
      }
      
      this.stats.currentTasks.delete(taskId);
      logger.info('Render task completed successfully', { 
        taskId, 
        duration: `${duration}ms`, 
        type: task.type,
        currentTaskCount: this.stats.currentTasks.size,
        recentTaskCount: this.stats.recentTasks.length,
        ...details 
      });
      
      // 调试：打印任务完成信息
      console.log('=== 任务完成 ===');
      console.log(`任务ID: ${taskId}`);
      console.log(`类型: ${task.type}`);
      console.log(`耗时: ${duration}ms`);
      console.log(`当前运行中任务数: ${this.stats.currentTasks.size}`);
      console.log(`历史任务数: ${this.stats.recentTasks.length}`);
      console.log(`成功渲染总数: ${this.stats.successfulRenders}`);
      console.log('详情:', details);
      console.log('==================');
    }
  }

  recordRenderFailure(taskId, error) {
    const task = this.stats.currentTasks.get(taskId);
    if (task) {
      const duration = new Date() - task.startTime;
      this.stats.failedRenders++;
      
      const errorRecord = {
        timestamp: new Date(),
        taskId,
        type: task.type,
        error: error.message || error.toString(),
        duration: `${duration}ms`,
        params: task.params
      };
      
      // 确保errors数组存在
      if (!this.stats.errors) {
        this.stats.errors = [];
      }
      this.stats.errors.push(errorRecord);
      
      // 只保留最近50个错误记录
      if (this.stats.errors.length > 50) {
        this.stats.errors = this.stats.errors.slice(-50);
      }
      
      // 添加到任务历史
      const failedTask = {
        taskId: task.id,
        type: task.type,
        status: 'failed',
        startTime: task.startTime.toISOString(),
        endTime: new Date().toISOString(),
        duration: `${duration}ms`,
        params: task.params,
        error: error.message || error.toString()
      };
      
      // 确保recentTasks数组存在
      if (!this.stats.recentTasks) {
        this.stats.recentTasks = [];
      }
      this.stats.recentTasks.push(failedTask);
      
      // 只保留最近20个完成的任务
      if (this.stats.recentTasks.length > 20) {
        this.stats.recentTasks = this.stats.recentTasks.slice(-20);
      }
      
      this.stats.currentTasks.delete(taskId);
      logger.error('Render task failed', errorRecord);
    }
  }

  getStats() {
    const runningTasks = Array.from(this.stats.currentTasks.values()).map(task => ({
      taskId: task.id,  // 前端期望 taskId 字段
      type: task.type,
      status: 'running',  // 前端期望 status 字段
      startTime: task.startTime.toISOString(),  // 前端期望 startTime 字段
      params: task.params,
      duration: `${new Date() - task.startTime}ms`
    }));

    // 确保关键数组存在
    if (!this.stats.recentTasks) {
      this.stats.recentTasks = [];
    }
    if (!this.stats.errors) {
      this.stats.errors = [];
    }

    return {
      ...this.stats,
      currentTasks: runningTasks,
      recentTasks: this.stats.recentTasks, // 包含任务历史
      uptime: `${Math.floor(process.uptime())}s`,
      successRate: this.stats.totalRequests > 0 
        ? Math.round((this.stats.successfulRenders / this.stats.totalRequests) * 100) 
        : 0
    };
  }

  getRecentErrors(limit = 10) {
    // 确保errors数组存在
    if (!this.stats.errors) {
      this.stats.errors = [];
    }
    return this.stats.errors.slice(-limit);
  }

  clearStats() {
    this.stats = {
      startTime: new Date(),
      totalRequests: 0,
      successfulRenders: 0,
      failedRenders: 0,
      articlesRendered: 0,
      pagesRendered: 0,
      averageRenderTime: 0,
      lastRenderTime: null,
      errors: [],
      currentTasks: new Map(),
      recentTasks: [], // 添加最近完成的任务历史
      systemHealth: {
        memoryUsage: {},
        uptime: 0,
        templateStatus: 'unknown'
      }
    };
  }
}

const monitor = new ServiceMonitor();

// ===== 任务ID生成器 =====
function generateTaskId() {
  return `task_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

const JAVA_BACKEND_URL = process.env.JAVA_BACKEND_URL || 'http://poetize-java:8081';
const PYTHON_BACKEND_URL = process.env.PYTHON_BACKEND_URL || 'http://poetize-python:5000';

const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
               hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
               '</code></pre>';
      } catch (_) {}
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>';
  }
});

// 完整版：先尝试 manifest.json，失败则解析 index.html，结果缓存 10 分钟
const assetCache = { assets: null, lastFetch: 0 };
async function getFrontEndAssets(host = 'nginx') {
  const TEN_MIN = 10 * 60 * 1000;
  if (assetCache.assets && Date.now() - assetCache.lastFetch < TEN_MIN) {
    return assetCache.assets;
  }

  // 1. manifest.json
  try {
    const manifestRes = await axios.get(`http://${host}/manifest.json`, { timeout: 3000 });
    if (manifestRes.status === 200 && manifestRes.data) {
      const m = typeof manifestRes.data === 'string' ? JSON.parse(manifestRes.data) : manifestRes.data;
      assetCache.assets = {
        css: m['app.css'] || '/css/app.css',
        js: m['app.js'] || '/js/app.js',
        vendorJs: m['vendor.js'] || null,
        vendorCss: m['vendor.css'] || null
      };
      assetCache.lastFetch = Date.now();
      return assetCache.assets;
    }
  } catch (_) { /* ignore */ }

  // 2. 解析首页 HTML
  try {
    const htmlRes = await axios.get(`http://${host}`, { timeout: 5000 });
    const html = htmlRes.data || '';

    const cssMatch = html.match(/\/(css|static\/css)\/app[^"']+\.css/);
    const jsMatch = html.match(/\/(js|static\/js)\/app[^"']+\.js/);
    const vendorJsMatch = html.match(/\/js\/chunk-vendors[^"']+\.js/);
    const vendorCssMatch = html.match(/\/css\/npm[^"']+\.css/);

    assetCache.assets = {
      css: cssMatch ? cssMatch[0] : '/css/app.css',
      js: jsMatch ? jsMatch[0] : '/js/app.js',
      vendorJs: vendorJsMatch ? vendorJsMatch[0] : null,
      vendorCss: vendorCssMatch ? vendorCssMatch[0] : null
    };
    assetCache.lastFetch = Date.now();
  } catch (_) {
    assetCache.assets = {
      css: '/css/app.css',
      js: '/js/app.js',
      vendorJs: null,
      vendorCss: null
    };
  }

  return assetCache.assets;
}

// ===== 内部服务请求头配置 =====
const INTERNAL_SERVICE_HEADERS = {
  'X-Internal-Service': 'poetize-prerender',
  'User-Agent': 'poetize-prerender/1.0.0'
};

// ===== 文章相关函数 =====
async function fetchArticle(id) {
  try {
    logger.debug('Fetching article', { id });
    const res = await axios.get(`${JAVA_BACKEND_URL}/article/getArticleByIdNoCount`, { 
      params: { id },
      timeout: 10000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const article = (res.data && res.data.data) || null;
    logger.debug('Article fetched', { id, found: !!article });
    return article;
  } catch (error) {
    logger.error('Failed to fetch article', { id, error: error.message, stack: error.stack });
    throw new Error(`Failed to fetch article ${id}: ${error.message}`);
  }
}

async function fetchTranslation(id, lang) {
  if (lang === 'zh') return null;
  try {
    logger.debug('Fetching translation', { id, lang });
    const res = await axios.get(`${JAVA_BACKEND_URL}/article/getTranslation`, { 
      params: { id },
      timeout: 8000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const translation = (res.data && res.data.code === 200) ? res.data.data : null;
    logger.debug('Translation fetched', { id, lang, found: !!translation });
    return translation;
  } catch (error) {
    logger.warn('Failed to fetch translation, using original content', { 
      id, 
      lang, 
      error: error.message 
    });
    return null;
  }
}

async function fetchMeta(id, lang) {
  try {
    logger.debug('Fetching meta', { id, lang });
    const res = await axios.get(`${PYTHON_BACKEND_URL}/python/seo/getArticleMeta`, { 
      params: { id },
      timeout: 5000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const meta = (res.data && res.data.status === 'success') ? (res.data.data || {}) : {};
    logger.debug('Meta fetched', { id, lang, keysCount: Object.keys(meta).length });
    return meta;
  } catch (error) {
    logger.warn('Failed to fetch meta, using defaults', { 
      id, 
      lang, 
      error: error.message 
    });
    return {};
  }
}

// ===== 新增：其他页面数据获取函数 =====

async function fetchWebInfo() {
  try {
    logger.debug('Fetching web info');
    const res = await axios.get(`${JAVA_BACKEND_URL}/webInfo/getWebInfo`, { 
      timeout: 5000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const webInfo = (res.data && res.data.data) || {};
    logger.debug('Web info fetched', { keys: Object.keys(webInfo) });
    return webInfo;
  } catch (error) {
    logger.warn('Failed to fetch web info, using defaults', { error: error.message });
    return {};
  }
}

async function fetchSeoConfig() {
  try {
    logger.debug('Fetching SEO config');
    const res = await axios.get(`${PYTHON_BACKEND_URL}/seo/getSeoConfig`, { 
      timeout: 5000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const seoConfig = (res.data && res.data.code === 200) ? (res.data.data || {}) : {};
    logger.debug('SEO config fetched', { keys: Object.keys(seoConfig) });
    return seoConfig;
  } catch (error) {
    logger.warn('Failed to fetch SEO config, using defaults', { 
      error: error.message, 
      status: error.response?.status,
      statusText: error.response?.statusText 
    });
    return {};
  }
}

async function fetchSortInfo() {
  try {
    logger.debug('Fetching sort info');
    const res = await axios.get(`${JAVA_BACKEND_URL}/sort/listSort`, { 
      timeout: 5000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const sortInfo = (res.data && res.data.data) || [];
    logger.debug('Sort info fetched', { count: sortInfo.length });
    return sortInfo;
  } catch (error) {
    logger.warn('Failed to fetch sort info, using empty array', { error: error.message });
    return [];
  }
}

async function fetchRecentArticles(limit = 5) {
  try {
    logger.debug('Fetching recent articles', { limit });
    const res = await axios.post(`${JAVA_BACKEND_URL}/article/listArticle`, {
      current: 1,
      size: limit
    }, { 
      timeout: 8000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const articles = (res.data && res.data.data && res.data.data.records) || [];
    logger.debug('Recent articles fetched', { count: articles.length, limit });
    return articles;
  } catch (error) {
    logger.warn('Failed to fetch recent articles, using empty array', { 
      limit, 
      error: error.message 
    });
    return [];
  }
}

async function fetchCollects() {
  try {
    logger.debug('Fetching collects');
    const res = await axios.get(`${JAVA_BACKEND_URL}/webInfo/listCollect`, { 
      timeout: 5000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const collects = (res.data && res.data.data) || {};
    logger.debug('Collects fetched', { categories: Object.keys(collects).length });
    return collects;
  } catch (error) {
    logger.warn('Failed to fetch collects, using empty object', { error: error.message });
    return {};
  }
}

async function fetchFriends() {
  try {
    logger.debug('Fetching friends');
    const res = await axios.get(`${JAVA_BACKEND_URL}/webInfo/listFriend`, { 
      timeout: 5000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const friends = (res.data && res.data.data) || {};
    logger.debug('Friends fetched', { categories: Object.keys(friends).length });
    return friends;
  } catch (error) {
    logger.warn('Failed to fetch friends, using empty object', { error: error.message });
    return {};
  }
}

async function fetchSortById(sortId) {
  try {
    logger.debug('Fetching sort by ID', { sortId });
    // 修改为使用现有的API: /webInfo/getSortInfo 或 /webInfo/listSortForPrerender
    const res = await axios.get(`${JAVA_BACKEND_URL}/webInfo/listSortForPrerender`, { 
      timeout: 5000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    
    // 从返回的分类列表中找到指定ID的分类
    const sortList = (res.data && res.data.data) || [];
    const sort = Array.isArray(sortList) ? sortList.find(s => s.id === parseInt(sortId)) : null;
    
    logger.debug('Sort fetched by ID', { sortId, found: !!sort, totalSorts: sortList.length });
    return sort;
  } catch (error) {
    logger.error('Failed to fetch sort by ID', { sortId, error: error.message });
    return null;
  }
}

async function fetchArticlesBySort(sortId, labelId = null, limit = 10) {
  try {
    logger.debug('Fetching articles by sort', { sortId, labelId, limit });
    const params = { current: 1, size: limit, sortId };
    if (labelId) params.labelId = labelId;
    
    const res = await axios.post(`${JAVA_BACKEND_URL}/article/listArticle`, params, { 
      timeout: 8000,
      headers: INTERNAL_SERVICE_HEADERS
    });
    const articles = (res.data && res.data.data && res.data.data.records) || [];
    logger.debug('Articles fetched by sort', { 
      sortId, 
      labelId, 
      limit, 
      count: articles.length 
    });
    return articles;
  } catch (error) {
    logger.warn('Failed to fetch articles by sort, using empty array', { 
      sortId, 
      labelId, 
      limit, 
      error: error.message 
    });
    return [];
  }
}

// ===== 通用HTML构建函数 =====
function buildHtmlTemplate({ title, meta, content, lang, pageType = 'article' }) {
  const templatePath = path.resolve('/app/dist/index.html');
  let templateHtml;
  
  if (!fs.existsSync(templatePath)) {
    // 如果挂载路径不存在，尝试相对路径作为fallback
    const fallbackPath = path.resolve(__dirname, './dist/index.html');
    if (!fs.existsSync(fallbackPath)) {
      throw new Error(`SPA template not found at ${templatePath} or ${fallbackPath}. Please ensure poetize-ui has been built and volumes are properly mounted.`);
    }
    console.warn(`Using fallback template path: ${fallbackPath}`);
    templateHtml = fs.readFileSync(fallbackPath, 'utf8');
  } else {
    templateHtml = fs.readFileSync(templatePath, 'utf8');
  }
  
  const $ = cheerio.load(templateHtml);

  $('html').attr('lang', lang);
  $('head title').text(title);

  // 清理占位符/旧meta，更彻底
  $('head meta[name="description"]').remove();
  $('head meta[name="keywords"]').remove();
  $('head meta[name="author"]').remove();
  $('head meta[property^="og:"]').remove();
  $('head meta[property^="twitter:"]').remove();
  $('head meta[property^="article:"]').remove();
  $('head link[rel="canonical"]').remove();

  // 调试：检查meta对象
  console.log('buildHtmlTemplate meta debug:', {
    metaType: typeof meta,
    metaIsObject: typeof meta === 'object' && meta !== null,
    metaKeys: meta ? Object.keys(meta) : 'null',
    metaStringified: JSON.stringify(meta)
  });

  // 注入新的meta，一次一个，更安全
  if (typeof meta === 'object' && meta !== null) {
    for (const key in meta) {
      if (!meta.hasOwnProperty(key)) continue;

      const value = (meta[key] || '').toString().replace(/"/g, '&quot;');
      
      if (key === 'title') {
        // title已在上面处理
        continue;
      } else if (key.startsWith('hreflang')) {
        // hreflang 已经是完整的 <link> 标签
        $('head').append(meta[key]);
      } else if (key === 'canonical') {
        $('head').append(`<link rel="canonical" href="${value}">`);
      } else if (['description', 'keywords', 'author'].includes(key)) {
        $('head').append(`<meta name="${key}" content="${value}">`);
      } else {
        // 处理 og:, twitter:, article: 等属性
        $('head').append(`<meta property="${key}" content="${value}">`);
      }
    }
  } else {
    console.error('Meta is not a valid object:', meta);
  }

  // 添加页面类型标识
  $('body').attr('data-prerender-type', pageType);
  $('body').attr('data-prerender-lang', lang);

  // 添加防止FOUC的关键内联样式
  const criticalCSS = `
    <style>
      /* 防止FOUC的关键样式 */
      html.prerender #app {
        visibility: visible;
        opacity: 1;
      }
      
      html:not(.loaded) #app {
        visibility: hidden;
      }
      
      html.loaded #app {
        visibility: visible;
        opacity: 1;
        transition: opacity 0.3s ease-in-out;
      }
      
      /* 预渲染内容的样式保护 */
      .article-detail, .home-prerender, .favorite-prerender, .sort-prerender, .sort-list-prerender {
        min-height: 200px;
        position: relative;
        opacity: 1;
        transform: translateY(0);
        animation: fadeIn 0.5s ease-in-out;
      }
      
      @keyframes fadeIn {
        from { 
          opacity: 0; 
          transform: translateY(10px); 
        }
        to { 
          opacity: 1; 
          transform: translateY(0); 
        }
      }
      
      /* 骨架屏效果 */
      .article-detail::before, 
      .home-prerender::before, 
      .favorite-prerender::before, 
      .sort-prerender::before,
      .sort-list-prerender::before {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: linear-gradient(90deg, 
          rgba(240, 240, 240, 0.1) 25%, 
          transparent 37%, 
          rgba(240, 240, 240, 0.1) 63%
        );
        animation: shimmer 1.5s ease-in-out infinite;
        z-index: 1;
        opacity: 0;
        transition: opacity 0.3s ease;
        pointer-events: none;
      }
      
      html:not(.loaded) .article-detail::before,
      html:not(.loaded) .home-prerender::before,
      html:not(.loaded) .favorite-prerender::before,
      html:not(.loaded) .sort-prerender::before,
      html:not(.loaded) .sort-list-prerender::before {
        opacity: 1;
      }
      
      @keyframes shimmer {
        0% { transform: translateX(-100%); }
        100% { transform: translateX(100%); }
      }
      
      /* 字体渲染优化 */
      body {
        font-display: swap;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
        text-rendering: optimizeLegibility;
      }
      
             /* 图片加载优化 */
       img {
         opacity: 1;
         transition: opacity 0.3s ease;
       }
       
       /* 只有在页面未完全加载时才隐藏图片 */
       html:not(.loaded) img:not(.loaded):not([src*="data:"]) {
         opacity: 0.3;
       }
       
       img.loaded,
       img[src*="data:"],
       img[complete] {
         opacity: 1;
       }
      
      /* 链接和按钮的基础样式保护 */
      a, button {
        transition: all 0.2s ease;
      }
      
      /* 预渲染内容的响应式保护 */
      @media (max-width: 768px) {
        .article-detail, .home-prerender, .favorite-prerender, .sort-prerender, .sort-list-prerender {
          min-height: 150px;
          padding: 1rem;
        }
      }
      
      /* 确保代码高亮区域的基础样式 */
      pre, code {
        background-color: #f6f8fa;
        border-radius: 3px;
        font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
      }
      
      pre {
        padding: 16px;
        overflow: auto;
        line-height: 1.45;
      }
      
      code {
        padding: 0.2em 0.4em;
        margin: 0;
        font-size: 85%;
      }
      
      /* 表格的基础样式保护 */
      table {
        border-collapse: collapse;
        width: 100%;
        margin: 1em 0;
      }
      
      th, td {
        border: 1px solid #ddd;
        padding: 8px;
        text-align: left;
      }
      
      th {
        background-color: #f2f2f2;
        font-weight: bold;
      }
    </style>
  `;
  
  $('head').append(criticalCSS);

  // 添加资源预加载优化
  $('head').prepend(`
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link rel="dns-prefetch" href="//cdn.jsdelivr.net">
  `);

  // 注入渲染好的内容
  $('#app').html(content);

  // 添加加载状态管理脚本
  const loadingScript = `
    <script>
      // 防止FOUC的立即执行脚本
      (function() {
        // 标记页面为预渲染状态
        document.documentElement.classList.add('prerender');
        
          // 图片加载状态处理
         function handleImageLoad() {
           const images = document.querySelectorAll('img');
           images.forEach(function(img) {
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
                 this.classList.add('loaded'); // 即使加载失败也要显示，避免一直隐藏
                 console.warn('图片加载失败，但仍显示:', this.src);
               }, { once: true });
               
               // 设置一个超时，确保图片不会永远隐藏
               setTimeout(function() {
                 if (!img.classList.contains('loaded')) {
                   img.classList.add('loaded');
                   console.warn('图片加载超时，强制显示:', img.src);
                 }
               }, 5000); // 5秒超时
             } else {
               // 没有src的图片直接标记为已加载
               img.classList.add('loaded');
             }
           });
         }
        
        // 监听资源加载完成
        function markAsLoaded() {
          requestAnimationFrame(function() {
            document.documentElement.classList.add('loaded');
            document.documentElement.classList.remove('prerender');
            handleImageLoad();
          });
        }
        
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', markAsLoaded);
        } else {
          markAsLoaded();
        }
        
        // Vue应用挂载完成后的回调
        window.addEventListener('app-mounted', function() {
          const app = document.getElementById('app');
          if (app) {
            app.classList.add('loaded');
          }
          handleImageLoad();
        });
        
        // 字体加载完成后的优化
        if (document.fonts) {
          document.fonts.ready.then(function() {
            document.documentElement.classList.add('fonts-loaded');
          });
        }
      })();
    </script>
  `;
  
  $('body').append(loadingScript);

  return $.html();
}

// ===== 文章页面渲染函数 =====
function buildHtml({ title, meta, content, lang }) {
  // 调试：确保参数格式正确
  console.log('buildHtml parameters:', {
    title: typeof title,
    meta: typeof meta,
    content: typeof content,
    lang: typeof lang,
    metaKeys: meta ? Object.keys(meta) : 'null'
  });

  const articleContent = `<main class="article-detail">${content}</main>`;
  
  // 确保meta是一个有效的对象
  const safeMeta = (typeof meta === 'object' && meta !== null) ? meta : {};
  
  return buildHtmlTemplate({ 
    title: title || 'Poetize', 
    meta: safeMeta, 
    content: articleContent, 
    lang: lang || 'zh', 
    pageType: 'article' 
  });
}

// ===== 首页渲染函数 =====
async function renderHomePage(lang = 'zh') {
  try {
    const [webInfo, seoConfig, sortInfo, recentArticles] = await Promise.all([
      fetchWebInfo(),
      fetchSeoConfig(),
      fetchSortInfo(), 
      fetchRecentArticles(8)
    ]);

    // 优先使用SEO配置，其次使用webInfo，最后使用默认值
    const title = seoConfig.site_title || webInfo.webTitle || webInfo.webName || 'Poetize';
    const description = seoConfig.site_description || `${webInfo.webName} - 个人博客网站，分享技术文章、生活感悟。`;
    const keywords = seoConfig.site_keywords || '博客,个人网站,技术分享';
    const author = seoConfig.default_author || webInfo.webName || 'Admin';
    const ogImage = seoConfig.og_image || webInfo.avatar || '';
    
    const meta = {
      description,
      keywords,
      author,
      'og:title': title,
      'og:description': description,
      'og:type': 'website',
      'og:url': seoConfig.site_address || process.env.SITE_URL || 'https://poetize.cn',
      'og:image': ogImage,
      'twitter:card': seoConfig.twitter_card || 'summary_large_image',
      'twitter:title': title,
      'twitter:description': description,
      'twitter:image': ogImage
    };

    // 构建首页内容（只包含静态SEO内容，动态内容由客户端加载）
    const homeContent = `
      <div class="home-prerender">
        <div class="home-hero">
          <h1>${webInfo.webName || 'Poetize'}</h1>
          <p>${description}</p>
        </div>
        <div class="home-categories">
          <h2>文章分类</h2>
          <ul>
            ${sortInfo.map(sort => `
              <li>
                <a href="/sort?sortId=${sort.id}" title="${sort.sortDescription || sort.sortName}">
                  ${sort.sortName}
                </a>
              </li>
            `).join('')}
          </ul>
        </div>
        <div class="home-recent-articles">
          <h2>最新文章</h2>
          <ul>
            ${recentArticles.map(article => `
              <li>
                <a href="/article/${article.id}" title="${article.articleTitle}">
                  <h3>${article.articleTitle}</h3>
                  <p>${article.summary || ''}</p>
                  <time>${article.createTime}</time>
                </a>
              </li>
            `).join('')}
          </ul>
        </div>
        <!-- 动态内容占位符，由客户端JavaScript填充 -->
        <div id="dynamic-content-placeholder" style="display:none;">
          <script>
            // 标记这是预渲染页面，客户端需要动态加载内容
            window.PRERENDER_DATA = {
              type: 'home',
              lang: '${lang}',
              timestamp: ${Date.now()}
            };
          </script>
        </div>
      </div>
    `;

    return buildHtmlTemplate({ 
      title, 
      meta, 
      content: homeContent, 
      lang, 
      pageType: 'home' 
    });
  } catch (error) {
    console.error('Failed to render home page:', error);
    throw error;
  }
}

// ===== 百宝箱页面渲染函数 =====
async function renderFavoritePage(lang = 'zh') {
  try {
    const [webInfo, seoConfig, collects, friends] = await Promise.all([
      fetchWebInfo(),
      fetchSeoConfig(),
      fetchCollects(),
      fetchFriends()
    ]);

    const siteName = seoConfig.site_title || webInfo.webName || 'Poetize';
    const title = `百宝箱 - ${siteName}`;
    const description = '收藏夹、友人帐、音乐欣赏 - 发现更多精彩内容';
    const author = seoConfig.default_author || webInfo.webName || 'Admin';
    const ogImage = seoConfig.og_image || webInfo.avatar || '';
    const baseUrl = seoConfig.site_address || process.env.SITE_URL || 'https://poetize.cn';
    
    // 在基础关键词基础上添加页面特定关键词
    const baseKeywords = seoConfig.site_keywords || '博客,个人网站,技术分享';
    const keywords = `${baseKeywords},百宝箱,收藏夹,友人帐,友链,音乐`;
    
    const meta = {
      description,
      keywords,
      author,
      'og:title': title,
      'og:description': description,
      'og:type': 'website',
      'og:url': `${baseUrl}/favorite`,
      'og:image': ogImage,
      'twitter:card': seoConfig.twitter_card || 'summary',
      'twitter:title': title,
      'twitter:description': description,
      'twitter:image': ogImage
    };

    // 构建百宝箱内容
    const favoriteContent = `
      <div class="favorite-prerender">
        <div class="favorite-hero">
          <h1>百宝箱</h1>
          <p>收藏夹、友人帐、音乐欣赏</p>
        </div>
        
        <div class="favorite-sections">
          <section class="collect-section">
            <h2>收藏夹</h2>
            <p>精选网站收藏</p>
            ${Object.keys(collects).length > 0 ? Object.keys(collects).map(category => `
              <div class="collect-category">
                <h3>${category}</h3>
                <ul>
                  ${collects[category].map(item => `
                    <li>
                      <a href="${item.url}" target="_blank" rel="noopener" title="${item.introduction}">
                        <img src="${item.cover}" alt="${item.title}" width="32" height="32" loading="lazy">
                        <span>${item.title}</span>
                        <small>${item.introduction}</small>
                      </a>
                    </li>
                  `).join('')}
                </ul>
              </div>
            `).join('') : '<p>暂无收藏夹</p>'}
          </section>
          
          <section class="friend-section">
            <h2>友人帐</h2>
            <p>留下你的网站，与更多朋友交流</p>
            
            <!-- 本站信息 -->
            <div class="site-info">
              <h3>本站信息</h3>
              <div class="site-card">
                <img src="${webInfo.avatar || ''}" alt="${webInfo.webName || 'Poetize'}" width="64" height="64" loading="lazy">
                <div class="site-details">
                  <h4>${webInfo.webName || 'Poetize'}</h4>
                  <p>${webInfo.webTitle || description}</p>
                  <a href="${baseUrl}" target="_blank" rel="noopener">${baseUrl}</a>
                </div>
              </div>
            </div>
            
            <!-- 友链列表 -->
            ${Object.keys(friends).length > 0 ? `
              <div class="friends-list">
                <h3>友情链接</h3>
                ${Object.keys(friends).map(category => `
                  <div class="friend-category">
                    <h4>${category}</h4>
                    <ul>
                      ${friends[category].map(friend => `
                        <li>
                          <a href="${friend.url}" target="_blank" rel="noopener" title="${friend.introduction}">
                            <img src="${friend.cover}" alt="${friend.title}" width="32" height="32" loading="lazy">
                            <span>${friend.title}</span>
                            <small>${friend.introduction}</small>
                          </a>
                        </li>
                      `).join('')}
                    </ul>
                  </div>
                `).join('')}
              </div>
            ` : '<p>暂无友链，欢迎交换友链</p>'}
          </section>
          
          <section class="music-section">
            <h2>曲乐</h2>
            <p>一曲肝肠断，天涯何处觅知音</p>
          </section>
        </div>
        
        <!-- 动态内容占位符 -->
        <div id="dynamic-content-placeholder" style="display:none;">
          <script>
            window.PRERENDER_DATA = {
              type: 'favorite',
              lang: '${lang}',
              timestamp: ${Date.now()}
            };
          </script>
        </div>
      </div>
    `;

    return buildHtmlTemplate({ 
      title, 
      meta, 
      content: favoriteContent, 
      lang, 
      pageType: 'favorite' 
    });
  } catch (error) {
    console.error('Failed to render favorite page:', error);
    throw error;
  }
}

// ===== 默认分类页面渲染函数（显示所有分类列表）=====
async function renderDefaultSortPage(lang = 'zh') {
  try {
    const [webInfo, seoConfig, sortList] = await Promise.all([
      fetchWebInfo(),
      fetchSeoConfig(),
      fetchSortInfo() // 获取所有分类信息
    ]);

    const siteName = seoConfig.site_title || webInfo.webName || 'Poetize';
    const title = `文章分类 - ${siteName}`;
    const description = '浏览所有文章分类，找到您感兴趣的内容主题';
    const author = seoConfig.default_author || webInfo.webName || 'Admin';
    const ogImage = seoConfig.og_image || webInfo.avatar || '';
    const baseUrl = seoConfig.site_address || process.env.SITE_URL || 'https://poetize.cn';
    
    // 在基础关键词基础上添加页面特定关键词
    const baseKeywords = seoConfig.site_keywords || '博客,个人网站,技术分享';
    const keywords = `${baseKeywords},文章分类,分类列表,内容导航`;
    
    const meta = {
      description,
      keywords,
      author,
      'og:title': title,
      'og:description': description,
      'og:type': 'website',
      'og:url': `${baseUrl}/sort`,
      'og:image': ogImage,
      'twitter:card': seoConfig.twitter_card || 'summary',
      'twitter:title': title,
      'twitter:description': description,
      'twitter:image': ogImage
    };

    // 构建默认分类页面内容
    const defaultSortContent = `
      <div class="sort-list-prerender">
        <div class="sort-hero">
          <h1>文章分类</h1>
          <p>探索不同主题的文章内容</p>
        </div>
        
        <div class="sort-categories">
          ${Array.isArray(sortList) && sortList.length > 0 ? `
            <div class="categories-grid">
              ${sortList.map(sort => `
                <div class="category-card">
                  <a href="/sort?sortId=${sort.id}" title="${sort.sortDescription || sort.sortName}">
                    <h3>${sort.sortName}</h3>
                    <p>${sort.sortDescription || '暂无描述'}</p>
                    <div class="category-stats">
                      <span class="article-count">${sort.countOfSort || 0} 篇文章</span>
                      ${sort.labels && sort.labels.length > 0 ? `<span class="label-count">${sort.labels.length} 个标签</span>` : ''}
                    </div>
                    ${sort.labels && sort.labels.length > 0 ? `
                      <div class="category-labels">
                        ${sort.labels.slice(0, 3).map(label => `
                          <span class="label-tag">${label.labelName}</span>
                        `).join('')}
                        ${sort.labels.length > 3 ? '<span class="label-more">...</span>' : ''}
                      </div>
                    ` : ''}
                  </a>
                </div>
              `).join('')}
            </div>
          ` : '<p class="no-categories">暂无分类</p>'}
        </div>
        
        <!-- 动态内容占位符 -->
        <div id="dynamic-content-placeholder" style="display:none;">
          <script>
            window.PRERENDER_DATA = {
              type: 'sort-list',
              lang: '${lang}',
              timestamp: ${Date.now()}
            };
          </script>
        </div>
      </div>
    `;

    return buildHtmlTemplate({ 
      title, 
      meta, 
      content: defaultSortContent, 
      lang, 
      pageType: 'sort-list' 
    });
  } catch (error) {
    console.error('Failed to render default sort page:', error);
    throw error;
  }
}

// ===== 分类页面渲染函数 =====
async function renderSortPage(sortId, labelId = null, lang = 'zh') {
  try {
    const [webInfo, seoConfig, sortData, articles] = await Promise.all([
      fetchWebInfo(),
      fetchSeoConfig(),
      fetchSortById(sortId),
      fetchArticlesBySort(sortId, labelId, 20)
    ]);

    if (!sortData) {
      throw new Error(`Sort ${sortId} not found`);
    }

    const siteName = seoConfig.site_title || webInfo.webName || 'Poetize';
    const title = `${sortData.sortName} - ${siteName}`;
    const description = sortData.sortDescription || `${sortData.sortName}分类下的所有文章`;
    const author = seoConfig.default_author || webInfo.webName || 'Admin';
    const ogImage = seoConfig.og_image || webInfo.avatar || '';
    const baseUrl = seoConfig.site_address || process.env.SITE_URL || 'https://poetize.cn';
    
    // 在基础关键词基础上添加分类特定关键词
    const baseKeywords = seoConfig.site_keywords || '博客,个人网站,技术分享';
    const keywords = `${baseKeywords},${sortData.sortName},文章分类,博客`;
    
    const meta = {
      description,
      keywords,
      author,
      'og:title': title,
      'og:description': description,
      'og:type': 'website',
      'og:url': `${baseUrl}/sort?sortId=${sortId}${labelId ? `&labelId=${labelId}` : ''}`,
      'og:image': ogImage,
      'twitter:card': seoConfig.twitter_card || 'summary',
      'twitter:title': title,
      'twitter:description': description,
      'twitter:image': ogImage
    };

    // 构建分类页面内容
    const sortContent = `
      <div class="sort-prerender">
        <div class="sort-hero">
          <h1>${sortData.sortName}</h1>
          <p>${sortData.sortDescription || ''}</p>
        </div>
        
        <div class="sort-articles">
          <h2>文章列表</h2>
          ${articles.length > 0 ? `
            <ul class="article-list">
              ${articles.map(article => `
                <li class="article-item">
                  <a href="/article/${article.id}" title="${article.articleTitle}">
                    ${article.articleCover ? `<img src="${article.articleCover}" alt="${article.articleTitle}" loading="lazy">` : ''}
                    <div class="article-info">
                      <h3>${article.articleTitle}</h3>
                      <p>${article.summary || ''}</p>
                      <div class="article-meta">
                        <time>${article.createTime}</time>
                        <span class="view-count">阅读 ${article.viewCount || 0}</span>
                        ${article.label ? `<span class="label">${article.label.labelName}</span>` : ''}
                      </div>
                    </div>
                  </a>
                </li>
              `).join('')}
            </ul>
          ` : '<p>暂无文章</p>'}
        </div>
        
        <!-- 标签筛选 -->
        ${sortData.labels && sortData.labels.length > 0 ? `
          <div class="sort-labels">
            <h3>标签筛选</h3>
            <ul>
              ${sortData.labels.map(label => `
                <li>
                  <a href="/sort?sortId=${sortId}&labelId=${label.id}" title="${label.labelDescription || label.labelName}">
                    ${label.labelName} (${label.countOfLabel || 0})
                  </a>
                </li>
              `).join('')}
            </ul>
          </div>
        ` : ''}
        
        <!-- 动态内容占位符 -->
        <div id="dynamic-content-placeholder" style="display:none;">
          <script>
            window.PRERENDER_DATA = {
              type: 'sort',
              sortId: ${sortId},
              labelId: ${labelId || 'null'},
              lang: '${lang}',
              timestamp: ${Date.now()}
            };
          </script>
        </div>
      </div>
    `;

    return buildHtmlTemplate({ 
      title, 
      meta, 
      content: sortContent, 
      lang, 
      pageType: 'sort' 
    });
  } catch (error) {
    console.error(`Failed to render sort page ${sortId}:`, error);
    throw error;
  }
}

// ===== 文章渲染函数 =====
async function renderIds(ids = [], options = {}) {
  if (!Array.isArray(ids) || ids.length === 0) {
    throw new Error('ids must be a non-empty array');
  }

  const taskId = generateTaskId();
  monitor.recordRenderStart(taskId, 'article', { ids, options });

  const OUTPUT_ROOT = options.outputRoot || process.env.PRERENDER_OUTPUT || path.resolve(__dirname, './dist/prerender');
  const langs = ['zh', 'en'];

  try {
    logger.info('Starting article rendering', { taskId, articleCount: ids.length, langs });

    const assets = await getFrontEndAssets(options.frontendHost || 'nginx');
    logger.debug('Frontend assets loaded', { taskId, assets });

    // 获取SEO配置和网站信息，所有文章共用
    const [seoConfig, webInfo] = await Promise.all([
      fetchSeoConfig(),
      fetchWebInfo()
    ]);

    // 调试：检查CSS文件是否存在
    const distPath = '/app/dist';
    const staticCssPath = path.join(distPath, 'static', 'css');
    logger.info('Checking CSS files availability', {
      taskId,
      distPathExists: fs.existsSync(distPath),
      staticCssPathExists: fs.existsSync(staticCssPath),
      distContents: fs.existsSync(distPath) ? fs.readdirSync(distPath) : [],
      staticCssContents: fs.existsSync(staticCssPath) ? fs.readdirSync(staticCssPath).filter(f => f.endsWith('.css')) : []
    });

    let successCount = 0;
    let failCount = 0;
    const errors = [];

    for (const id of ids) {
      for (const lang of langs) {
        try {
          logger.debug('Rendering article', { taskId, articleId: id, lang });

          const article = await fetchArticle(id);
          if (!article) { 
            logger.warn('Article not found, skipping', { taskId, articleId: id });
            continue; 
          }

          let contentHtml = article.articleContent || '';
          let articleTitle = article.articleTitle || '';

          // translation
          const t = await fetchTranslation(id, lang);
          if (t) {
            if (t.content) contentHtml = t.content;
            if (t.title) articleTitle = t.title;
            logger.debug('Translation applied', { taskId, articleId: id, lang });
          }

          // 对内容进行 HTML 实体解码，避免 &gt; 等导致 markdown 失效
          contentHtml = decodeHtmlEntities(contentHtml);

          // markdown -> html
          contentHtml = md.render(contentHtml);
          logger.debug('Markdown content rendered to HTML', { taskId, articleId: id, lang });

          // 获取文章特定的meta信息
          const articleMeta = await fetchMeta(id, lang);
          
          // 增强meta信息，结合SEO配置
          const siteName = seoConfig.site_title || webInfo.webName || 'Poetize';
          const baseKeywords = seoConfig.site_keywords || '博客,个人网站,技术分享';
          const author = seoConfig.default_author || webInfo.webName || 'Admin';
          const baseUrl = seoConfig.site_address || process.env.SITE_URL || 'https://poetize.cn';
          
          // 合并meta信息：文章特定meta + SEO配置
          const meta = {
            ...articleMeta,
            // 确保基础字段存在
            author: articleMeta.author || author,
            keywords: articleMeta.keywords ? `${baseKeywords},${articleMeta.keywords}` : baseKeywords,
            'og:site_name': siteName,
            'og:url': articleMeta['og:url'] || `${baseUrl}/article/${id}`,
            'og:image': articleMeta['og:image'] || seoConfig.og_image || webInfo.avatar || '',
            'twitter:card': seoConfig.twitter_card || 'summary_large_image',
            'twitter:site': seoConfig.twitter_site || ''
          };

          // 调试：检查meta对象的格式
          logger.info('Meta object before buildHtml', { 
            taskId, 
            articleId: id, 
            lang, 
            metaType: typeof meta,
            metaKeys: Object.keys(meta),
            metaSample: Object.keys(meta).slice(0, 3).reduce((obj, key) => {
              obj[key] = meta[key];
              return obj;
            }, {})
          });

          let html = buildHtml({ title: meta.title || articleTitle || 'Poetize', meta, content: contentHtml, lang });
          
          const dir = path.join(OUTPUT_ROOT, 'article', id.toString());
          fs.mkdirSync(dir, { recursive: true });
          const filename = lang === 'zh' ? 'index.html' : `index-${lang}.html`;
          const filePath = path.join(dir, filename);
          fs.writeFileSync(filePath, html, 'utf8');
          
          successCount++;
          logger.debug('Article rendered successfully', { 
            taskId, 
            articleId: id, 
            lang, 
            filePath: `${dir}/${filename}`,
            size: `${(html.length / 1024).toFixed(1)}KB`
          });

        } catch (err) {
          failCount++;
          errors.push({ articleId: id, lang, error: err.message });
          logger.error('Failed to render article', { 
            taskId, 
            articleId: id, 
            lang, 
            error: err.message,
            stack: err.stack 
          });
        }
      }
    }

    if (errors.length > 0 && successCount === 0) {
      throw new Error(`All renders failed. Errors: ${JSON.stringify(errors)}`);
    }

    monitor.recordRenderSuccess(taskId, { 
      count: ids.length, 
      successCount, 
      failCount, 
      langs: langs.length 
    });

    logger.info('Article rendering completed', { 
      taskId, 
      totalArticles: ids.length,
      successCount, 
      failCount,
      errorCount: errors.length
    });

  } catch (error) {
    monitor.recordRenderFailure(taskId, error);
    throw error;
  }
}

// ===== 新增：页面类型渲染函数 =====

// 辅助函数：渲染单个分类页面（用于批量渲染，不创建新任务）
async function renderSingleSortPage(sortId, parentTaskId = null) {
  const OUTPUT_ROOT = process.env.PRERENDER_OUTPUT || path.resolve(__dirname, './dist/prerender');
  // 分类页面只生成中文版
  const langs = ['zh'];
  
  for (const lang of langs) {
    const html = await renderSortPage(sortId, null, lang);
    
    const outputPath = path.join(OUTPUT_ROOT, 'sort', sortId.toString());
    fs.mkdirSync(outputPath, { recursive: true });
    
    const filename = lang === 'zh' ? 'index.html' : `index-${lang}.html`;
    const filePath = path.join(outputPath, filename);
    fs.writeFileSync(filePath, html, 'utf8');
    
    logger.debug('Sort page rendered', { 
      parentTaskId, 
      sortId, 
      lang, 
      filePath: `${outputPath}/${filename}`,
      size: `${(html.length / 1024).toFixed(1)}KB`
    });
  }
}

async function renderPages(type, params = {}) {
  const taskId = generateTaskId();
  monitor.recordRenderStart(taskId, type, { type, params });

  const OUTPUT_ROOT = process.env.PRERENDER_OUTPUT || path.resolve(__dirname, './dist/prerender');
  // 只有文章页面需要多语言，其他页面只生成中文版
  const langs = ['zh'];
  
  try {
    logger.info('Starting page rendering', { taskId, type, params, langs });

    let successCount = 0;
    let failCount = 0;
    const results = [];

    for (const lang of langs) {
      try {
        logger.debug('Rendering page', { taskId, type, lang, params });

        let html;
        let outputPath;

        switch (type) {
          case 'home':
            html = await renderHomePage(lang);
            outputPath = path.join(OUTPUT_ROOT, 'home');
            break;
            
          case 'favorite':
            html = await renderFavoritePage(lang);
            outputPath = path.join(OUTPUT_ROOT, 'favorite');
            break;
            
          case 'sort':
            const { sortId, labelId } = params;
            if (!sortId) {
              // 渲染默认分类页面（显示所有分类列表）
              html = await renderDefaultSortPage(lang);
              outputPath = path.join(OUTPUT_ROOT, 'sort');
            } else {
              // 渲染特定分类页面
              html = await renderSortPage(sortId, labelId, lang);
              outputPath = path.join(OUTPUT_ROOT, 'sort', sortId.toString());
              if (labelId) outputPath = path.join(outputPath, labelId.toString());
            }
            break;
            
          default:
            throw new Error(`Unknown page type: ${type}`);
        }

        fs.mkdirSync(outputPath, { recursive: true });
        const filename = lang === 'zh' ? 'index.html' : `index-${lang}.html`;
        const filePath = path.join(outputPath, filename);
        fs.writeFileSync(filePath, html, 'utf8');
        
        successCount++;
        results.push({ lang, path: `${outputPath}/${filename}`, size: `${(html.length / 1024).toFixed(1)}KB` });
        
        logger.debug('Page rendered successfully', { 
          taskId, 
          type, 
          lang, 
          filePath: `${outputPath}/${filename}`,
          size: `${(html.length / 1024).toFixed(1)}KB`
        });

      } catch (err) {
        failCount++;
        logger.error('Failed to render page', { 
          taskId, 
          type, 
          lang, 
          params,
          error: err.message,
          stack: err.stack 
        });
      }
    }

    if (failCount > 0 && successCount === 0) {
      throw new Error(`All page renders failed for type: ${type}`);
    }

    monitor.recordRenderSuccess(taskId, { 
      type, 
      params, 
      successCount, 
      failCount,
      results,
      count: successCount // 页面渲染应该记录实际成功的页面数量
    });

    logger.info('Page rendering completed', { 
      taskId, 
      type, 
      params,
      successCount, 
      failCount,
      results
    });

  } catch (error) {
    monitor.recordRenderFailure(taskId, error);
    throw error;
  }
}

// ===== API路由 =====

// 中间件：记录请求
app.use((req, res, next) => {
  const requestId = generateTaskId();
  req.requestId = requestId;
  
  // 只有渲染相关的请求才计入业务统计，监控API不计入
  const isMonitoringRequest = req.path.startsWith('/status') || 
                              req.path.startsWith('/health') || 
                              req.path.startsWith('/logs') || 
                              req.path.startsWith('/monitor') || 
                              req.path.startsWith('/errors') ||
                              req.path.startsWith('/cleanup');
  
  if (!isMonitoringRequest) {
    monitor.recordRequest(req.method + ' ' + req.path);
  }
  
  // 只记录非监控API的请求，避免自动刷新产生大量日志
  if (!isMonitoringRequest) {
    logger.info('API request received', {
      requestId,
      method: req.method,
      path: req.path,
      ip: req.ip,
      userAgent: req.get('User-Agent')
    });
  } else {
    // 监控API请求只记录为DEBUG级别（通常不会输出）
    logger.debug('Monitoring API request', {
      requestId,
      method: req.method,
      path: req.path
    });
  }
  
  next();
});

// 原有文章渲染API - 增强版
app.post('/render', async (req, res) => {
  const requestId = req.requestId;
  const ids = req.body.ids;
  
  logger.info('Render request received', { requestId, ids });
  
  if (!Array.isArray(ids) || ids.length === 0) {
    logger.warn('Invalid render request - ids array required', { requestId, body: req.body });
    return res.status(400).json({ 
      message: 'ids array required',
      requestId,
      timestamp: new Date().toISOString()
    });
  }

  if (ids.length > 50) {
    logger.warn('Too many articles in single request', { requestId, count: ids.length });
    return res.status(400).json({ 
      message: 'Too many articles. Maximum 50 per request.',
      requestId,
      received: ids.length,
      maximum: 50
    });
  }

  try {
    const startTime = Date.now();
    await renderIds(ids);
    const duration = Date.now() - startTime;
    
    logger.info('Render request completed successfully', { 
      requestId, 
      articleCount: ids.length, 
      duration: `${duration}ms` 
    });
    
    res.json({ 
      status: 'ok', 
      rendered: ids.length,
      requestId,
      duration: `${duration}ms`,
      timestamp: new Date().toISOString()
    });
  } catch (e) {
    logger.error('Render request failed', { 
      requestId, 
      error: e.message, 
      stack: e.stack,
      ids 
    });
    res.status(500).json({ 
      message: e.message,
      requestId,
      timestamp: new Date().toISOString()
    });
  }
});

// 新增：页面渲染API - 增强版
app.post('/render/pages', async (req, res) => {
  const requestId = req.requestId;
  const { type, params = {} } = req.body;
  
  logger.info('Page render request received', { requestId, type, params });
  
  if (!type) {
    logger.warn('Invalid page render request - type required', { requestId, body: req.body });
    return res.status(400).json({ 
      message: 'type is required',
      requestId,
      supportedTypes: ['home', 'favorite', 'sort'],
      timestamp: new Date().toISOString()
    });
  }

  if (!['home', 'favorite', 'sort', 'allSorts'].includes(type)) {
    logger.warn('Invalid page type', { requestId, type });
    return res.status(400).json({ 
      message: 'Invalid page type',
      requestId,
      received: type,
      supported: ['home', 'favorite', 'sort', 'allSorts'],
      timestamp: new Date().toISOString()
    });
  }

  try {
    const startTime = Date.now();
    
    if (type === 'allSorts') {
      // 处理批量分类页面渲染 - 创建统一的批量任务
      const { sortIds } = params;
      if (!Array.isArray(sortIds) || sortIds.length === 0) {
        return res.status(400).json({ 
          message: 'sortIds array required for allSorts type',
          requestId 
        });
      }
      
      logger.info('Rendering all sort pages', { requestId, sortIds });
      
      // 创建统一的批量任务而不是多个独立任务
      const batchTaskId = generateTaskId();
      monitor.recordRenderStart(batchTaskId, 'allSorts', { sortIds, count: sortIds.length });
      
      try {
        let successCount = 0;
        let failCount = 0;
        const results = [];
        
        for (const sortId of sortIds) {
           try {
             // 直接执行分类页面渲染逻辑，不创建新任务
             await renderSingleSortPage(sortId, batchTaskId);
             successCount++;
             results.push({ sortId, status: 'success' });
             logger.info('Sort page rendered successfully', { batchTaskId, sortId });
           } catch (error) {
             failCount++;
             results.push({ sortId, status: 'failed', error: error.message });
             logger.error('Sort page render failed', { batchTaskId, sortId, error: error.message });
           }
         }
        
        monitor.recordRenderSuccess(batchTaskId, {
          type: 'allSorts',
          sortIds,
          successCount,
          failCount,
          results,
          count: successCount // 成功渲染的分类数量
        });
        
      } catch (error) {
        monitor.recordRenderFailure(batchTaskId, error);
        throw error;
      }
    } else {
      await renderPages(type, params);
    }
    
    const duration = Date.now() - startTime;
    
    logger.info('Page render request completed successfully', { 
      requestId, 
      type, 
      params, 
      duration: `${duration}ms` 
    });
    
    res.json({ 
      status: 'ok', 
      type, 
      params,
      requestId,
      duration: `${duration}ms`,
      timestamp: new Date().toISOString()
    });
  } catch (e) {
    logger.error('Page render request failed', { 
      requestId, 
      type, 
      params,
      error: e.message, 
      stack: e.stack 
    });
    res.status(500).json({ 
      message: e.message,
      requestId,
      type,
      params,
      timestamp: new Date().toISOString()
    });
  }
});

// 新增：服务状态监控API
app.get('/status', (req, res) => {
  const requestId = req.requestId;
  // 移除状态请求日志，避免刷屏
  
  const stats = monitor.getStats();
  res.json({
    service: 'prerender-worker',
    version: '2.0.0',
    status: 'running',
    requestId,
    timestamp: new Date().toISOString(),
    ...stats
  });
});

// 新增：详细的健康检查API
app.get('/health', (req, res) => {
  const requestId = req.requestId;
  
  try {
    // 检查模板文件
    const templatePath = path.resolve('/app/dist/index.html');
    const templateExists = fs.existsSync(templatePath);
    
    // 检查输出目录
    const outputPath = process.env.PRERENDER_OUTPUT || path.resolve(__dirname, './dist/prerender');
    const outputDirExists = fs.existsSync(outputPath);
    
    // 内存使用检查
    const memUsage = process.memoryUsage();
    const memoryHealthy = memUsage.heapUsed < 500 * 1024 * 1024; // 500MB
    
    // 运行时间检查
    const uptime = process.uptime();
    
    const health = {
      status: templateExists && outputDirExists && memoryHealthy ? 'healthy' : 'unhealthy',
      requestId,
      timestamp: new Date().toISOString(),
      checks: {
        template: {
          status: templateExists ? 'ok' : 'missing',
          path: templatePath
        },
        outputDirectory: {
          status: outputDirExists ? 'ok' : 'missing',
          path: outputPath
        },
        memory: {
          status: memoryHealthy ? 'ok' : 'high',
          usage: {
            heapUsed: `${(memUsage.heapUsed / 1024 / 1024).toFixed(1)}MB`,
            heapTotal: `${(memUsage.heapTotal / 1024 / 1024).toFixed(1)}MB`,
            rss: `${(memUsage.rss / 1024 / 1024).toFixed(1)}MB`
          }
        },
        uptime: `${Math.floor(uptime)}s`,
        runningTasks: monitor.stats.currentTasks.size
      }
    };
    
    // 移除健康检查完成日志，避免刷屏
    
    const statusCode = health.status === 'healthy' ? 200 : 503;
    res.status(statusCode).json(health);
    
  } catch (error) {
    logger.error('Health check failed', { requestId, error: error.message });
    res.status(500).json({
      status: 'error',
      requestId,
      error: error.message,
      timestamp: new Date().toISOString()
    });
  }
});

// 新增：实时日志API
app.get('/logs', (req, res) => {
  const requestId = req.requestId;
  const limit = parseInt(req.query.limit) || 100;
  const level = req.query.level; // 可选：按级别过滤
  
  // 移除内存日志请求的日志，避免刷屏
  
  let logs = logger.getMemoryLogs(limit);
  
  // 按级别过滤
  if (level && ['error', 'warn', 'info', 'debug'].includes(level)) {
    logs = logs.filter(log => log.level === level);
  }
  
  res.json({
    requestId,
    timestamp: new Date().toISOString(),
    logs,
    total: logs.length,
    memoryTotal: logger.memoryLogs.length
  });
});

// 新增：日志文件列表API
app.get('/logs/files', (req, res) => {
  const requestId = req.requestId;
  
  // 移除日志文件列表请求的日志，避免刷屏
  
  const files = logger.getLogFiles();
  res.json({
    requestId,
    timestamp: new Date().toISOString(),
    files: files.map(file => ({
      name: file.name,
      size: `${(file.size / 1024).toFixed(1)}KB`,
      modified: file.modified,
      date: file.name.replace('prerender-', '').replace('.log', '')
    }))
  });
});

// 新增：读取特定日志文件API
app.get('/logs/files/:filename', (req, res) => {
  const requestId = req.requestId;
  const filename = req.params.filename;
  const lines = parseInt(req.query.lines) || 1000;
  const level = req.query.level;
  
  // 移除日志文件读取请求的日志，避免刷屏
  
  // 安全检查
  if (!filename.match(/^prerender-\d{4}-\d{2}-\d{2}\.log$/)) {
    return res.status(400).json({
      error: 'Invalid filename format',
      requestId,
      timestamp: new Date().toISOString()
    });
  }
  
  let logs = logger.readLogFile(filename, lines);
  
  // 按级别过滤
  if (level && ['error', 'warn', 'info', 'debug'].includes(level)) {
    logs = logs.filter(log => log.level === level);
  }
  
  res.json({
    requestId,
    timestamp: new Date().toISOString(),
    filename,
    logs,
    total: logs.length
  });
});

// 新增：下载日志文件API
app.get('/logs/download/:filename', (req, res) => {
  const requestId = req.requestId;
  const filename = req.params.filename;
  
  logger.info('Log file download request', { requestId, filename });
  
  // 安全检查
  if (!filename.match(/^prerender-\d{4}-\d{2}-\d{2}\.log$/)) {
    return res.status(400).json({
      error: 'Invalid filename format',
      requestId
    });
  }
  
  const filePath = path.join(logger.logDir, filename);
  
  if (!fs.existsSync(filePath)) {
    return res.status(404).json({
      error: 'File not found',
      requestId,
      filename
    });
  }
  
  res.download(filePath, filename, (err) => {
    if (err) {
      logger.error('Log file download failed', { requestId, filename, error: err.message });
    } else {
      logger.info('Log file downloaded successfully', { requestId, filename });
    }
  });
});

// 新增：详细的监控仪表板数据API
app.get('/monitor/dashboard', (req, res) => {
  const requestId = req.requestId;
  
  // 移除仪表板数据请求的日志，避免刷屏
  
  const stats = monitor.getStats();
  const recentErrors = monitor.getRecentErrors(10);
  const recentLogs = logger.getMemoryLogs(50);
  const logFiles = logger.getLogFiles();
  const logDiskUsage = logger.getLogDiskUsage();
  
  // 计算一些额外的统计信息
  const now = new Date();
  const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
  
  const recentActivity = recentLogs.filter(log => 
    new Date(log.timestamp) > oneHourAgo
  );
  
  const errorCount = recentActivity.filter(log => log.level === 'error').length;
  const warnCount = recentActivity.filter(log => log.level === 'warn').length;
  
  // 格式化内存信息以匹配前端期望
  const memUsage = process.memoryUsage();
  
  res.json({
    requestId,
    timestamp: new Date().toISOString(),
    stats: {
      // 确保所有前端期望的字段都存在
      totalRequests: stats.totalRequests || 0,
      successfulRenders: stats.successfulRenders || 0,
      failedRenders: stats.failedRenders || 0,
      averageRenderTime: stats.averageRenderTime || 0,
      currentTasks: stats.currentTasks || [],
      recentTasks: stats.recentTasks || [], // 添加任务历史到stats中
      // 添加额外的统计信息
      articlesRendered: stats.articlesRendered || 0,
      pagesRendered: stats.pagesRendered || 0,
      successRate: stats.successRate || 0,
      lastRenderTime: stats.lastRenderTime,
      recentActivity: {
        totalLogs: recentActivity.length,
        errorCount,
        warnCount,
        infoCount: recentActivity.filter(log => log.level === 'info').length,
        timeRange: '1 hour'
      }
    },
    recentErrors,
    recentLogs: recentLogs.slice(0, 20),
    logFiles: logFiles.slice(0, 7), // 最近7天的日志文件
    logDiskUsage, // 添加日志磁盘使用情况
    systemInfo: {
      nodeVersion: process.version,
      uptime: `${Math.floor(process.uptime())}s`,
      // 格式化内存信息以匹配前端期望的结构
      memory: {
        used: memUsage.heapUsed,
        total: memUsage.heapTotal,
        rss: memUsage.rss,
        external: memUsage.external
      },
      pid: process.pid
    }
  });
});

// 新增：错误日志API
app.get('/errors', (req, res) => {
  const requestId = req.requestId;
  const limit = parseInt(req.query.limit) || 10;
  
  logger.debug('Error log request', { requestId, limit });
  
  const errors = monitor.getRecentErrors(limit);
  res.json({
    requestId,
    timestamp: new Date().toISOString(),
    errors,
    total: monitor.stats.errors.length
  });
});

// 新增：日志磁盘使用情况API
app.get('/logs/usage', (req, res) => {
  const requestId = req.requestId;
  
  logger.debug('Log disk usage request', { requestId });
  
  const usage = logger.getLogDiskUsage();
  res.json({
    requestId,
    timestamp: new Date().toISOString(),
    ...usage
  });
});

// 新增：手动日志清理API
app.post('/logs/cleanup', (req, res) => {
  const requestId = req.requestId;
  const { retentionDays } = req.body || {};
  
  logger.info('Manual log cleanup request', { requestId, retentionDays });
  
  try {
    const result = logger.manualCleanup(retentionDays);
    res.json({
      requestId,
      timestamp: new Date().toISOString(),
      ...result
    });
  } catch (error) {
    logger.error('Manual log cleanup failed', { requestId, error: error.message });
    res.status(500).json({
      success: false,
      error: error.message,
      requestId,
      timestamp: new Date().toISOString()
    });
  }
});

// 新增：清理预渲染数据API
app.post('/cleanup', (req, res) => {
  const requestId = req.requestId;
  const options = req.body || {};
  
  logger.info('Cleanup request received', { requestId, options });
  
  try {
    const results = {};
    
    // 清理预渲染文件
    if (options.clearFiles !== false) {
      const prerenderDir = process.env.PRERENDER_OUTPUT || path.resolve(__dirname, './dist/prerender');
      if (fs.existsSync(prerenderDir)) {
        const deletedFiles = clearDirectory(prerenderDir);
        results.deletedFiles = deletedFiles;
        logger.info('Prerender files cleared', { requestId, deletedFiles });
      }
    }
    
    // 清理内存缓存
    if (options.clearMemory !== false) {
      monitor.clearStats();
      logger.memoryLogs = [];
      results.memoryCleared = true;
      logger.info('Memory cache cleared', { requestId });
    }
    
    // 清理日志文件（可选，默认不清理）
    if (options.clearLogs === true) {
      const logFiles = logger.getLogFiles();
      const deletedLogs = logFiles.length;
      logFiles.forEach(file => {
        try {
          fs.unlinkSync(file.path);
        } catch (e) {
          logger.warn('Failed to delete log file', { requestId, file: file.name, error: e.message });
        }
      });
      results.deletedLogs = deletedLogs;
      logger.info('Log files cleared', { requestId, deletedLogs });
    }
    
    // 手动触发日志清理（使用自定义保留天数）
    if (options.cleanupLogs === true) {
      const retentionDays = options.logRetentionDays || null;
      const cleanupResult = logger.manualCleanup(retentionDays);
      results.logCleanup = cleanupResult;
      logger.info('Manual log cleanup triggered', { requestId, retentionDays, result: cleanupResult });
    }
    
    res.json({
      success: true,
      requestId,
      timestamp: new Date().toISOString(),
      results
    });
    
  } catch (error) {
    logger.error('Cleanup failed', { requestId, error: error.message, stack: error.stack });
    res.status(500).json({
      success: false,
      error: error.message,
      requestId,
      timestamp: new Date().toISOString()
    });
  }
});



// 工具函数：清理目录
function clearDirectory(dirPath) {
  let deletedCount = 0;
  
  if (!fs.existsSync(dirPath)) {
    return deletedCount;
  }
  
  function deleteRecursive(dir) {
    const files = fs.readdirSync(dir);
    
    files.forEach(file => {
      const filePath = path.join(dir, file);
      const stat = fs.statSync(filePath);
      
      if (stat.isDirectory()) {
        deleteRecursive(filePath);
        fs.rmdirSync(filePath);
      } else {
        fs.unlinkSync(filePath);
        deletedCount++;
      }
    });
  }
  
  try {
    deleteRecursive(dirPath);
    return deletedCount;
  } catch (error) {
    logger.error('Failed to clear directory', { dirPath, error: error.message });
    return deletedCount;
  }
}

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => {
  logger.info('Prerender worker started', {
    port: PORT,
    nodeVersion: process.version,
    timestamp: new Date().toISOString(),
    environment: {
      JAVA_BACKEND_URL,
      PYTHON_BACKEND_URL,
      LOG_LEVEL: process.env.LOG_LEVEL || 'info',
      PRERENDER_OUTPUT: process.env.PRERENDER_OUTPUT || 'default'
    }
  });
  console.log(`🚀 Prerender worker listening on port ${PORT}`);
});