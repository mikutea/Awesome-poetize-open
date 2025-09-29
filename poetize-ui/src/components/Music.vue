<template>
  <div>
    <div class="music-container">
      <!-- 封面 -->
      <div class="music-header my-animation-slide-top">
        <!-- 背景图片 -->
        <video class="index-video" autoplay="autoplay" muted="muted" loop="loop"
               preload="metadata" 
               :src="$store.state.sysConfig['webStaticResourcePrefix'] + 'assets/backgroundVideo.mp4'">
        </video>
        <div style="position: absolute;left: 0;top: 0;padding: 5px 20px; position: absolute; left: 20px; top: 25px; margin: 10px;">
          <!-- 标题 -->
          <div style="color: var(--white);margin: 0 10px">
            <div style="font-size: 30px;font-weight: bold;line-height: 2">
              曲乐
            </div>
            <div style="font-size: 16px;opacity: 0.8;margin-top: 10px">
              一曲肝肠断，天涯何处觅知音
            </div>
          </div>
        </div>
      </div>

      <!-- 内容 -->
      <div class="music-content">
        <funny></funny>
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
  const funny = () => import( "./funny");

  export default {
    components: {
      myFooter,
      funny
    },

    data() {
      return {}
    },

    computed: {},

    watch: {},

    created() {
      // 设置页面标题
      this.$store.commit("setTitle", "曲乐 | " + this.$store.state.sysConfig.webName);
    },

    mounted() {

    },

    methods: {}
  }
</script>

<style scoped>
  .music-container {
    padding: 25px;
    background: var(--favoriteBg);
  }

  .music-header {
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

  .music-content {
    margin: 0 auto;
    max-width: 1200px;
  }

  @media screen and (max-width: 906px) {
    .music-header {
      height: 180px;
    }
  }

  @media screen and (max-width: 636px) {
    .music-header {
      height: 160px;
    }
  }
</style>
