<template>
  <div>
    <div class="favorites-container">
      <!-- 封面 -->
      <div class="favorites-header my-animation-slide-top">
        <!-- 背景图片 -->
        <video class="index-video" autoplay="autoplay" muted="muted" loop="loop"
               preload="metadata" 
               :src="$store.state.sysConfig['webStaticResourcePrefix'] + 'assets/backgroundVideo.mp4'">
        </video>
        <div style="position: absolute;left: 0;top: 0;padding: 5px 20px; position: absolute; left: 20px; top: 25px; margin: 10px;">
          <!-- 标题 -->
          <div style="color: var(--white);margin: 0 10px">
            <div style="font-size: 30px;font-weight: bold;line-height: 2">
              收藏夹
            </div>
            <div style="font-size: 16px;opacity: 0.8;margin-top: 10px">
              将本网站添加到您的收藏夹吧，发现更多精彩内容
            </div>
          </div>
        </div>
      </div>

      <!-- 内容 -->
      <div class="favorites-content">
        <!-- 收藏夹 -->
        <div v-if="!$common.isEmpty(collects)" class="my-animation-hideToShow">
          <div v-for="(value, key) in collects" :key="key" style="margin-top: 20px">
            <div class="collect-classify">
              {{key}}
            </div>
            <div class="favorite-item-wrap">
              <div v-for="(item, index) in value" :key="index" @click="toUrl(item.url)" class="favorite-item">
                <div>
                  <el-avatar class="favorite-item-image" :size="60"
                             :src="item.cover">
                  </el-avatar>
                </div>
                <div style="width: calc(100% - 80px)">
                  <div class="favorite-item-title">
                    {{item.title}}
                  </div>
                  <div class="favorite-item-introduction">
                    {{item.introduction}}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 暂无收藏提示 -->
        <div v-else class="empty-state">
          <div class="empty-icon">📂</div>
          <div class="empty-text">暂无收藏内容</div>
          <div class="empty-desc">管理员还没有添加收藏内容哦~</div>
        </div>
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

  export default {
    components: {
      myFooter
    },

    data() {
      return {
        collects: {}
      }
    },

    computed: {},

    watch: {},

    created() {
      // 设置页面标题
      this.$store.commit("setTitle", "收藏夹 | " + this.$store.state.sysConfig.webName);
      this.getCollect();
    },

    mounted() {

    },

    methods: {
      toUrl(url) {
        window.open(url);
      },
      getCollect() {
        this.$http.get(this.$constant.baseURL + "/webInfo/listCollect")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.collects = res.data;
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
  .favorites-container {
    padding: 25px;
    background: var(--favoriteBg);
  }

  .favorites-header {
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

  .favorites-content {
    margin: 0 auto;
    max-width: 1200px;
    padding: 40px 0;
  }

  .collect-classify {
    font-size: 28px;
    font-weight: bold;
    margin-bottom: 10px;
  }

  .favorite-item-wrap {
    display: flex;
    flex-wrap: wrap;
    margin-left: -10px;
  }

  .favorite-item {
    transition: all 0.3s;
    border-radius: 12px;
    box-shadow: 0 8px 16px -4px #2c2d300c;
    background: var(--background);
    display: flex;
    width: calc(100% / 4 - 20px);
    max-width: 320px;
    height: 90px;
    overflow: hidden;
    padding: 15px;
    cursor: pointer;
    margin: 10px;
  }

  .favorite-item:hover {
    background: #425AEF;
    color: var(--white);
  }

  .favorite-item:hover .favorite-item-image {
    transition: all 0.6s;
    width: 0 !important;
    height: 0 !important;
    opacity: 0;
    margin-right: 0;
  }

  .favorite-item:hover div:nth-child(2) {
    width: 100% !important;
  }

  .favorite-item-image {
    margin-right: 20px;
    transition: all 0.3s;
  }

  .favorite-item-title {
    font-size: 19px;
    font-weight: bold;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    margin-bottom: 5px;
  }

  .favorite-item-introduction {
    opacity: 0.7;
    font-weight: bold;
    letter-spacing: 1px;
    font-size: 14px;
    line-height: 1.2;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .empty-state {
    text-align: center;
    padding: 80px 20px;
    color: var(--greyFont);
  }

  .empty-icon {
    font-size: 48px;
    margin-bottom: 20px;
  }

  .empty-text {
    font-size: 20px;
    font-weight: bold;
    margin-bottom: 10px;
  }

  .empty-desc {
    font-size: 14px;
    opacity: 0.7;
  }

  @media screen and (max-width: 906px) {
    .favorites-header {
      height: 180px;
    }
    
    .favorite-item {
      width: calc(100% / 3 - 20px);
    }
  }

  @media screen and (max-width: 636px) {
    .favorites-header {
      height: 160px;
    }
    
    .favorite-item {
      width: calc(100% / 2 - 20px);
    }
  }

  @media screen and (max-width: 400px) {
    .favorite-item {
      width: calc(100% - 20px);
    }
  }
</style>
