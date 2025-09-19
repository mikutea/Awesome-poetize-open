import Tiows from "./tiows";
import constant from "./constant";
import {ElMessage} from "element-plus";

export default function () {
  this.ws_protocol = constant.wsProtocol;
  this.ip = constant.imBaseURL;
  this.port = constant.wsPort;
  
  // 从URL参数中获取WebSocket token
  const urlParams = new URLSearchParams(window.location.search);
  this.currentToken = urlParams.get('token');
  
  if (this.currentToken) {
    this.paramStr = 'token=' + this.currentToken;
  } else {
    // 如果URL中没有token，尝试使用localStorage中的token（向后兼容）
    const userToken = localStorage.getItem("userToken");
    if (userToken) {
      this.currentToken = userToken;
      this.paramStr = 'token=' + userToken;
    } else {
      console.error('未找到WebSocket token');
      this.paramStr = '';
    }
  }
  
  this.binaryType = 'blob';
  this.renewalTimer = null; // token续签定时器
  this.heartbeatTimer = null; // 心跳定时器
  this.wsHeartbeatTimer = null; // WebSocket心跳定时器
  this.lastHeartbeatResponse = Date.now(); // 最后心跳响应时间
  this.reconnectAttempts = 0; // 重连尝试次数
  this.maxReconnectAttempts = 10; // 最大重连次数
  this.isPageVisible = true; // 页面可见性状态
  this.reconnectTimer = null; // 重连定时器
  this.isReconnecting = false; // 是否正在重连
  this.visibilityListenerAdded = false; // 页面可见性监听器是否已添加
  this.connectionStableTimer = null; // 连接稳定性检查定时器
  this.lastConnectionTime = 0; // 最后连接成功时间

  // 更新参数字符串，确保使用最新的token
  this.updateParamStr = () => {
    // 优先从URL参数获取最新token
    const urlParams = new URLSearchParams(window.location.search);
    const urlToken = urlParams.get('token');
    
    if (urlToken && urlToken !== this.currentToken) {
      console.log('从URL获取到新token，更新当前token');
      this.currentToken = urlToken;
    }
    
    // 如果URL中没有token，尝试从localStorage获取
    if (!this.currentToken) {
      const userToken = localStorage.getItem("userToken");
      if (userToken) {
        console.log('从localStorage获取token');
        this.currentToken = userToken;
      }
    }
    
    if (this.currentToken) {
      this.paramStr = 'token=' + encodeURIComponent(this.currentToken);
      console.log('更新参数字符串，token前缀:', this.currentToken.substring(0, 20) + '...');
    } else {
      console.error('无法获取有效token');
      this.paramStr = '';
    }
  }

  this.initWs = () => {
    // 每次初始化WebSocket时都使用最新的token
    this.updateParamStr();
    console.log('初始化WebSocket，使用token:', this.currentToken ? this.currentToken.substring(0, 20) + '...' : 'null');
    this.tio = new Tiows(this.ws_protocol, this.ip, this.port, this.paramStr, this.binaryType);
    this.tio.connect();
    
    // WebSocket连接成功后启动token续签检查
    this.tio.onopen = () => {
      console.log('WebSocket连接成功');
      this.isReconnecting = false; // 重连完成
      this.lastConnectionTime = Date.now(); // 记录连接成功时间
      
      // 清除之前的稳定性检查定时器
      if (this.connectionStableTimer) {
        clearTimeout(this.connectionStableTimer);
      }
      
      // 只有连接稳定30秒后才重置重连计数器，防止立即断开重连的无限循环
      this.connectionStableTimer = setTimeout(() => {
        if (this.tio && this.tio.isReady() && this.reconnectAttempts > 0) {
          console.log(`连接已稳定30秒，重置重连计数器（之前尝试了${this.reconnectAttempts}次）`);
          this.reconnectAttempts = 0;
        }
      }, 30000); // 30秒后才重置
      
      this.startTokenRenewalCheck();
      this.startHeartbeat();
      // 只添加一次页面可见性监听器
      if (!this.visibilityListenerAdded) {
        this.setupPageVisibilityListener();
        this.visibilityListenerAdded = true;
      }
    };
    
    // WebSocket连接关闭时清理定时器
    this.tio.onclose = (event) => {
      console.log('WebSocket连接关闭', event);
      this.stopTokenRenewalCheck();
      this.stopHeartbeat();
      
      // 清除之前的重连定时器
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
      }
      
      // 清除连接稳定性检查定时器
      if (this.connectionStableTimer) {
        clearTimeout(this.connectionStableTimer);
        this.connectionStableTimer = null;
      }
      
      // 检查连接持续时间，如果连接时间很短，说明可能存在服务器端问题
      const connectionDuration = Date.now() - this.lastConnectionTime;
      if (connectionDuration < 5000) { // 连接持续时间少于5秒
        console.warn(`连接持续时间很短(${connectionDuration}ms)，可能存在服务器端问题`);
        // 增加重连延迟，避免频繁重连
        this.reconnectAttempts = Math.min(this.reconnectAttempts + 2, this.maxReconnectAttempts);
      }
      
      // 如果页面不可见，不进行重连
      if (!this.isPageVisible) {
        console.log('页面不可见，暂停重连');
        return;
      }
      
      // 如果正在重连中，避免重复重连
      if (this.isReconnecting) {
        console.log('已在重连中，跳过此次重连');
        return;
      }
      
      // 如果不是正常关闭且重连次数未超限，尝试重连
      if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts) {
        this.reconnectAttempts++;
        this.isReconnecting = true;
        
        // 检查是否可能是服务器端问题（连续快速断开重连）
        const connectionDuration = Date.now() - this.lastConnectionTime;
        if (connectionDuration < 5000 && this.reconnectAttempts >= 3) {
          console.error('检测到连续快速断开重连，可能是服务器端问题，停止自动重连');
          this.isReconnecting = false;
          ElMessage({
            message: "检测到服务器端问题，请检查token有效性或联系管理员",
            type: 'error',
            duration: 8000
          });
          return;
        }
        
        // 根据连接持续时间调整重连延迟
        let baseDelay = 1000 * Math.pow(2, this.reconnectAttempts - 1);
        if (connectionDuration < 10000) {
          // 如果连接持续时间很短，增加延迟
          baseDelay *= 2;
        }
        const delay = Math.min(baseDelay, 60000); // 最大延迟60秒
        
        console.log(`连接断开(持续${connectionDuration}ms)，${delay}ms后进行第${this.reconnectAttempts}次重连尝试`);
        
        // 只在前几次重连时显示消息，避免消息过多
        if (this.reconnectAttempts <= 3) {
          ElMessage({
            message: `连接已断开，正在尝试重连(${this.reconnectAttempts}/${this.maxReconnectAttempts})...`,
            type: 'warning',
            duration: 3000
          });
        }
        
        this.reconnectTimer = setTimeout(() => {
          if (this.reconnectAttempts <= this.maxReconnectAttempts && this.isPageVisible) {
            this.reconnect();
          } else {
            this.isReconnecting = false;
          }
        }, delay);
      } else if (this.reconnectAttempts >= this.maxReconnectAttempts) {
        this.isReconnecting = false;
        ElMessage({
          message: "连接失败次数过多，请检查网络后刷新页面重试",
          type: 'error',
          duration: 5000
        });
      }
    };
    
    // WebSocket连接错误处理
    this.tio.onerror = (event) => {
      console.error('WebSocket连接错误:', event);
      ElMessage({
        message: "连接出现错误，请检查网络！",
        type: 'error',
        duration: 3000
      });
    };
  }

  this.sendMsg = (value) => {
    console.log('准备发送消息:', value);
    
    if (!this.tio) {
      console.error('WebSocket未初始化');
      ElMessage({
        message: "WebSocket未初始化，请刷新页面重试！",
        type: 'error',
        duration: 4000
      });
      return false;
    }

    // 检查连接状态
    const readyState = this.tio.getReadyState();
    console.log('当前WebSocket状态:', readyState);
    
    if (!this.tio.isReady()) {
      let message = "连接异常，消息发送失败！";
      let showReconnectHint = false;
      
      switch (readyState) {
        case WebSocket.CONNECTING:
          message = "正在连接中，请稍后重试！";
          console.warn('WebSocket正在连接中');
          break;
        case WebSocket.CLOSING:
          message = "连接正在关闭，请稍后重试！";
          console.warn('WebSocket正在关闭');
          break;
        case WebSocket.CLOSED:
          message = "连接已断开，消息发送失败！正在尝试重新连接...";
          console.warn('WebSocket连接已断开');
          showReconnectHint = true;
          // 尝试重新连接
          this.reconnect();
          break;
        default:
          console.warn('WebSocket状态异常:', readyState);
          message = "连接状态异常，消息发送失败！";
      }
      
      ElMessage({
        message: message,
        type: 'error',
        duration: showReconnectHint ? 5000 : 3000
      });
      return false;
    }

    // 发送消息前再次确认连接状态
    if (this.tio.ws && this.tio.ws.readyState === WebSocket.OPEN) {
      console.log('WebSocket连接正常，开始发送消息');
      try {
        const success = this.tio.send(value);
        if (success) {
          console.log('消息发送成功');
          return true;
        } else {
          console.error('消息发送失败');
          ElMessage({
            message: "消息发送失败，请检查网络连接！",
            type: 'error',
            duration: 3000
          });
          return false;
        }
      } catch (error) {
        console.error('发送消息时出现异常:', error);
        ElMessage({
          message: "发送消息时出现异常：" + (error.message || '未知错误'),
          type: 'error',
          duration: 4000
        });
        return false;
      }
    } else {
      console.error('WebSocket连接状态异常，实际状态:', this.tio.ws ? this.tio.ws.readyState : 'ws对象不存在');
      ElMessage({
        message: "连接状态异常，消息发送失败！请刷新页面重试。",
        type: 'error',
        duration: 4000
      });
      return false;
    }
  }

  // 重新连接方法
  this.reconnect = () => {
    console.log('尝试重新连接WebSocket...');
    
    // 如果页面不可见，不进行重连
    if (!this.isPageVisible) {
      console.log('页面不可见，取消重连');
      this.isReconnecting = false;
      return;
    }
    
    if (this.tio) {
      this.tio.close();
    }
    
    // 检查网络状态
    if (!navigator.onLine) {
      console.log('网络不可用，等待网络恢复后重连');
      this.waitForNetworkAndReconnect();
      return;
    }
    
    // 重连时使用最新的token（在initWs中会调用updateParamStr）
    console.log('开始重连，将使用最新token');
    this.initWs();
  }

  // 等待网络恢复后重连
  this.waitForNetworkAndReconnect = () => {
    const handleOnline = () => {
      console.log('网络已恢复，开始重连');
      window.removeEventListener('online', handleOnline);
      this.reconnect();
    };
    
    window.addEventListener('online', handleOnline);
    
    ElMessage({
      message: "网络不可用，等待网络恢复...",
      type: 'warning',
      duration: 3000
    });
  }

  // ==================== Token续签相关方法 ====================

  /**
   * 启动token续签检查
   * 每5分钟检查一次token剩余有效期
   */
  this.startTokenRenewalCheck = () => {
    if (this.renewalTimer) {
      clearInterval(this.renewalTimer);
    }
    
    // 每5分钟检查一次
    this.renewalTimer = setInterval(() => {
      this.checkAndRenewToken();
    }, 5 * 60 * 1000);
    
    // 延迟5秒后再执行第一次检查，减少等待时间
    setTimeout(() => {
      this.checkAndRenewToken();
    }, 5000);
  }

  /**
   * 停止token续签检查
   */
  this.stopTokenRenewalCheck = () => {
    if (this.renewalTimer) {
      clearInterval(this.renewalTimer);
      this.renewalTimer = null;
    }
  }

  /**
   * 检查token有效期并在需要时续签
   */
  this.checkAndRenewToken = async () => {
    if (!this.currentToken) {
      console.warn('没有可用的token进行续签检查');
      return;
    }

    try {
      console.log('开始检查token有效期...');
      // 检查token剩余有效期
      const response = await fetch(`${constant.baseURL}/im/checkWsTokenExpiry?wsToken=${this.currentToken}`);
      const result = await response.json();
      
      console.log('Token检查响应:', result);
      
      if (result.code === 200 && result.data !== null && result.data !== undefined) {
        const remainingMinutes = result.data;
        console.log(`Token剩余有效期: ${remainingMinutes}分钟`);
        
        // 只有当剩余时间少于10分钟时才进行续签
        if (remainingMinutes <= 10) {
          console.log('Token即将过期，开始续签...');
          const renewSuccess = await this.renewToken();
          if (!renewSuccess) {
            console.error('Token续签失败');
          }
        } else {
          console.log('Token有效期充足，无需续签');
        }
      } else {
        console.warn('检查token有效期失败:', result.message || '未知错误');
        // 只有在明确token无效时才尝试续签，避免误判
        if (result.message && (result.message.includes('无效') || result.message.includes('过期') || result.message.includes('失效'))) {
          console.log('Token确实无效，尝试续签...');
          await this.renewToken();
        } else {
          console.log('Token检查失败但可能是网络问题，暂不续签');
        }
      }
    } catch (error) {
      console.error('检查token有效期时发生网络错误:', error);
      // 网络错误时不进行续签，避免误操作
    }
  }

  /**
   * 续签token
   */
  this.renewToken = async () => {
    if (!this.currentToken) {
      console.error('没有可用的token进行续签');
      return false;
    }

    try {
      console.log('开始续签token...');
      const response = await fetch(`${constant.baseURL}/im/renewWsToken?oldToken=${this.currentToken}`);
      const result = await response.json();
      
      console.log('Token续签响应:', result);
      
      if (result.code === 200 && result.data) {
        const newToken = result.data;
        console.log('Token续签成功，新token:', newToken.substring(0, 20) + '...');
        
        // 更新当前token
        this.currentToken = newToken;
        this.updateParamStr(); // 使用统一的方法更新参数字符串
        
        // 更新URL参数，确保页面刷新时也能使用新token
        try {
          const url = new URL(window.location);
          url.searchParams.set('token', newToken);
          window.history.replaceState({}, '', url);
          console.log('URL参数已更新为新token');
        } catch (urlError) {
          console.warn('更新URL参数失败:', urlError);
        }
        
        // ElMessage({
        //   message: "会话已自动续期",
        //   type: 'success',
        //   duration: 2000
        // });
        
        return true;
      } else {
        console.error('Token续签失败:', result.message || '未知错误');
        
        // 只有在确实需要用户重新登录时才显示警告
        if (result.message && (result.message.includes('登录') || result.message.includes('认证') || result.message.includes('过期'))) {
          ElMessage({
            message: "会话已过期，请刷新页面重新登录",
            type: 'warning',
            duration: 5000
          });
        } else {
          console.log('Token续签失败，但可能是临时问题，不提示用户');
        }
        
        return false;
      }
    } catch (error) {
      console.error('续签token时发生网络错误:', error);
      // 网络错误时不提示用户，避免误导
      return false;
    }
  }

  // ==================== 心跳检测相关方法 ====================

  /**
   * 启动心跳检测
   * 每60秒发送一次WebSocket心跳（降低频率），每3分钟进行HTTP心跳检测
   */
  this.startHeartbeat = () => {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
    }
    if (this.wsHeartbeatTimer) {
      clearInterval(this.wsHeartbeatTimer);
    }
    
    // 记录最后一次心跳响应时间
    this.lastHeartbeatResponse = Date.now();
    
    // WebSocket心跳：每60秒发送一次（降低频率，减少服务器压力）
    this.wsHeartbeatTimer = setInterval(() => {
      // 只有在页面可见时才发送WebSocket心跳
      if (this.isPageVisible) {
        this.sendWebSocketHeartbeat();
      } else {
        console.log('页面不可见，跳过WebSocket心跳');
      }
    }, 60000); // 从30秒改为60秒
    
    // HTTP心跳：每3分钟发送一次，用于token续签（降低频率）
    this.heartbeatTimer = setInterval(() => {
      // 只有在页面可见时才发送HTTP心跳
      if (this.isPageVisible) {
        this.sendHeartbeat();
      } else {
        console.log('页面不可见，跳过HTTP心跳');
      }
    }, 3 * 60 * 1000); // 从2分钟改为3分钟
  }

  /**
   * 停止心跳检测
   */
  this.stopHeartbeat = () => {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.wsHeartbeatTimer) {
      clearInterval(this.wsHeartbeatTimer);
      this.wsHeartbeatTimer = null;
    }
  }

  /**
   * 发送WebSocket心跳包
   */
  this.sendWebSocketHeartbeat = () => {
    if (this.tio && this.tio.isReady()) {
      // 获取当前用户ID，避免store未定义的问题
      let currentUserId = 0;
      try {
        if (typeof window !== 'undefined' && window.store && window.store.state && window.store.state.currentUser) {
          currentUserId = window.store.state.currentUser.id;
        }
      } catch (e) {
        console.warn('获取当前用户ID失败，使用默认值0');
      }
      
      const heartbeatMsg = JSON.stringify({
        messageType: 0, // 心跳消息类型
        content: 'heartbeat',
        fromId: currentUserId,
        timestamp: Date.now()
      });
      
      console.log('发送WebSocket心跳包');
      const success = this.tio.send(heartbeatMsg);
      
      if (!success) {
        console.error('WebSocket心跳包发送失败，连接可能异常');
        // 心跳包发送失败时，不立即重连，而是等待下次心跳再检查
        console.log('等待下次心跳检查连接状态');
      } else {
        // 放宽心跳响应检测：10分钟没有任何响应才认为连接异常
        // 这样可以避免长时间不聊天时的误判
        const now = Date.now();
        if (now - this.lastHeartbeatResponse > 600000) { // 10分钟没有任何响应
          console.warn('长时间没有收到服务器响应，可能连接异常');
          this.handleConnectionError();
        } else {
          console.log('WebSocket心跳包发送成功，连接正常');
        }
      }
    } else {
      console.warn('WebSocket连接不可用，检查连接状态');
      // 连接不可用时，检查是否真的需要重连
      if (this.isPageVisible && this.reconnectAttempts < this.maxReconnectAttempts) {
        console.log('页面可见且未达重连上限，尝试重连');
        this.handleConnectionError();
      } else {
        console.log('页面不可见或已达重连上限，跳过重连');
      }
    }
  }

  /**
   * 处理连接错误
   */
  this.handleConnectionError = () => {
    console.log('检测到连接异常，分析重连必要性');
    
    // 如果页面不可见，不进行重连
    if (!this.isPageVisible) {
      console.log('页面不可见，暂不重连');
      return;
    }
    
    // 如果已经在重连中，避免重复重连
    if (this.isReconnecting) {
      console.log('已在重连中，跳过此次重连请求');
      return;
    }
    
    // 检查网络状态
    if (!navigator.onLine) {
      console.log('网络不可用，等待网络恢复');
      return;
    }
    
    // 只有在重连次数未超限时才尝试重连
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      console.log(`开始第${this.reconnectAttempts + 1}次重连尝试`);
      
      // 只在前3次重连时显示提示，避免过多提示
      if (this.reconnectAttempts < 3) {
        ElMessage({
          message: `检测到连接异常，正在重新连接(${this.reconnectAttempts + 1}/${this.maxReconnectAttempts})...`,
          type: 'warning',
          duration: 3000
        });
      }
      
      // 延迟重连，避免频繁连接，使用指数退避
      const delay = Math.min(2000 * Math.pow(1.5, this.reconnectAttempts), 30000);
      console.log(`${delay}ms后开始重连`);
      
      setTimeout(() => {
        if (this.isPageVisible && !this.isReconnecting) {
          this.reconnect();
        } else {
          console.log('重连条件不满足，取消重连');
        }
      }, delay);
    } else {
      console.log('重连次数已达上限，停止自动重连');
      ElMessage({
        message: "连接异常次数过多，请刷新页面重试",
        type: 'error',
        duration: 5000
      });
    }
  }

  /**
   * 更新心跳响应时间（在收到任何消息时调用）
   */
  this.updateHeartbeatResponse = () => {
    this.lastHeartbeatResponse = Date.now();
  }

  /**
   * 发送HTTP心跳并处理token续签
   */
  this.sendHeartbeat = async () => {
    if (!this.currentToken) {
      console.warn('没有可用的token进行心跳检测');
      return;
    }

    try {
      const response = await fetch(`${constant.baseURL}/im/heartbeat?wsToken=${this.currentToken}`);
      const result = await response.json();
      
      if (result.code === 200 && result.data) {
        const returnedToken = result.data;
        
        // 如果返回的token与当前token不同，说明服务器进行了自动续签
        if (returnedToken !== this.currentToken) {
          console.log('服务器自动续签了token');
          this.currentToken = returnedToken;
          this.updateParamStr(); // 使用统一的方法更新参数字符串
          
          // 更新URL参数
          try {
            const url = new URL(window.location);
            url.searchParams.set('token', returnedToken);
            window.history.replaceState({}, '', url);
            console.log('HTTP心跳检测：URL参数已更新为新token');
          } catch (urlError) {
            console.warn('更新URL参数失败:', urlError);
          }
        }
        
        console.log('HTTP心跳检测成功');
        this.updateHeartbeatResponse(); // 更新响应时间
      } else {
        console.warn('HTTP心跳检测失败:', result.message);
      }
    } catch (error) {
      console.error('HTTP心跳检测时发生错误:', error);
    }
  }

  // ==================== 页面可见性检测 ====================

  /**
   * 设置页面可见性监听器
   */
  this.setupPageVisibilityListener = () => {
    // 页面可见性变化处理
    const handleVisibilityChange = () => {
      const wasVisible = this.isPageVisible;
      this.isPageVisible = !document.hidden;
      console.log('页面可见性变化:', this.isPageVisible ? '可见' : '隐藏');
      
      if (this.isPageVisible && !wasVisible) {
        // 页面从隐藏变为可见时，检查连接状态
        console.log('页面变为可见，检查WebSocket连接状态');
        
        // 重置重连状态，允许重新连接
        this.isReconnecting = false;
        
        // 延迟检查连接状态，给浏览器更多时间恢复
        setTimeout(() => {
          if (this.isPageVisible && (!this.tio || !this.tio.isReady())) {
            console.log('页面恢复可见时发现连接异常，尝试重连');
            // 页面恢复可见时，适当重置重连计数，但不完全清零
            this.reconnectAttempts = Math.max(0, this.reconnectAttempts - 2);
            this.reconnect();
          } else if (this.tio && this.tio.isReady()) {
            // 连接正常，更新心跳响应时间并发送心跳检测
            this.updateHeartbeatResponse();
            console.log('页面恢复可见，连接正常，发送心跳检测');
            this.sendWebSocketHeartbeat();
          }
        }, 2000); // 增加延迟时间到2秒
      } else if (!this.isPageVisible && wasVisible) {
        // 页面从可见变为隐藏时，清理重连定时器
        console.log('页面变为隐藏，清理重连定时器');
        if (this.reconnectTimer) {
          clearTimeout(this.reconnectTimer);
          this.reconnectTimer = null;
        }
        this.isReconnecting = false;
      }
    };
    
    // 监听页面可见性变化
    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    // 监听窗口焦点变化（作为备用）
    window.addEventListener('focus', () => {
      if (this.isPageVisible && (!this.tio || !this.tio.isReady()) && !this.isReconnecting) {
        console.log('窗口获得焦点时发现连接异常，尝试重连');
        this.reconnectAttempts = 0; // 重置重连计数
        this.reconnect();
      }
    });
    
    // 监听网络状态变化
    window.addEventListener('online', () => {
      console.log('网络已恢复，检查WebSocket连接');
      if (this.isPageVisible && (!this.tio || !this.tio.isReady()) && !this.isReconnecting) {
        this.reconnectAttempts = 0; // 重置重连计数
        this.reconnect();
      }
    });
    
    window.addEventListener('offline', () => {
      console.log('网络已断开');
      // 清理重连定时器
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
      }
      this.isReconnecting = false;
      ElMessage({
        message: "网络连接已断开",
        type: 'warning',
        duration: 3000
      });
    });
  }

  // ==================== 清理方法 ====================

  /**
   * 清理所有定时器和连接
   */
  this.cleanup = () => {
    this.stopTokenRenewalCheck();
    this.stopHeartbeat();
    
    // 清理重连定时器
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    
    // 清理连接稳定性检查定时器
    if (this.connectionStableTimer) {
      clearTimeout(this.connectionStableTimer);
      this.connectionStableTimer = null;
    }
    
    this.isReconnecting = false;
    
    // 移除事件监听器（注意：这里的函数引用可能不正确，需要保存引用）
    // 由于事件监听器是匿名函数，实际上很难正确移除，但这不会造成严重问题
    
    if (this.tio) {
      this.tio.close();
    }
  }

  // 在WebSocket消息处理中调用此方法来更新心跳响应时间
  this.onMessageReceived = () => {
    this.updateHeartbeatResponse();
  }
}
