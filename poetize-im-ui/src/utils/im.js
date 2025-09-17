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

  this.initWs = () => {
    this.tio = new Tiows(this.ws_protocol, this.ip, this.port, this.paramStr, this.binaryType);
    this.tio.connect();
    
    // WebSocket连接成功后启动token续签检查
    this.tio.onopen = () => {
      console.log('WebSocket连接成功');
      this.startTokenRenewalCheck();
      this.startHeartbeat();
    };
    
    // WebSocket连接关闭时清理定时器
    this.tio.onclose = () => {
      console.log('WebSocket连接关闭');
      this.stopTokenRenewalCheck();
      this.stopHeartbeat();
    };
  }

  this.sendMsg = (value) => {
    if (this.tio && this.tio.ws && this.tio.ws.readyState === 1) {
      this.tio.send(value);
      return true;
    } else {
      ElMessage({
        message: "发送失败，请重试！",
        type: 'error'
      });
      return false;
    }
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
    
    // 立即执行一次检查
    setTimeout(() => {
      this.checkAndRenewToken();
    }, 1000);
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
      // 检查token剩余有效期
      const response = await fetch(`${constant.baseURL}/im/checkWsTokenExpiry?wsToken=${this.currentToken}`);
      const result = await response.json();
      
      if (result.flag && result.data) {
        const remainingMinutes = result.data;
        console.log(`Token剩余有效期: ${remainingMinutes}分钟`);
        
        // 如果剩余时间少于10分钟，进行续签
        if (remainingMinutes <= 10) {
          console.log('Token即将过期，开始续签...');
          await this.renewToken();
        }
      } else {
        console.warn('检查token有效期失败:', result.message);
        // token可能已经无效，尝试续签
        await this.renewToken();
      }
    } catch (error) {
      console.error('检查token有效期时发生错误:', error);
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
      const response = await fetch(`${constant.baseURL}/im/renewWsToken?oldToken=${this.currentToken}`);
      const result = await response.json();
      
      if (result.flag && result.data) {
        const newToken = result.data;
        console.log('Token续签成功');
        
        // 更新当前token
        this.currentToken = newToken;
        this.paramStr = 'token=' + newToken;
        
        // 可选：更新URL参数（如果需要）
        const url = new URL(window.location);
        url.searchParams.set('token', newToken);
        window.history.replaceState({}, '', url);
        
        return true;
      } else {
        console.error('Token续签失败:', result.message);
        ElMessage({
          message: "会话即将过期，请刷新页面重新登录",
          type: 'warning'
        });
        return false;
      }
    } catch (error) {
      console.error('续签token时发生错误:', error);
      return false;
    }
  }

  // ==================== 心跳检测相关方法 ====================

  /**
   * 启动心跳检测
   * 每2分钟发送一次心跳，保持连接活跃并自动续签
   */
  this.startHeartbeat = () => {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
    }
    
    // 每2分钟发送一次心跳
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
  }

  /**
   * 发送心跳并处理token续签
   */
  this.sendHeartbeat = async () => {
    if (!this.currentToken) {
      console.warn('没有可用的token进行心跳检测');
      return;
    }

    try {
      const response = await fetch(`${constant.baseURL}/im/heartbeat?wsToken=${this.currentToken}`);
      const result = await response.json();
      
      if (result.flag && result.data) {
        const returnedToken = result.data;
        
        // 如果返回的token与当前token不同，说明服务器进行了自动续签
        if (returnedToken !== this.currentToken) {
          console.log('服务器自动续签了token');
          this.currentToken = returnedToken;
          this.paramStr = 'token=' + returnedToken;
          
          // 更新URL参数
          const url = new URL(window.location);
          url.searchParams.set('token', returnedToken);
          window.history.replaceState({}, '', url);
        }
        
        console.log('心跳检测成功');
      } else {
        console.warn('心跳检测失败:', result.message);
      }
    } catch (error) {
      console.error('心跳检测时发生错误:', error);
    }
  }

  // ==================== 清理方法 ====================

  /**
   * 清理所有定时器和连接
   */
  this.cleanup = () => {
    this.stopTokenRenewalCheck();
    this.stopHeartbeat();
    if (this.tio) {
      this.tio.close();
    }
  }
}
