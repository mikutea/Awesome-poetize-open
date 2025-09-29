<template>
    <div>
      <div class="friend-links-container">
        <!-- 封面 -->
        <div class="friend-links-header my-animation-slide-top">
          <!-- 背景图片 -->
          <video class="index-video" autoplay="autoplay" muted="muted" loop="loop"
                 preload="metadata" 
                 :src="$store.state.sysConfig['webStaticResourcePrefix'] + 'assets/backgroundVideo.mp4'">
          </video>
          <div style="position: absolute;left: 0;top: 0;padding: 5px 20px; position: absolute; left: 20px; top: 25px; margin: 10px;">
            <!-- 标题 -->
            <div style="color: var(--white);margin: 0 10px">
              <div style="font-size: 30px;font-weight: bold;line-height: 2">
                友人帐
              </div>
              <div style="font-size: 16px;opacity: 0.8;margin-top: 10px">
                留下你的网站吧，让我们建立友谊的桥梁
              </div>
            </div>
          </div>
        </div>
  
        <!-- 内容 -->
        <div class="friend-links-content">
          <friend></friend>
        </div>
      </div>
  
      <!-- 页脚 -->
      <div style="background: var(--favoriteBg)">
        <myFooter></myFooter>
      </div>
    </div>
  </template>
  
  <script>
    const myFooter = () => import( "./common/myFooter");
    const friend = () => import( "./friend");
  
    export default {
      components: {
        myFooter,
        friend
      },
  
      data() {
        return {}
      },
  
      computed: {},
  
      watch: {},
  
      created() {
        // 设置页面标题
        this.$store.commit("setTitle", "友人帐 | " + this.$store.state.sysConfig.webName);
      },
  
      mounted() {
  
      },
  
      methods: {}
    }
  </script>
  
  <style scoped>
    .friend-links-container {
      padding: 25px;
      background: var(--favoriteBg);
    }
  
    .friend-links-header {
      margin: 60px auto 30px;
      height: 200px;
      position: relative;
      overflow: hidden;
      border-radius: 20px;
      max-width: 1200px;
    }
  
    .index-video {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  
    .friend-links-content {
      margin: 0 auto;
      max-width: 1200px;
    }
  
    @media screen and (max-width: 906px) {
      .friend-links-header {
        height: 180px;
      }
    }
  
    @media screen and (max-width: 636px) {
      .friend-links-header {
        height: 160px;
      }
    }
  </style>