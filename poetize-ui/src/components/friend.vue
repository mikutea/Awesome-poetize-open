<template>
  <div>
    <div class="friend-wrap">
      <div class="friend-main">
        <h2 style="font-size: 24px;">🌟青出于蓝</h2>
        <card :resourcePathList="eliteFriends" @clickResourcePath="clickFriend"></card>

        <hr>

        <h2 style="font-size: 24px; margin-top: 60px">🥇友情链接</h2>
        <card :resourcePathList="regularFriends" @clickResourcePath="clickFriend"></card>

        <hr>

        <div style="font-size: 24px;font-weight: bold;margin-top: 60px">✉️ 申请方式</div>
        <div class="application-guide">
          <div class="guide-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <p class="step-title">添加本站链接</p>
              <p class="step-desc">首先将本站链接添加至您的网站，信息如下：</p>
            </div>
          </div>
          
          <div class="site-info-box">
            <div class="info-item">
              <span class="info-label">网站名称</span>
              <span class="info-value">{{siteInfo.title || $constant.friendWebName}}</span>
            </div>
            <div class="info-item">
              <span class="info-label">网站地址</span>
              <span class="info-value">{{siteInfo.url || $constant.friendUrl}}</span>
            </div>
            <div class="info-item">
              <span class="info-label">网站描述</span>
              <span class="info-value">{{siteInfo.introduction || $constant.friendIntroduction}}</span>
            </div>
            <div class="info-item">
              <span class="info-label">网站封面</span>
              <span class="info-value">{{siteInfo.cover || $constant.friendCover}}</span>
            </div>
          </div>

          <div class="guide-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <p class="step-title">提交申请</p>
              <p class="step-desc">点击下方信封 📮 填写您的网站信息提交申请</p>
            </div>
          </div>

          <div class="guide-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <p class="step-title">等待审核</p>
              <p class="step-desc">审核通过后将会添加至该页面中，请耐心等待</p>
            </div>
          </div>
        </div>
        
        <div style="font-size: 24px;font-weight: bold;margin-top: 30px">⚠️ 温馨提示</div>
        <div class="notice-box">
          <ul class="notice-list">
            <li>不会添加带有广告营销和没有实质性内容的友链</li>
            <li>申请之前请将本网站添加为您的友链</li>
            <li>审核时间一般在一周内，请耐心等待</li>
          </ul>
        </div>

        <!-- 添加友链 -->
        <div @click="clickLetter()" class="form-wrap">
          <!-- 信封上面 -->
          <img class="before-img" :src="mainStore.sysConfig['webStaticResourcePrefix'] + 'assets/friendLetterTop.png'" style="width: 100%"/>
          <!-- 信 -->
          <div class="envelope" style="animation: hideToShow 2s">
            <div class="form-main">
              <div>
                <h3 style="text-align: center">有朋自远方来</h3>
                <div>
                  <div class="myCenter form-friend">
                    <div class="user-title">
                      <div>名称：</div>
                      <div>简介：</div>
                      <div>封面：</div>
                      <div>网址：</div>
                    </div>
                    <div class="user-content">
                      <div>
                        <el-input maxlength="30" v-model="friend.title"></el-input>
                      </div>
                      <div>
                        <el-input maxlength="120" v-model="friend.introduction"></el-input>
                      </div>
                      <div>
                        <el-input maxlength="200" v-model="friend.cover"></el-input>
                      </div>
                      <div>
                        <el-input maxlength="200" v-model="friend.url"></el-input>
                      </div>
                    </div>
                  </div>
                  <div class="myCenter" style="margin-top: 20px">
                    <proButton :info="'提交'"
                               @click.native.stop="submitFriend()"
                               :before="$constant.before_color_2"
                               :after="$constant.after_color_2">
                    </proButton>
                  </div>
                </div>
                <div>
                  <img :src="mainStore.sysConfig['webStaticResourcePrefix'] + 'assets/friendLetterBiLi.png'" style="width: 100%;margin: 5px auto"/>
                </div>
                <p style="font-size: 12px;text-align: center;color: #999">欢迎交换友链</p>
              </div>
            </div>
          </div>
          <img class="after-img" :src="mainStore.sysConfig['webStaticResourcePrefix'] + 'assets/friendLetterBottom.png'" style="width: 100%"/>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
    import { useMainStore } from '@/stores/main';

const card = () => import( "./common/card");
  const proButton = () => import( "./common/proButton");

  export default {
    components: {
      card,
      proButton
    },

    data() {
      return {
        friendList: {},
        siteInfo: {},
        friend: {
          title: "",
          introduction: "",
          cover: "",
          url: ""
        }
      }
    },

    computed: {
      mainStore() {
        return useMainStore();
      },
      // 兼容旧的emoji key，统一使用新emoji访问
      eliteFriends() {
        // 优先使用新key，如果没有则尝试旧key
        return this.friendList['🌟青出于蓝'] || this.friendList['♥️青出于蓝'] || [];
      },
      regularFriends() {
        return this.friendList['🥇友情链接'] || [];
      }
    },

    watch: {},

    created() {
      this.getFriends();
      this.getSiteInfo();
    },

    mounted() {

    },

    methods: {
      clickLetter() {
        const formWrapElements = document.querySelectorAll('.form-wrap');
        formWrapElements.forEach(element => {
          // 信封展开后高度增加，但不向上偏移（top保持为0）
          if (document.body.clientWidth < 700) {
            element.style.height = '650px';
          } else {
            element.style.height = '700px';
          }
          
          // 让信封内容向上移动
          const envelope = element.querySelector('.envelope');
          if (envelope) {
            envelope.style.transform = 'translateY(-150px)';
          }
        });
      },
      submitFriend() {
        if (this.$common.isEmpty(this.mainStore.currentUser)) {
          // 使用统一的登录跳转函数
          this.$common.redirectToLogin(this.$router, {
            message: '请先登录！'
          }, this);
          return;
        }

        if (this.friend.title.trim() === "") {
          this.$message({
            message: "你还没写名称呢~",
            type: "warning"
          });
          return;
        }

        if (this.friend.introduction.trim() === "") {
          this.$message({
            message: "你还没写简介呢~",
            type: "warning"
          });
          return;
        }

        if (this.friend.cover.trim() === "") {
          this.$message({
            message: "你还没设置封面呢~",
            type: "warning"
          });
          return;
        }

        if (this.friend.url.trim() === "") {
          this.$message({
            message: "你还没写网址呢~",
            type: "warning"
          });
          return;
        }

        this.$http.post(this.$constant.baseURL + "/webInfo/saveFriend", this.friend)
          .then((res) => {
            const formWrapElements = document.querySelectorAll('.form-wrap');
            formWrapElements.forEach(element => {
              element.style.height = '447px';
              element.style.top = '0';
            });
            this.$message({
              type: 'success',
              message: '提交成功，待管理员审核！'
            });
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      clickFriend(path) {
        window.open(path);
      },
      getFriends() {
        this.$http.get(this.$constant.baseURL + "/webInfo/listFriend")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.friendList = res.data;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      getSiteInfo() {
        this.$http.get(this.$constant.baseURL + "/webInfo/getSiteInfo")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.siteInfo = res.data;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      }
    }
  }
</script>

<style scoped>

  .friend-main {
    max-width: 1200px;
    margin: 40px auto;
    border-radius: 10px;
    padding: 40px;
    background: rgba(255, 255, 255, .85);
  }

  .friend-main h2 {
    font-size: 20px;
  }

  hr {
    position: relative;
    margin: 40px auto;
    border: 2px dashed var(--lightGreen);
    overflow: visible;
  }

  hr:before {
    position: absolute;
    top: -14px;
    left: 5%;
    color: var(--lightGreen);
    content: '❄';
    font-size: 30px;
    line-height: 1;
    /* 性能优化: 有缩放动画，需要GPU加速 */
    transition: transform 1s ease-in-out, opacity 1s ease-in-out;
    will-change: transform;
    transform: translateZ(0);
  }

  hr:hover:before {
    left: calc(95% - 20px);
  }

  .form-wrap {
    margin: 0 auto;
    overflow: hidden;
    width: 530px;
    height: 447px;
    position: relative;
    top: 0;
    /* 性能优化: 高度变化动画，需要GPU加速 */
    transition: height 1s ease-in-out;
    z-index: 0;
    cursor: pointer;
    will-change: height;
    transform: translateZ(0);
  }


  .before-img {
    position: absolute;
    bottom: 126px;
    left: 0;
    background-repeat: no-repeat;
    width: 530px;
    height: 317px;
    z-index: -100;
  }

  .after-img {
    position: absolute;
    bottom: -2px;
    left: 0;
    background-repeat: no-repeat;
    width: 530px;
    height: 259px;
    z-index: 100;
  }

  .friend-wrap {
    color: var(--black);
  }

  .envelope {
    position: relative;
    margin: 0 auto;
    /* 性能优化: 有复杂动画，需要GPU加速 */
    transition: transform 1s ease-in-out;
    padding: 200px 20px 20px;
    will-change: transform;
    transform: translateY(0) translateZ(0);
  }

  .form-main {
    background: var(--white);
    margin: 0 auto;
    border-radius: 10px;
    overflow: hidden;
    /* 添加阴影，让表单和背景区分开 */
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.05);
  }


  .user-title {
    text-align: right;
    user-select: none;
  }

  .user-content {
    text-align: left;
  }

  .user-title div {
    height: 55px;
    line-height: 55px;
    text-align: center;
  }

  .user-content > div {
    height: 55px;
    display: flex;
    align-items: center;
  }

  .user-content >>> .el-input__inner {
    border: none;
    height: 35px;
    background: var(--whiteMask);
  }

  .form-friend {
    margin-top: 12px;
    background-color: #eee;
    border: #ddd 1px solid;
    padding: 20px 0;
  }

  /* 章节标题 */
  .section-title {
    font-size: 22px;
    font-weight: 600;
    margin: 50px 0 24px 0;
    color: var(--black);
    padding-left: 12px;
    border-left: 4px solid var(--lightGreen);
  }
  
  /* 申请指南容器 */
  .application-guide {
    margin-bottom: 50px;
  }
  
  /* 申请步骤 */
  .guide-step {
    display: flex;
    align-items: flex-start;
    margin: 24px 0;
  }
  
  .step-number {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    background: var(--lightGreen);
    color: white;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    font-weight: bold;
    margin-right: 16px;
  }
  
  .step-content {
    flex: 1;
  }
  
  .step-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--black);
    margin: 0 0 6px 0;
  }
  
  .step-desc {
    font-size: 14px;
    color: #666;
    margin: 0;
    line-height: 1.6;
  }
  
  /* 网站信息卡片 */
  .site-info-box {
    background: #f7f9fc;
    border: 1px solid #e1e8ed;
    border-radius: 8px;
    padding: 20px;
    margin: 20px 0 20px 52px;
  }
  
  .info-item {
    display: flex;
    padding: 12px 0;
    border-bottom: 1px dashed #e1e8ed;
  }
  
  .info-item:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
  
  .info-item:first-child {
    padding-top: 0;
  }
  
  .info-label {
    flex-shrink: 0;
    width: 90px;
    font-size: 14px;
    color: #666;
    font-weight: 500;
  }
  
  .info-value {
    flex: 1;
    font-size: 14px;
    color: #333;
    word-break: break-all;
  }
  
  /* 温馨提示卡片 */
  .notice-box {
    background: #fffbf0;
    border: 1px solid #ffe7a0;
    border-radius: 8px;
    padding: 20px 24px;
    margin-bottom: 40px;
  }
  
  .notice-header {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
  }
  
  .notice-icon {
    font-size: 22px;
    margin-right: 8px;
  }
  
  .notice-title {
    font-size: 16px;
    font-weight: 600;
    color: #d97706;
  }
  
  .notice-list {
    margin: 0;
    padding-left: 20px;
    list-style: none;
  }
  
  .notice-list li {
    position: relative;
    padding-left: 16px;
    margin: 10px 0;
    font-size: 14px;
    color: #666;
    line-height: 1.6;
  }
  
  .notice-list li:before {
    content: '•';
    position: absolute;
    left: 0;
    color: #f59e0b;
    font-weight: bold;
  }

  @media screen and (max-width: 700px) {
    .form-wrap {
      width: auto;
    }

    .before-img {
      width: auto;
    }

    .after-img {
      width: auto;
    }
    
    .site-info-box {
      margin-left: 0;
    }
    
    .info-item {
      flex-direction: column;
    }
    
    .info-label {
      margin-bottom: 4px;
      color: #999;
    }
  }

  @media screen and (max-width: 500px) {
    .friend-main {
      padding: 40px 15px;
    }
    
    .section-title {
      font-size: 20px;
      margin-top: 40px;
    }
    
    .step-number {
      width: 32px;
      height: 32px;
      font-size: 16px;
      margin-right: 12px;
    }
    
    .step-title {
      font-size: 15px;
    }
    
    .step-desc {
      font-size: 13px;
    }
    
    .site-info-box {
      padding: 16px;
    }
    
    .notice-box {
      padding: 16px 20px;
    }
  }
</style>
