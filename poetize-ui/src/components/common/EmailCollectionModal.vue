<template>
  <div class="email-collection-modal" v-if="visible">
    <div class="modal-overlay" @click="handleSkip"></div>
    <div class="modal-content">
      <div class="modal-header">
        <h3>完善个人信息</h3>
        <i class="el-icon-close close-btn" @click="handleSkip"></i>
      </div>
      
      <div class="modal-body">
        <div class="welcome-info">
          <div class="avatar">
            <img :src="userInfo.avatar" :alt="userInfo.username" />
          </div>
          <div class="user-info">
            <h4>欢迎，{{ userInfo.username }}！</h4>
            <p class="platform-info">通过 {{ platformName }} 登录成功</p>
          </div>
        </div>
        
        <div class="email-section">
          <div class="section-title">
            <i class="el-icon-message"></i>
            <span>邮箱地址（可选）</span>
          </div>
          <p class="email-description">
            我们仅使用您的邮箱发送重要通知，如安全提醒、系统更新等。
            <br>无需验证，您也可以稍后在个人设置中添加。
          </p>
          
          <el-input
            v-model="email"
            placeholder="请输入您的邮箱地址（可选）"
            prefix-icon="el-icon-message"
            :class="{ 'error': emailError }"
            @input="clearError"
            @keyup.enter="handleConfirm"
          />
          <div class="error-message" v-if="emailError">{{ emailError }}</div>
        </div>
      </div>
      
      <div class="modal-footer">
        <el-button @click="handleSkip" class="skip-btn">
          跳过，稍后设置
        </el-button>
        <el-button type="primary" @click="handleConfirm" :loading="submitting">
          {{ email ? '保存并继续' : '直接进入' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'EmailCollectionModal',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    userInfo: {
      type: Object,
      default: () => ({})
    },
    provider: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      email: '',
      emailError: '',
      submitting: false
    }
  },
  computed: {
    platformName() {
      const platformNames = {
        'gitee': 'Gitee',
        'github': 'GitHub',
        'google': 'Google',
        'yandex': 'Yandex',
        'x': 'Twitter',
        'qq': 'QQ',
        'baidu': 'Baidu'
      }
      return platformNames[this.provider] || this.provider
    }
  },
  methods: {
    validateEmail(email) {
      if (!email) return true // 邮箱是可选的
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      return emailRegex.test(email)
    },
    
    clearError() {
      this.emailError = ''
    },
    
    async handleConfirm() {
      // 验证邮箱格式（如果用户输入了邮箱）
      if (this.email && !this.validateEmail(this.email)) {
        this.emailError = '请输入有效的邮箱地址'
        return
      }
      
      this.submitting = true
      
      try {
        // 如果用户输入了邮箱，更新用户信息
        if (this.email) {
          await this.updateUserEmail(this.email)
        }
        
        // 完成登录流程
        this.$emit('complete', {
          email: this.email,
          skipped: !this.email
        })
        
      } catch (error) {
        console.error('更新邮箱失败:', error)
        this.$message.error('保存邮箱失败，但登录成功')
        // 即使更新失败，也继续登录流程
        this.$emit('complete', {
          email: '',
          skipped: true,
          error: error.message
        })
      } finally {
        this.submitting = false
      }
    },
    
    handleSkip() {
      this.$emit('complete', {
        email: '',
        skipped: true
      })
    },
    
    async updateUserEmail(email) {
      // 调用后端API更新用户邮箱
      const response = await this.$http.post(
        this.$constant.baseURL + '/user/updateEmail',
        { email: email },
        false, // 不是管理员请求
        true   // JSON格式
      )
      
      if (response.code !== 200) {
        throw new Error(response.message || '更新邮箱失败')
      }
      
      return response.data
    }
  },
  
  watch: {
    visible(newVal) {
      if (newVal) {
        // 模态框显示时重置状态
        this.email = ''
        this.emailError = ''
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.email-collection-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal-content {
  position: relative;
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 480px;
  max-height: 90vh;
  overflow: hidden;
  animation: modalSlideIn 0.3s ease-out;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 24px;
}

.modal-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.close-btn {
  font-size: 18px;
  color: #999;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  /* 性能优化: 只监听背景色变化，不需要GPU */
  transition: background-color 0.2s ease, color 0.2s ease;
}

.close-btn:hover {
  color: #666;
  background: #f5f5f5;
}

.modal-body {
  padding: 0 24px 24px;
}

.welcome-info {
  display: flex;
  align-items: center;
  margin-bottom: 32px;
  padding: 20px;
  background: linear-gradient(-45deg, #e8d8b9, #eccec5, #a3e9eb, #bdbdf0, #eec1ea);
  background-size: 400% 400%;
  animation: gradientBG 10s ease infinite;
  border-radius: 8px;
  color: white;
}

.avatar {
  margin-right: 16px;
}

.avatar img {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.user-info h4 {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 600;
}

.platform-info {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.email-section {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.section-title i {
  margin-right: 8px;
  color: #409eff;
}

.email-description {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: #666;
  line-height: 1.5;
}

.el-input.error >>> .el-input__inner {
  border-color: #f56c6c;
}

.error-message {
  margin-top: 8px;
  font-size: 12px;
  color: #f56c6c;
}

.modal-footer {
  display: flex;
  justify-content: space-between;
  padding: 20px 24px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}

.skip-btn {
  color: #666;
  border-color: #ddd;
}

.skip-btn:hover {
  color: #409eff;
  border-color: #409eff;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .modal-content {
    width: 95%;
    margin: 20px;
  }
  
  .modal-header,
  .modal-body {
    padding-left: 16px;
    padding-right: 16px;
  }
  
  .modal-footer {
    padding: 16px;
    flex-direction: column;
    gap: 12px;
  }
  
  .welcome-info {
    flex-direction: column;
    text-align: center;
  }
  
  .avatar {
    margin-right: 0;
    margin-bottom: 12px;
  }
}
</style>
