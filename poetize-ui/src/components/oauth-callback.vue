<template>
  <div class="oauth-callback-container">
    <div class="callback-content">
      <div v-if="loading" class="loading-section">
        <i class="el-icon-loading" style="font-size: 48px; color: var(--themeBackground);"></i>
        <p class="loading-text">正在处理授权信息...</p>
      </div>
      
      <div v-else-if="success" class="success-section">
        <i class="el-icon-success" style="font-size: 48px; color: var(--green);"></i>
        <h3>绑定成功！</h3>
        <p>{{ successMessage }}</p>
        <el-button type="primary" @click="goToUserCenter">返回个人中心</el-button>
      </div>
      
      <div v-else class="error-section">
        <i class="el-icon-error" style="font-size: 48px; color: var(--red);"></i>
        <h3>绑定失败</h3>
        <p>{{ errorMessage }}</p>
        <el-button type="primary" @click="goToUserCenter">返回个人中心</el-button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "OAuthCallback",
  data() {
    return {
      loading: true,
      success: false,
      successMessage: "",
      errorMessage: ""
    };
  },
  mounted() {
    this.handleCallback();
  },
  methods: {
    handleCallback() {
      try {
        // 获取URL参数
        const urlParams = new URLSearchParams(window.location.search);
        const code = urlParams.get('code');
        const state = urlParams.get('state');
        const error = urlParams.get('error');
        const success = urlParams.get('success');
        const message = urlParams.get('message');
        const platformType = urlParams.get('platform') || this.$route.query.platform;

        // 检查是否已经处理完成（来自Python后端的直接结果）
        if (success === 'true') {
          this.handleSuccess(message || "绑定成功！现在您可以使用第三方账号登录了");
          return;
        }

        // 检查是否有错误
        if (error) {
          this.handleError(`授权失败: ${error}`);
          return;
        }

        // 检查必要参数（旧的流程，用于向后兼容）
        if (!code || !state) {
          this.handleError("缺少必要的授权参数");
          return;
        }

        if (!platformType) {
          this.handleError("缺少平台类型参数");
          return;
        }

        // 调用后端绑定接口（旧的流程，用于向后兼容）
        this.bindAccount(platformType, code, state);
      } catch (error) {
        console.error("处理OAuth回调失败:", error);
        this.handleError("处理授权信息失败");
      }
    },

    bindAccount(platformType, code, state) {
      const params = new URLSearchParams();
      params.append('platformType', platformType);
      params.append('code', code);
      params.append('state', state);

      this.$http.post(this.$constant.baseURL + "/user/bindThirdPartyAccount", params, {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      })
      .then(res => {
        this.loading = false;
        if (res.code === 200) {
          this.success = true;
          this.successMessage = res.message || "账号绑定成功！现在您可以使用第三方账号登录了";
        } else {
          this.handleError(res.message || "绑定失败，请稍后再试");
        }
      })
      .catch(error => {
        console.error("绑定第三方账号失败:", error);
        this.handleError("绑定失败，请稍后再试");
      });
    },

    handleSuccess(message) {
      this.loading = false;
      this.success = true;
      this.successMessage = message;
    },

    handleError(message) {
      this.loading = false;
      this.success = false;
      this.errorMessage = message;
    },

    goToUserCenter() {
      this.$router.push('/user');
    }
  }
};
</script>

<style scoped>
.oauth-callback-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--background);
}

.callback-content {
  text-align: center;
  padding: 40px;
  background: var(--whiteMask);
  border-radius: 10px;
  box-shadow: 0 4px 20px var(--miniMask);
  backdrop-filter: blur(10px);
  border: 1px solid var(--lightGray);
  max-width: 400px;
  width: 90%;
}

.loading-section,
.success-section,
.error-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.loading-text {
  color: var(--fontColor);
  font-size: 16px;
  margin: 0;
}

.success-section h3 {
  color: var(--green);
  margin: 0;
  font-size: 24px;
}

.error-section h3 {
  color: var(--red);
  margin: 0;
  font-size: 24px;
}

.success-section p,
.error-section p {
  color: var(--fontColor);
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
}

@media screen and (max-width: 480px) {
  .callback-content {
    padding: 30px 20px;
  }
  
  .success-section h3,
  .error-section h3 {
    font-size: 20px;
  }
  
  .loading-text,
  .success-section p,
  .error-section p {
    font-size: 13px;
  }
}
</style>
