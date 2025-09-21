<template>
  <div>
    <!-- el过渡动画 -->
    <transition name="el-fade-in-linear">
      <!-- 导航栏 -->
      <div v-show="toolbar.visible || ($common.mobile() || mobile)"
           @mouseenter="hoverEnter = true"
           @mouseleave="hoverEnter = false"
           :class="[{ enter: toolbar.enter }, { hoverEnter: (hoverEnter || this.$route.path === '/favorite' || this.$route.path === '/travel' || this.$route.path === '/privacy') && !toolbar.enter }]"
           class="toolbar-content myBetween">
        <!-- 网站名称 -->
        <div class="toolbar-title">
          <h2 @click="$router.push({path: '/'})">{{$store.state.webInfo.webName}}</h2>
        </div>

        <!-- 手机导航按钮 -->
        <div v-if="$common.mobile() || mobile"
             class="toolbar-mobile-menu"
             @click="toolbarDrawer = !toolbarDrawer"
             :class="{ enter: toolbar.enter }">
          <i class="el-icon-s-operation"></i>
        </div>

        <!-- 导航列表 -->
        <div v-else>
          <ul class="scroll-menu">
            <!-- 遍历导航项并按配置顺序显示 -->
            <template v-for="(item, index) in orderedNavItems">
              <!-- 首页 -->
              <li v-if="item.name === '首页'" :key="'nav-'+index" @click="$router.push({path: '/'})">
                <div class="my-menu">
                  🏡 <span>首页</span>
                </div>
              </li>

              <!-- 记录 -->
              <el-dropdown v-if="item.name === '记录'" :key="'nav-'+index" :hide-timeout="500" placement="bottom">
                <li>
                  <div class="my-menu">
                    📒 <span>记录</span>
                  </div>
                </li>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item v-for="(sort, sortIndex) in sortInfo" :key="sortIndex">
                    <div @click="$router.push({path: '/sort', query: {sortId: sort.id}})">
                      {{sort.sortName}}
                    </div>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>

              <!-- 家 -->
              <li v-if="item.name === '家'" :key="'nav-'+index" @click="$router.push({path: '/love'})">
                <div class="my-menu">
                  ❤️‍🔥 <span>家</span>
                </div>
              </li>

              <!-- 百宝箱 -->
              <li v-if="item.name === '百宝箱'" :key="'nav-'+index" @click="$router.push({path: '/favorite'})">
                <div class="my-menu">
                  🧰 <span>百宝箱</span>
                </div>
              </li>

              <!-- 留言 -->
              <li v-if="item.name === '留言'" :key="'nav-'+index" @click="$router.push({path: '/message'})">
                <div class="my-menu">
                  📪 <span>留言</span>
                </div>
              </li>

              <!-- 联系我 -->
              <li v-if="item.name === '联系我'" :key="'nav-'+index" @click="goIm()">
                <div class="my-menu">
                  💬 <span>联系我</span>
                </div>
              </li>
            </template>

            <!-- 后台 -->
            <li @click="goAdmin()" v-if="!$common.isEmpty($store.state.currentUser) && ($store.state.currentUser.userType === 0 || $store.state.currentUser.userType === 1)">
              <div class="my-menu">
                💻️ <span>后台</span>
              </div>
            </li>

            <!-- 登录/个人中心 -->
            <li>
              <!-- 未登录时显示粉色圆形登录按钮 -->
              <div v-if="$common.isEmpty($store.state.currentUser)" 
                   class="circle-login-button"
                   @click="$router.push({path: '/user'})">
                登录
              </div>
              
              <!-- 已登录时显示头像下拉菜单 -->
              <el-dropdown placement="bottom" v-else>
                <el-avatar class="user-avatar" :size="36"
                          style="margin-top: 12px"
                          :src="$store.state.currentUser.avatar">
                </el-avatar>

                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item @click.native="$router.push({path: '/user'})">
                    <i class="fa fa-user-circle" aria-hidden="true"></i> <span>个人中心</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click.native="logout()">
                    <i class="fa fa-sign-out" aria-hidden="true"></i> <span>退出</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </li>
          </ul>
        </div>
      </div>
    </transition>

    <div id="main-container" :style="mainContainerStyle">
      <router-view></router-view>
    </div>

    <!-- 回到顶部按钮 -->
<!--    <div href="#" class="cd-top" v-if="!$common.mobile()" @click="toTop()"></div>-->

    <div class="toolButton">
      <!-- 简化语言切换按钮 - 只在文章页面且屏幕≤1050px时显示 -->
      <div class="simple-lang-switch"
           v-if="showSimpleLangSwitch"
           @click="handleSimpleLangSwitch()"
           :title="getSimpleLangSwitchTitle()">
        <span class="simple-lang-text">{{ getSimpleLangDisplay() }}</span>
      </div>

      <!-- 目录按钮 - 只在文章页面显示 -->
      <div class="toc-button-container" v-if="showTocButton" @click="clickTocButton()">
        <i class="fa fa-align-justify toc-button-icon" aria-hidden="true"></i>
      </div>

      <div class="backTop" v-if="toolButton" @click="toTop()">
        <!-- 回到顶部按钮 -->
        <svg viewBox="0 0 1024 1024" width="50" height="50">
          <path
            d="M696.741825 447.714002c2.717387-214.485615-173.757803-312.227566-187.33574-320.371729-10.857551 5.430775-190.050127 103.168727-187.33274 320.371729-35.297037 24.435488-73.306463 65.1623-67.875688 135.752376 5.430775 70.589076 76.018851 119.460051 103.168726 116.745664 27.152875-2.716387 19.004713-21.7221 19.004713-21.7221l8.148162-38.011425s40.721814 59.732525 51.583363 59.732525h146.609927c13.574938 0 51.585363-59.732525 51.585363-59.732525l8.147162 38.011425s-8.147162 19.005713 19.004713 21.7221c27.148876 2.714388 97.738951-46.156588 103.168727-116.745664s-32.57965-111.316888-67.876688-135.752376z m-187.33574-2.713388c-5.426776 0-70.589076-2.717387-78.733239-78.737238 2.713388-73.306463 73.306463-78.733239 78.733239-81.450626 5.430775 0 76.02385 8.144163 78.736238 81.450626-8.143163 76.019851-73.305463 78.737238-78.736238 78.737238z m0 0"
            fill="#000000"></path>
          <path
            d="M423.602441 746.060699c6.47054-6.297579 12.823107-7.017417 21.629121-2.784372 34.520213 16.582259 70.232157 19.645568 107.031855 9.116944 8.118169-2.323476 15.974396-5.475765 23.598677-9.22392 13.712907-6.73648 26.003134 0.8878 26.080116 16.13936 0.109975 22.574907-0.024994 45.142816 0.080982 67.709725 0.031993 7.464316-2.277486 13.322995-9.44387 16.608254-7.277358 3.333248-13.765895 1.961558-19.526595-3.264264-3.653176-3.313253-7.063407-6.897444-10.634601-10.304675-6.563519-6.259588-6.676494-6.25259-10.625603 1.603638-8.437097 16.80121-16.821205 33.623415-25.257302 50.423625-2.489438 4.953882-5.706713 9.196925-11.411426 10.775569-8.355115 2.315478-15.772442-1.070758-20.272427-9.867774-8.774021-17.15313-17.269104-34.453228-25.918153-51.669344-3.750154-7.469315-3.9891-7.479313-10.141712-1.514658-3.715162 3.602187-7.31435 7.326347-11.142486 10.800563-5.571743 5.060858-11.934308 6.269586-18.936728 3.207277-6.82746-2.984327-9.869774-8.483086-9.892769-15.685462-0.070984-23.506697-0.041991-47.018393-0.020995-70.532089 0.007998-4.679944 1.46467-8.785018 4.803916-11.538397z"
            fill="#000000"></path>
        </svg>
      </div>

      <el-popover placement="left"
                  :close-delay="500"
                  trigger="hover">
        <div slot="reference">
          <i class="fa fa-cog iconRotate" style="color: var(--black)" aria-hidden="true"></i>
        </div>
        <div class="my-setting">
          <div>
            <!-- 太阳按钮 -->
            <i v-if="isDark" class="el-icon-sunny iconRotate" @click="changeColor()"></i>
            <!-- 月亮按钮 -->
            <i v-else class="fa fa-moon-o" aria-hidden="true" @click="changeColor()"></i>
          </div>
          <div>
            <i class="fa fa-snowflake-o" aria-hidden="true" @click="changeMouseAnimation()"></i>
          </div>
        </div>
      </el-popover>
    </div>

    <!-- 点击动画 -->
    <canvas v-if="mouseAnimation" id="mousedown"
            style="position:fixed;left:0;top:0;pointer-events:none;z-index: 1000">
    </canvas>

    <!-- 图片预览 -->
    <div id="outerImg">
      <div id="innerImg" style="position:absolute">
        <img id="bigImg" src=""/>
      </div>
    </div>

    <el-drawer :visible.sync="toolbarDrawer"
               :show-close="false"
               size="65%"
               custom-class="toolbarDrawer"
               title="欢迎光临"
               direction="ltr">
      <div>
        <ul class="small-menu">
          <!-- 遍历导航项并按配置顺序显示 -->
          <template v-for="(item, index) in orderedNavItems">
            <!-- 首页 -->
            <li v-if="item.name === '首页'" :key="'mobile-nav-'+index" @click="smallMenu({path: '/'})">
              <div>
                🏡 <span>首页</span>
              </div>
            </li>

            <!-- 记录 -->
            <li v-if="item.name === '记录'" :key="'mobile-nav-'+index">
              <div>
                📒 <span>记录</span>
              </div>
              <div>
                <div v-for="(menu, menuIndex) in sortInfo"
                     :key="menuIndex"
                     class="sortMenu"
                     @click="smallMenu({path: '/sort', query: {sortId: menu.id}})">
                  {{menu.sortName}}
                </div>
              </div>
            </li>

            <!-- 家 -->
            <li v-if="item.name === '家'" :key="'mobile-nav-'+index" @click="smallMenu({path: '/love'})">
              <div>
                ❤️‍🔥 <span>家</span>
              </div>
            </li>

            <!-- 百宝箱 -->
            <li v-if="item.name === '百宝箱'" :key="'mobile-nav-'+index" @click="smallMenu({path: '/favorite'})">
              <div>
                🧰 <span>百宝箱</span>
              </div>
            </li>

            <!-- 留言 -->
            <li v-if="item.name === '留言'" :key="'mobile-nav-'+index" @click="smallMenu({path: '/message'})">
              <div>
                📪 <span>留言</span>
              </div>
            </li>

            <!-- 联系我 -->
            <li v-if="item.name === '联系我'" :key="'mobile-nav-'+index" @click="goIm()">
              <div>
                💬 <span>联系我</span>
              </div>
            </li>
          </template>

          <!-- 后台 -->
          <li @click="goAdmin()" v-if="!$common.isEmpty($store.state.currentUser) && ($store.state.currentUser.userType === 0 || $store.state.currentUser.userType === 1)">
            <div>
              💻️ <span>后台</span>
            </div>
          </li>

          <!-- 登录/个人中心 -->
          <li v-if="$common.isEmpty($store.state.currentUser)" @click="smallMenu({path: '/user'})">
            <div>
              <i class="fa fa-sign-in" aria-hidden="true"></i> <span>登录</span>
            </div>
          </li>

          <li v-if="!$common.isEmpty($store.state.currentUser)" @click="smallMenu({path: '/user'})">
            <div>
              <i class="fa fa-user-circle" aria-hidden="true"></i> <span>个人中心</span>
            </div>
          </li>

          <li v-if="!$common.isEmpty($store.state.currentUser)" @click="smallMenuLogout">
            <div>
              <i class="fa fa-sign-out" aria-hidden="true"></i> <span>退出</span>
            </div>
          </li>
        </ul>
      </div>
    </el-drawer>
  </div>
</template>

<script>
  import mousedown from '../utils/mousedown';

  export default {
    data() {
      return {
        toolButton: false,
        showTocButton: false, // 控制目录按钮显示
        showSimpleLangSwitch: false, // 控制简化语言切换按钮显示
        hoverEnter: false,
        mouseAnimation: false,
        isDark: false,
        scrollTop: 0,
        toolbarDrawer: false,
        mobile: false,
        visitCountInterval: null
      }
    },
    mounted() {
      if (this.mouseAnimation) {
        mousedown();
      }
      window.addEventListener("scroll", this.onScrollPage);
      
      // 优先从localStorage恢复用户保存的主题（支持过期机制）
      try {
        const savedData = localStorage.getItem('poetize-theme');
        if (savedData) {
          // 尝试解析新格式（带时间戳）
          try {
            const themeData = JSON.parse(savedData);
            
            // 检查是否是新格式
            if (themeData && themeData.timestamp && themeData.theme) {
              const now = Date.now();
              const elapsed = now - themeData.timestamp;
              
              // 检查是否过期（1天 = 24 * 60 * 60 * 1000 毫秒）
              if (elapsed > themeData.expiry) {
                console.log('主题设置已过期，清除并使用默认逻辑');
                localStorage.removeItem('poetize-theme');
                // 使用默认的白天夜晚逻辑
                if (this.isDaylight()) {
                  this.isDark = true;
                  this.applyDarkTheme();
                }
              } else {
                console.log(`恢复保存的主题: ${themeData.theme}，剩余有效时间: ${Math.round((themeData.expiry - elapsed) / (60 * 60 * 1000))}小时`);
                if (themeData.theme === 'dark') {
                  this.isDark = true;
                  this.applyDarkTheme();
                } else {
                  this.isDark = false;
                  this.applyLightTheme();
                }
                console.log('主题已从localStorage恢复');
              }
            } else {
              // 旧格式，直接使用并升级为新格式
              console.log('检测到旧格式主题数据，将升级为新格式');
              if (savedData === 'dark') {
                this.isDark = true;
                this.applyDarkTheme();
                // 升级为新格式
                const themeData = {
                  theme: 'dark',
                  timestamp: Date.now(),
                  expiry: 24 * 60 * 60 * 1000
                };
                localStorage.setItem('poetize-theme', JSON.stringify(themeData));
              } else {
                this.isDark = false;
                this.applyLightTheme();
                // 升级为新格式
                const themeData = {
                  theme: 'light',
                  timestamp: Date.now(),
                  expiry: 24 * 60 * 60 * 1000
                };
                localStorage.setItem('poetize-theme', JSON.stringify(themeData));
              }
              console.log('主题已从localStorage恢复并升级为新格式');
            }
          } catch (parseError) {
            console.error('解析主题数据失败:', parseError);
            localStorage.removeItem('poetize-theme');
            // 使用默认逻辑
            if (this.isDaylight()) {
              this.isDark = true;
              this.applyDarkTheme();
            }
          }
        } else {
          console.log('未找到保存的主题，使用默认逻辑');
          // 如果没有保存的主题，则使用原来的白天夜晚逻辑
          if (this.isDaylight()) {
            this.isDark = true;
            this.applyDarkTheme();
          }
        }
      } catch (error) {
        console.error('恢复主题时出错:', error);
        // 出错时使用原来的逻辑
        if (this.isDaylight()) {
          this.isDark = true;
          this.applyDarkTheme();
        }
      }

      // 灰色模式
      if (this.$store.state.webInfo && this.$store.state.webInfo.enableGrayMode) {
        this.applyGrayMask();
      }

      // 初始化目录按钮显示状态
      this.updateTocButtonVisibility();

      // 初始化简化语言切换按钮显示状态
      this.updateSimpleLangSwitchVisibility();
    },
    destroyed() {
      window.removeEventListener("scroll", this.onScrollPage);
      window.removeEventListener("resize", this.getWindowWidth);
      
      // 清除访问量刷新定时器
      if (this.visitCountInterval) {
        clearInterval(this.visitCountInterval);
        this.visitCountInterval = null;
      }
    },
    watch: {
      // 监听路由变化，控制目录按钮显示
      '$route'(to, from) {
        this.updateTocButtonVisibility();
        this.updateSimpleLangSwitchVisibility();
      },

      scrollTop(scrollTop, oldScrollTop) {
        //如果滑动距离超过实际背景高度的一半视为进入页面，背景改为白色
        const backgroundHeight = this.getActualBackgroundHeight();
        let enter = scrollTop > backgroundHeight / 2;
        const top = scrollTop - oldScrollTop < 0;
        let isShow = scrollTop - backgroundHeight > 30;
        this.toolButton = isShow;
        if (isShow && !this.$common.mobile()) {
          const cdTopElements = document.querySelectorAll('.cd-top');
          cdTopElements.forEach(element => {
            if (window.innerHeight > 950) {
              element.style.top = '0';
            } else {
              element.style.top = (window.innerHeight - 950) + 'px';
            }
          });
        } else if (!isShow && !this.$common.mobile()) {
          const cdTopElements = document.querySelectorAll('.cd-top');
          cdTopElements.forEach(element => {
            element.style.top = '-900px';
          });
        }

        //导航栏显示与颜色
        let toolbarStatus = {
          enter: enter,
          visible: top,
        };
        this.$store.commit("changeToolbarStatus", toolbarStatus);
      },
    },
    created() {
      // 获取网站信息
      this.getWebInfo();
      this.getSysConfig();
      this.getSortInfo();

      window.addEventListener("scroll", this.onScrollPage, true);
      window.addEventListener("resize", this.getWindowWidth, true);

      this.getWindowWidth();

      this.mobile = document.body.clientWidth < 1100;

      window.addEventListener('resize', () => {
        let docWidth = document.body.clientWidth;
        if (docWidth < 810) {
          this.mobile = true;
        } else {
          this.mobile = false;
        }
      });
    },
    computed: {
      toolbar() {
        return this.$store.state.toolbar;
      },
      sortInfo() {
        return this.$store.state.sortInfo;
      },
      mainContainerStyle() {
        const height = this.$store.state.webInfo.homePagePullUpHeight;
        if (typeof height !== 'number' || height < 0 || height > 100) {
          return {};
        }

        const marginTop = 3.5 * height;

        return {
          marginTop: `${marginTop}px`
        };
      },
      orderedNavItems() {
        try {
          if (this.$store.state.webInfo && this.$store.state.webInfo.navConfig) {
            const navConfig = this.$store.state.webInfo.navConfig;
            // 处理空JSON对象或空字符串的情况
            if (navConfig === "{}" || navConfig === "" || navConfig === "[]") {
              console.log("导航配置为空，使用默认导航项");
              return this.defaultNavItems;
            }
            
            // 正常解析导航配置
            return JSON.parse(navConfig);
          }
        } catch (e) {
          console.error("解析导航配置失败:", e);
        }
        
        // 如果出错或没有配置，返回默认导航项
        return this.defaultNavItems;
      },
      defaultNavItems() {
        // 默认导航顺序
        return [
          { name: "首页", icon: "🏡", link: "/", type: "internal", order: 1, enabled: true },
          { name: "记录", icon: "📒", link: "#", type: "dropdown", order: 2, enabled: true },
          { name: "家", icon: "❤️‍🔥", link: "/love", type: "internal", order: 3, enabled: true },
          { name: "百宝箱", icon: "🧰", link: "/favorite", type: "internal", order: 4, enabled: true },
          { name: "留言", icon: "📪", link: "/message", type: "internal", order: 5, enabled: true },
          { name: "联系我", icon: "💬", link: "#chat", type: "special", order: 6, enabled: true }
        ];
      }
    },
    methods: {
      smallMenu(data) {
        this.$router.push(data);
        this.toolbarDrawer = false;
      },

      smallMenuLogout() {
        this.logout();
        this.toolbarDrawer = false;
      },

      async goIm() {
        if (this.$common.isEmpty(this.$store.state.currentUser)) {
          this.$message({
            message: "请先登录！",
            type: "error"
          });
          return;
        }

        try {
          // 获取WebSocket临时token
          const response = await this.$http.get(this.$constant.baseURL + "/im/getWsToken", {}, true);
          
          if (response.code === 200 && response.data) {
            const wsToken = response.data;
            // 使用临时token打开聊天室
            window.open(this.$constant.imBaseURL + "?token=" + wsToken + "&defaultStoreType=" + (this.$store.state.sysConfig['store.type'] || 'local'));
          } else {
            this.$message({
              message: response.message || "获取聊天室访问凭证失败",
              type: "error"
            });
          }
        } catch (error) {
          console.error("获取WebSocket token失败:", error);
          this.$message({
            message: "进入聊天室失败，请稍后重试",
            type: "error"
          });
        }
      },

      goAdmin() {
        window.open(this.$constant.webURL + "/admin");
      },

      logout() {
        this.$http.get(this.$constant.baseURL + "/user/logout")
          .then((res) => {
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
        this.$store.commit("loadCurrentUser", {});
        localStorage.removeItem("userToken");
        this.$router.push({path: '/'});
      },
      getWebInfo() {
        this.$http.get(this.$constant.baseURL + "/webInfo/getWebInfo")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              // 保存原始的webTitle字符串用于设置页面标题
              const originalWebTitle = res.data.webTitle;
              
              // 处理网站信息
              this.$store.commit("loadWebInfo", res.data);
              
              // 更新浏览器标签栏标题 - 使用原始的webTitle字符串
              if (originalWebTitle) {
                document.title = originalWebTitle;
                // 同时更新title.js中保存的原始标题
                window.OriginTitile = originalWebTitle;
              }
              
              // 获取完 webInfo 后再执行一次自动夜间判断
              this.maybeApplyAutoNight();
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      
      // 已移除定时刷新访问量的逻辑
      getSysConfig() {
        this.$http.get(this.$constant.baseURL + "/sysConfig/listSysConfig")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.$store.commit("loadSysConfig", res.data);
              this.buildCssPicture();
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      buildCssPicture() {
        let root = document.querySelector(":root");
        let webStaticResourcePrefix = this.$store.state.sysConfig['webStaticResourcePrefix'];
        root.style.setProperty("--commentURL", "url(" + webStaticResourcePrefix + "assets/commentURL.png)");
        root.style.setProperty("--springBg", "url(" + webStaticResourcePrefix + "assets/springBg.png)");
        root.style.setProperty("--admireImage", "url(" + webStaticResourcePrefix + "assets/admireImage.jpg)");
        root.style.setProperty("--toTop", "url(" + webStaticResourcePrefix + "assets/toTop.png)");
        root.style.setProperty("--bannerWave1", "url(" + webStaticResourcePrefix + "assets/bannerWave1.png) repeat-x");
        root.style.setProperty("--bannerWave2", "url(" + webStaticResourcePrefix + "assets/bannerWave2.png) repeat-x");
        root.style.setProperty("--backgroundPicture", "url(" + webStaticResourcePrefix + "assets/backgroundPicture.jpg)");
        root.style.setProperty("--toolbar", "url(" + webStaticResourcePrefix + "assets/toolbar.jpg)");
        root.style.setProperty("--love", "url(" + webStaticResourcePrefix + "assets/love.jpg)");
      },
      getSortInfo() {
        this.$http.get(this.$constant.baseURL + "/webInfo/getSortInfo")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.$store.commit("loadSortInfo", res.data);
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      changeColor() {
        this.isDark = !this.isDark;
        if (this.isDark) {
          this.applyDarkTheme();
          
          // 保存深色主题到localStorage（带过期时间）
          try {
            const themeData = {
              theme: 'dark',
              timestamp: Date.now(),
              expiry: 24 * 60 * 60 * 1000 // 1天过期时间（毫秒）
            };
            localStorage.setItem('poetize-theme', JSON.stringify(themeData));
            console.log('主题已保存到localStorage: dark，将在1天后过期');
          } catch (error) {
            console.error('保存主题到localStorage失败:', error);
          }
        } else {
          this.applyLightTheme();
          
          // 保存浅色主题到localStorage（带过期时间）
          try {
            const themeData = {
              theme: 'light',
              timestamp: Date.now(),
              expiry: 24 * 60 * 60 * 1000 // 1天过期时间（毫秒）
            };
            localStorage.setItem('poetize-theme', JSON.stringify(themeData));
            console.log('主题已保存到localStorage: light，将在1天后过期');
          } catch (error) {
            console.error('保存主题到localStorage失败:', error);
          }
        }
      },
      
      applyDarkTheme() {
        let root = document.querySelector(":root");
        root.style.setProperty("--background", "#272727");
        root.style.setProperty("--fontColor", "white");
        root.style.setProperty("--borderColor", "#4F4F4F");
        root.style.setProperty("--borderHoverColor", "black");
        root.style.setProperty("--articleFontColor", "#E4E4E4");
        root.style.setProperty("--articleGreyFontColor", "#D4D4D4");
        root.style.setProperty("--commentContent", "#383838");
        root.style.setProperty("--favoriteBg", "#1e1e1e");
        // 修复遮罩相关变量
        root.style.setProperty("--whiteMask", "rgba(56, 56, 56, 0.3)");
        root.style.setProperty("--maxWhiteMask", "rgba(56, 56, 56, 0.5)");
        root.style.setProperty("--maxMaxWhiteMask", "rgba(56, 56, 56, 0.7)");
        root.style.setProperty("--miniWhiteMask", "rgba(56, 56, 56, 0.15)");
        root.style.setProperty("--mask", "rgba(0, 0, 0, 0.5)");
        root.style.setProperty("--miniMask", "rgba(0, 0, 0, 0.3)");
        root.style.setProperty("--inputBackground", "#383838");
        root.style.setProperty("--secondaryText", "#B0B0B0");
        // 设置卡片背景RGB值用于半透明背景
        root.style.setProperty("--card-bg-rgb", "39, 39, 39");
      },
      
      applyLightTheme() {
        let root = document.querySelector(":root");
        root.style.setProperty("--background", "white");
        root.style.setProperty("--fontColor", "black");
        root.style.setProperty("--borderColor", "rgba(0, 0, 0, 0.5)");
        root.style.setProperty("--borderHoverColor", "rgba(110, 110, 110, 0.4)");
        root.style.setProperty("--articleFontColor", "#1F1F1F");
        root.style.setProperty("--articleGreyFontColor", "#616161");
        root.style.setProperty("--commentContent", "#F7F9FE");
        root.style.setProperty("--favoriteBg", "#f7f9fe");
        // 恢复亮色模式的遮罩变量
        root.style.setProperty("--whiteMask", "rgba(255, 255, 255, 0.3)");
        root.style.setProperty("--maxWhiteMask", "rgba(255, 255, 255, 0.5)");
        root.style.setProperty("--maxMaxWhiteMask", "rgba(255, 255, 255, 0.7)");
        root.style.setProperty("--miniWhiteMask", "rgba(255, 255, 255, 0.15)");
        root.style.setProperty("--mask", "rgba(0, 0, 0, 0.3)");
        root.style.setProperty("--miniMask", "rgba(0, 0, 0, 0.15)");
        root.style.setProperty("--inputBackground", "#f5f5f5");
        root.style.setProperty("--secondaryText", "#666666");
        // 设置卡片背景RGB值用于半透明背景
        root.style.setProperty("--card-bg-rgb", "255, 255, 255");
      },
      // 更新目录按钮显示状态
      updateTocButtonVisibility() {
        // 只在文章页面显示目录按钮
        this.showTocButton = this.$route.path.startsWith('/article/') && this.$route.params.id;
      },

      // 目录按钮点击事件
      clickTocButton() {
        const tocElements = document.querySelectorAll('.toc');
        tocElements.forEach(element => {
          const currentDisplay = window.getComputedStyle(element).display;
          if (currentDisplay === 'none') {
            element.style.display = 'unset';
          } else {
            element.style.display = 'none';
          }
        });
      },

      // 更新简化语言切换按钮显示状态
      updateSimpleLangSwitchVisibility() {
        // 只在文章页面显示简化语言切换按钮
        this.showSimpleLangSwitch = this.$route.path.startsWith('/article/') && this.$route.params.id;
      },

      // 获取当前语言的简化显示
      getSimpleLangDisplay() {
        // 从article组件获取当前语言，如果获取不到则默认为中文
        const articleComponent = this.getArticleComponent();

        if (articleComponent && articleComponent.currentLang) {
          const langMap = {
            'zh': '简',
            'zh-TW': '繁',
            'zh-CN': '简',
            'zh-HK': '港',
            'zh-Hant': '繁',
            'zh-Hans': '简',
            'en': 'EN',
            'ja': 'JP',
            'ko': '한',
            'fr': 'FR',
            'de': 'DE',
            'es': 'ES',
            'ru': 'RU',
            'pt': 'PT',
            'it': 'IT',
            'ar': 'AR',
            'th': 'TH',
            'vi': 'VI'
          };
          return langMap[articleComponent.currentLang] || articleComponent.currentLang.toUpperCase();
        }

        // 如果无法获取article组件，尝试从URL或localStorage获取默认语言
        const urlParams = new URLSearchParams(window.location.search);
        const urlLang = urlParams.get('lang');
        const savedLang = localStorage.getItem('preferredLanguage');

        // 优先使用URL参数，然后是保存的偏好，最后是默认值
        const defaultLang = urlLang || savedLang || 'zh';

        const langMap = {
          'zh': '简',
          'zh-TW': '繁',
          'zh-CN': '简',
          'zh-HK': '港',
          'zh-Hant': '繁',
          'zh-Hans': '简',
          'en': 'EN',
          'ja': 'JP',
          'ko': '한',
          'fr': 'FR',
          'de': 'DE',
          'es': 'ES',
          'ru': 'RU',
          'pt': 'PT',
          'it': 'IT',
          'ar': 'AR',
          'th': 'TH',
          'vi': 'VI'
        };

        return langMap[defaultLang] || '简';
      },

      // 获取简化语言切换按钮的提示文本
      getSimpleLangSwitchTitle() {
        const articleComponent = this.getArticleComponent();
        if (articleComponent && articleComponent.availableLanguageButtons) {
          const nextLang = this.getNextAvailableLanguage();
          if (nextLang) {
            return `点击切换到${nextLang.name}`;
          }
        }
        return '语言切换';
      },

      // 获取下一个可用语言
      getNextAvailableLanguage() {
        const articleComponent = this.getArticleComponent();
        if (articleComponent && articleComponent.availableLanguageButtons && articleComponent.availableLanguageButtons.length > 1) {
          const currentIndex = articleComponent.availableLanguageButtons.findIndex(
            lang => lang.code === articleComponent.currentLang
          );
          const nextIndex = (currentIndex + 1) % articleComponent.availableLanguageButtons.length;
          return articleComponent.availableLanguageButtons[nextIndex];
        }
        return null;
      },

      // 获取article组件实例
      getArticleComponent() {
        // 通过多种方式查找article组件
        const findArticleComponent = (children) => {
          for (let child of children) {
            // 检查组件是否有article相关的数据属性（更严格的检查）
            if (child.availableLanguageButtons !== undefined &&
                child.currentLang !== undefined &&
                child.handleLanguageSwitch !== undefined &&
                child.sourceLanguage !== undefined &&
                child.languageMap !== undefined) {
              return child;
            }
            // 检查组件名称和文件路径
            if (child.$options.name === 'article' ||
                child.$vnode?.tag?.includes('article') ||
                child.$options._componentTag === 'article' ||
                child.$options.__file?.includes('article.vue')) {
              return child;
            }
            // 递归查找子组件
            if (child.$children && child.$children.length > 0) {
              const found = findArticleComponent(child.$children);
              if (found) return found;
            }
          }
          return null;
        };

        // 首先尝试从$children查找
        let articleComponent = findArticleComponent(this.$children);

        // 如果没找到，尝试从$refs查找
        if (!articleComponent && this.$refs) {
          for (let refName in this.$refs) {
            const ref = this.$refs[refName];
            if (ref && ref.availableLanguageButtons !== undefined &&
                ref.currentLang !== undefined &&
                ref.handleLanguageSwitch !== undefined &&
                ref.sourceLanguage !== undefined) {
              articleComponent = ref;
              break;
            }
          }
        }

        // 如果还没找到，尝试从全局查找
        if (!articleComponent) {
          const allComponents = this.$root.$children;
          articleComponent = findArticleComponent(allComponents);
        }

        // 最后尝试从router-view中查找
        if (!articleComponent) {
          const routerView = this.$children.find(child =>
            child.$vnode?.componentOptions?.tag === 'router-view'
          );
          if (routerView && routerView.$children) {
            articleComponent = findArticleComponent(routerView.$children);
          }
        }

        return articleComponent;
      },

      // 处理简化语言切换按钮点击
      handleSimpleLangSwitch() {
        const articleComponent = this.getArticleComponent();
        if (articleComponent && articleComponent.handleLanguageSwitch) {
          const nextLang = this.getNextAvailableLanguage();
          if (nextLang) {
            // 调用article组件的语言切换方法
            articleComponent.handleLanguageSwitch(nextLang.code);

            // 强制更新显示
            this.$forceUpdate();
          }
        }
      },

      toTop() {
        window.scrollTo({
          top: 0,
          behavior: "smooth"
        });
      },
      onScrollPage() {
        this.scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
      },
      isDaylight() {
        // 后台可配置：enableAutoNight, autoNightStart, autoNightEnd
        const cfg = this.$store?.state?.webInfo || {};

        // 若未开启自动夜间则直接返回 false
        if (cfg.enableAutoNight === false) return false;

        // 读取小时区间，提供默认值 23~7
        const start = typeof cfg.autoNightStart === 'number' ? cfg.autoNightStart : 23;
        const end   = typeof cfg.autoNightEnd   === 'number' ? cfg.autoNightEnd   : 7;

        const h = new Date().getHours();

        // 跨午夜区间的判断
        if (start > end) {
          return h >= start || h < end;
        }
        // 同日区间
        return h >= start && h < end;
      },
      changeMouseAnimation() {
        this.mouseAnimation = !this.mouseAnimation;
        if (this.mouseAnimation) {
          this.$nextTick(() => {
            mousedown();
          });
        }
      },
      getWindowWidth() {
        // Implementation of getWindowWidth method
      },
      getRandomFont() {
        // Implementation of getRandomFont method
      },
      // 根据后台配置重新判断并自动应用夜间主题（仅当用户未手动设置主题时调用）
      maybeApplyAutoNight() {
        try {
          const savedData = localStorage.getItem('poetize-theme');
          if (savedData) {
            // 检查是否有有效的主题设置
            try {
              const themeData = JSON.parse(savedData);
              if (themeData && themeData.timestamp && themeData.theme) {
                const now = Date.now();
                const elapsed = now - themeData.timestamp;
                
                // 如果主题未过期，则尊重用户选择
                if (elapsed <= themeData.expiry) {
                  return; // 用户已手动选择主题且未过期，尊重用户
                } else {
                  // 主题已过期，清除并继续自动逻辑
                  console.log('主题设置已过期，将使用自动夜间模式逻辑');
                  localStorage.removeItem('poetize-theme');
                }
              }
            } catch (parseError) {
              // 旧格式或解析失败，认为用户有手动设置
              return;
            }
          }

          if (this.isDaylight()) {
            this.isDark = true;
            this.applyDarkTheme();
          } else {
            this.isDark = false;
            this.applyLightTheme();
          }
        } catch(e) {
          console.warn('auto night check error', e);
        }
      },
      applyGrayMask() {
        if (document.getElementById('gray-mask')) return;
        const mask = document.createElement('div');
        mask.id = 'gray-mask';
        mask.style.position = 'fixed';
        mask.style.inset = '0';
        mask.style.pointerEvents = 'none';
        mask.style.background = '#000';
        mask.style.mixBlendMode = 'saturation';
        mask.style.zIndex = '2147483647';
        document.body.appendChild(mask);
      },
      getWebsitConfig() {
        this.$store.dispatch("getWebsitConfig");
      },
      loadFont() {
      },
      getActualBackgroundHeight() {
        // 获取当前设置的首页上拉高度，与bannerStyle()保持一致的计算逻辑
        const height = this.$store.state.webInfo.homePagePullUpHeight;
        
        // 如果是有效的数值且在0-100范围内，直接使用该值作为vh
        if (typeof height === 'number' && height >= 0 && height <= 100) {
          // height值直接对应vh，100 = 100vh = window.innerHeight
          return window.innerHeight * (height / 100);
        }
        
        // 否则使用默认的50vh
        return window.innerHeight / 2;
      }
    }
  }
</script>

<style scoped>

.toolbar-content {
  width: 100%;
  height: 60px;
  color: var(--white);
  /* 固定位置，不随滚动条滚动 */
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  /* 禁止选中文字 */
  user-select: none;
  transition: all 0.3s ease-in-out;
  font-family: 'MyAwesomeFont', serif;
}

.toolbar-content.enter {
  background: var(--toolbarBackground);
  color: var(--toolbarFont);
  box-shadow: 0 1px 3px 0 rgba(0, 34, 77, 0.05);
}

.toolbar-content.hoverEnter {
  background: var(--translucent);
  box-shadow: 0 1px 3px 0 rgba(0, 34, 77, 0.05);
}

.toolbar-title {
  margin-left: 30px;
  cursor: pointer;
  font-family: 'MyAwesomeFont', serif;
}

.toolbar-mobile-menu {
  font-size: 30px;
  margin-right: 15px;
  cursor: pointer;
}

.scroll-menu {
  margin: 0 25px 0 0;
  display: flex;
  justify-content: flex-end;
  padding: 0;
  font-family: 'MyAwesomeFont', serif;
}

.scroll-menu li {
  list-style: none;
  margin: 0 12px;
  font-size: 17px;
  height: 60px;
  line-height: 60px;
  position: relative;
  cursor: pointer;
  font-family: 'MyAwesomeFont', serif;
}

.scroll-menu li:hover .my-menu span {
  color: var(--themeBackground);
}

.scroll-menu li:hover .my-menu i {
  color: var(--themeBackground);
  animation: scale 1.5s ease-in-out infinite;
}

.scroll-menu li .my-menu:after {
  content: "";
  display: block;
  position: absolute;
  bottom: 0;
  height: 6px;
  background-color: var(--themeBackground);
  width: 100%;
  max-width: 0;
  transition: max-width 0.25s ease-in-out;
}

.scroll-menu li:hover .my-menu:after {
  max-width: 100%;
}

.sortMenu {
  margin-left: 44px;
  font-size: 17px;
  position: relative;
}

.sortMenu:after {
  top: 32px;
  width: 35px;
  left: 0;
  height: 2px;
  background: var(--themeBackground);
  content: "";
  border-radius: 1px;
  position: absolute;
}

.el-dropdown {
  font-size: unset;
  color: unset;
}

.el-popper[x-placement^=bottom] {
  margin-top: -8px;
}

.el-dropdown-menu {
  padding: 5px 0;
}

.el-dropdown-menu__item {
  font-size: unset;
}

.el-dropdown-menu__item:hover {
  background-color: var(--white);
  color: var(--themeBackground);
}

.toolButton {
  position: fixed;
  right: 3vh;
  bottom: 3vh;
  animation: slide-bottom 0.5s ease-in-out both;
  z-index: 100;
  cursor: pointer;
  font-size: 25px;
  width: 30px;
}

.my-setting {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-around;
  cursor: pointer;
  font-size: 20px;
}

.my-setting i {
  padding: 5px;
}

.my-setting i:hover {
  color: var(--themeBackground);
}

.cd-top {
  background: var(--toTop) no-repeat center;
  position: fixed;
  right: 5vh;
  top: -900px;
  z-index: 99;
  width: 70px;
  height: 900px;
  background-size: contain;
  transition: all 0.5s ease-in-out;
  cursor: pointer;
}

.backTop {
  transition: all 0.3s ease-in;
  position: relative;
  top: 0;
  left: -13px;
}

.backTop:hover {
  top: -10px;
}

#outerImg {
  position: fixed;
  top: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 10;
  width: 100%;
  height: 100%;
  display: none;
}

/* 简化语言切换按钮样式 - 仅在≤1050px时显示 */
.simple-lang-switch {
  display: none;
}

@media (max-width: 1050px) {
  .simple-lang-switch {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(10px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    cursor: pointer;
    transition: all 0.3s ease;
    margin-bottom: 8px;
    border: 1px solid rgba(255, 255, 255, 0.8);
    user-select: none;
  }

  .simple-lang-switch:hover {
    background: rgba(255, 255, 255, 1);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    transform: translateY(-1px);
  }

  .simple-lang-switch:active {
    transform: translateY(0) scale(0.95);
  }

  .simple-lang-text {
    font-size: 12px;
    font-weight: 600;
    color: var(--black);
    transition: color 0.3s ease;
  }

  .simple-lang-switch:hover .simple-lang-text {
    color: var(--themeBackground);
  }
}

/* 目录按钮样式 */
.toc-button-container {
  cursor: pointer;
  font-size: 25px;
  width: 30px;
  transition: all 0.3s ease;
  margin-bottom: 4px
}

.toc-button-icon {
  font-size: 23px;
  color: var(--black);
}

.toc-button-container:hover .toc-button-icon {
  color: var(--themeBackground);
}

@media screen and (max-width: 400px) {
  .toolButton {
    right: 0.5vh;
  }
}

/* 移动端简化语言切换按钮优化 */
@media (max-width: 768px) {
  .simple-lang-switch {
    width: 32px !important;
    height: 32px !important;
    margin-bottom: 6px !important;
    border-radius: 6px !important;
  }

  .simple-lang-text {
    font-size: 11px !important;
  }

  /* 移动端触摸优化 */
  .simple-lang-switch:hover {
    transform: none !important; /* 移除hover效果避免触摸设备粘滞 */
  }

  .simple-lang-switch:active {
    transform: scale(0.95) !important;
  }
}

.my-menu {
  font-family: 'MyAwesomeFont', serif;
}

.my-menu span {
  font-family: 'MyAwesomeFont', serif;
}

/* 圆形登录按钮样式 */
.circle-login-button {
  background-color: #ff8da1;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
}
</style>
