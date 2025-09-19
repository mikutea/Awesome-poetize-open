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

  this.initWs = () => {
    this.tio = new Tiows(this.ws_protocol, this.ip, this.port, this.paramStr, this.binaryType);
    this.tio.connect();
    
    // WebSocket连接成功后启动token续签检查
    this.tio.onopen = () => {
      console.log('WebSocket连接成功');
      this.reconnectAttempts = 0; // 重置重连计数
      this.startTokenRenewalCheck();
      this.startHeartbeat();
      this.setupPageVisibilityListener(); // 设置页面可见性监听
    };
    
    // WebSocket连接关闭时清理定时器
    this.tio.onclose = (event) => {
      console.log('WebSocket连接关闭', event);
      this.stopTokenRenewalCheck();
      this.stopHeartbeat();
      
      // 如果不是正常关闭且重连次数未超限，尝试重连
      if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts) {
        this.reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts - 1), 30000); // 指数退避，最大30秒
        
        console.log(`连接断开，${delay}ms后进行第${this.reconnectAttempts}次重连尝试`);
        
        ElMessage({
          message: `连接已断开，正在尝试重连(${this.reconnectAttempts}/${this.maxReconnectAttempts})...`,
          type: 'warning',
          duration: 3000
        });
        
        setTimeout(() => {
          if (this.reconnectAttempts <= this.maxReconnectAttempts) {
            this.reconnect();
          }
        }, delay);
      } else if (this.reconnectAttempts >= this.maxReconnectAttempts) {
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
        type: 'error'
      });
      return false;
    }

    // 检查连接状态
    const readyState = this.tio.getReadyState();
    console.log('当前WebSocket状态:', readyState);
    
    if (!this.tio.isReady()) {
      let message = "连接异常，请重试！";
      
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
          message = "连接已断开，正在重新连接...";
          console.warn('WebSocket连接已断开');
          // 尝试重新连接
          this.reconnect();
          break;
        default:
          console.warn('WebSocket状态异常:', readyState);
      }
      
      ElMessage({
        message: message,
        type: 'warning'
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
        } else {
          console.error('消息发送失败');
          ElMessage({
            message: "发送失败，请重试！",
            type: 'error'
          });
        }
        return success;
      } catch (error) {
        console.error('发送消息时出现异常:', error);
        ElMessage({
          message: "发送异常，请重试！",
          type: 'error'
        });
        return false;
      }
    } else {
      console.error('WebSocket连接状态异常，实际状态:', this.tio.ws ? this.tio.ws.readyState : 'ws对象不存在');
      ElMessage({
        message: "连接状态异常，请刷新页面重试！",
        type: 'error'
      });
      return false;
    }
  }

  // 重新连接方法
  this.reconnect = () => {
    console.log('尝试重新连接WebSocket...');
    if (this.tio) {
      this.tio.close();
    }
    
    // 检查网络状态
    if (!navigator.onLine) {
      console.log('网络不可用，等待网络恢复后重连');
      this.waitForNetworkAndReconnect();
      return;
    }
    
    // 立即重连
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
    
    // 延迟30秒后再执行第一次检查，给WebSocket连接充分的时间稳定
    setTimeout(() => {
      this.checkAndRenewToken();
    }, 30000);
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
        this.paramStr = 'token=' + newToken;
        
        // 可选：更新URL参数（如果需要）
        try {
          const url = new URL(window.location);
          url.searchParams.set('token', newToken);
          window.history.replaceState({}, '', url);
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
   * 每30秒发送一次WebSocket心跳，每2分钟进行HTTP心跳检测
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
    
    // WebSocket心跳：每30秒发送一次（页面可见时）
    this.wsHeartbeatTimer = setInterval(() => {
      // 只有在页面可见时才发送WebSocket心跳
      if (this.isPageVisible) {
        this.sendWebSocketHeartbeat();
      } else {
        console.log('页面不可见，跳过WebSocket心跳');
      }
    }, 30000);
    
    // HTTP心跳：每2分钟发送一次，用于token续签
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat();
    }, 2 * 60 * 1000);
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
        this.handleConnectionError();
      } else {
        // 检查是否长时间没有收到任何消息
        const now = Date.now();
        if (now - this.lastHeartbeatResponse > 120000) { // 2分钟没有任何响应
          console.warn('长时间没有收到服务器响应，可能连接异常');
          this.handleConnectionError();
        }
      }
    } else {
      console.warn('WebSocket连接不可用，无法发送心跳包');
      this.handleConnectionError();
    }
  }

  /**
   * 处理连接错误
   */
  this.handleConnectionError = () => {
    console.log('检测到连接异常，准备重新连接');
    
    // 只有在重连次数未超限时才尝试重连
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.stopHeartbeat();
      this.stopTokenRenewalCheck();
      
      ElMessage({
        message: "检测到连接异常，正在重新连接...",
        type: 'warning',
        duration: 3000
      });
      
      // 延迟重连，避免频繁连接
      setTimeout(() => {
        this.reconnect();
      }, 2000);
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
          this.paramStr = 'token=' + returnedToken;
          
          // 更新URL参数
          try {
            const url = new URL(window.location);
            url.searchParams.set('token', returnedToken);
            window.history.replaceState({}, '', url);
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
      this.isPageVisible = !document.hidden;
      console.log('页面可见性变化:', this.isPageVisible ? '可见' : '隐藏');
      
      if (this.isPageVisible) {
        // 页面变为可见时，检查连接状态
        console.log('页面变为可见，检查WebSocket连接状态');
        if (!this.tio || !this.tio.isReady()) {
          console.log('页面恢复可见时发现连接异常，尝试重连');
          this.reconnect();
        } else {
          // 立即发送一次心跳检测连接
          this.sendWebSocketHeartbeat();
        }
      }
    };
    
    // 监听页面可见性变化
    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    // 监听窗口焦点变化（作为备用）
    window.addEventListener('focus', () => {
      if (this.isPageVisible && (!this.tio || !this.tio.isReady())) {
        console.log('窗口获得焦点时发现连接异常，尝试重连');
        this.reconnect();
      }
    });
    
    // 监听网络状态变化
    window.addEventListener('online', () => {
      console.log('网络已恢复，检查WebSocket连接');
      if (!this.tio || !this.tio.isReady()) {
        this.reconnect();
      }
    });
    
    window.addEventListener('offline', () => {
      console.log('网络已断开');
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
    
    // 移除事件监听器
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    window.removeEventListener('focus', this.handleFocus);
    window.removeEventListener('online', this.handleOnline);
    window.removeEventListener('offline', this.handleOffline);
    
    if (this.tio) {
      this.tio.close();
    }
  }

  // 在WebSocket消息处理中调用此方法来更新心跳响应时间
  this.onMessageReceived = () => {
    this.updateHeartbeatResponse();
  }
}
