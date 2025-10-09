import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '../store'
import constant from '../utils/constant'
import common from '../utils/common'
import { handleTokenExpire, isLoggedIn, getValidToken } from '../utils/tokenExpireHandler'
import translationModelManage from "../components/admin/translationModelManage";

const originalPush = VueRouter.prototype.push;
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => err);
}

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    component: () => import('../components/home'),
    children: [{
      path: "/",
      name: "index",
      component: () => import('../components/index')
    }, {
      path: "/sort",
      name: "sort",
      component: () => import('../components/sort')
    }, {
      path: "/sort/:id",
      name: "sort-category",
      component: () => import('../components/sort')
    }, {
      path: "/article/:lang/:id",
      name: "article-translated",
      component: () => import('../components/article')
    }, {
      path: "/article/:id",
      name: "article",
      component: () => import('../components/article')
    }, {
      path: "/weiYan",
      name: "weiYan",
      component: () => import('../components/weiYan')
    }, {
      path: "/love",
      name: "love",
      component: () => import('../components/love')
    }, {
      path: "/favorite",
      name: "favorite",
      component: () => import('../components/favorite')
    }, {
      path: "/friends",
      name: "friends",
      component: () => import('../components/FriendLinks')
    }, {
      path: "/music",
      name: "music",
      component: () => import('../components/Music')
    }, {
      path: "/favorites",
      name: "favorites",
      component: () => import('../components/Favorites')
    }, {
      path: "/travel",
      name: "travel",
      component: () => import('../components/travel')
    }, {
      path: "/message",
      name: "message",
      component: () => import('../components/message')
    }, {
      path: "/about",
      name: "about",
      component: () => import('../components/about')
    }, {
      path: "/user",
      name: "user",
      component: () => import('../components/user')
    }, {
      path: "/oauth-callback",
      name: "oauth-callback",
      component: () => import('../components/oauth-callback')
    }, {
      path: "/letter",
      name: "letter",
      component: () => import('../components/letter')
    }, {
      path: "/privacy",
      name: "privacy",
      component: () => import('../views/Privacy')
    }]
  },
  {
    path: '/admin',
    redirect: '/welcome',
    meta: {requiresAuth: true},
    component: () => import('../components/admin/admin'),
    children: [{
      path: '/welcome',
      name: 'welcome',
      component: () => import('../components/admin/welcome')
    }, {
      path: '/main',
      name: 'main',
      component: () => import('../components/admin/main')
    }, {
      path: '/webEdit',
      name: 'webEdit',
      component: () => import('../components/admin/webEdit')
    }, {
      path: '/userList',
      name: 'userList',
      component: () => import('../components/admin/userList')
    }, {
      path: '/postList',
      name: 'postList',
      component: () => import('../components/admin/postList')
    }, {
      path: '/postEdit',
      name: 'postEdit',
      component: () => import('../components/admin/postEdit')
    }, {
      path: '/sortList',
      name: 'sortList',
      component: () => import('../components/admin/sortList')
    }, {
      path: '/configList',
      name: 'configList',
      component: () => import('../components/admin/configList')
    }, {
      path: '/commentList',
      name: 'commentList',
      component: () => import('../components/admin/commentList')
    }, {
      path: '/treeHoleList',
      name: 'treeHoleList',
      component: () => import('../components/admin/treeHoleList')
    }, {
      path: '/resourceList',
      name: 'resourceList',
      component: () => import('../components/admin/resourceList')
    }, {
      path: '/loveList',
      name: 'loveList',
      component: () => import('../components/admin/loveList')
    }, {
      path: '/resourcePathList',
      name: 'resourcePathList',
      component: () => import('../components/admin/resourcePathList')
    }, {
      path: '/visitStats',
      name: 'visitStats',
      component: () => import('../components/admin/visitStats')
    }, {
      path: '/seoConfig',
      name: 'seoConfig',
      component: () => import('../components/admin/seoConfig')
    }, {
      path: '/aiChatConfig',
      name: 'aiChatConfig',
      component: () => import('../components/admin/aiChatConfig')
    }, 
    // 下面两个组件暂不需要，已注释
    // {
    //   path: '/admin/userManage',
    //   component: () => import('../components/admin/userManage.vue'),
    //   meta: {
    //     requireAuth: true,
    //     isAdmin: true
    //   }
    // }, 
    {
      path: '/admin/translationModel',
      component: () => import('../components/admin/translationModelManage.vue'),
      meta: {
        requireAuth: true,
        isAdmin: true
      }
    }
    // {
    //   path: '/admin/dataStatistics',
    //   component: () => import('../components/admin/dataStatistics.vue'),
    //   meta: {
    //     requireAuth: true,
    //     isAdmin: true
    //   }
    // }
    ]
  },
  {
    path: '/verify',
    name: 'verify',
    component: () => import('../components/admin/verify')
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('../components/Forbidden')
  },
  {
    path: '/404',
    name: 'notFound',
    component: () => import('../components/NotFound')
  },
  {
    path: '*',
    name: 'catchAll',
    component: () => import('../components/NotFound')
  }
]

const router = new VueRouter({
  mode: "history",
  routes: routes,
  scrollBehavior(to, from, savedPosition) {
    return {x: 0, y: 0}
  }
})

router.beforeEach((to, from, next) => {
  // 检查是否需要重定向到403页面（Nginx错误重定向）
  if (to.query.redirect === '403') {
    next('/403');
    return;
  }

  // Token过期检查 - 在处理其他逻辑之前先检查token状态
  // 跳过登录页面和公共页面的token检查
  const publicPaths = ['/user', '/verify', '/403', '/404', '/', '/about', '/privacy'];
  const isPublicPath = publicPaths.includes(to.path) || to.path.startsWith('/article/') || to.path.startsWith('/sort/');

  if (!isPublicPath) {
    // 检查是否需要管理员权限
    const needsAdminAuth = to.matched.some(record => record.meta.isAdmin);

    if (needsAdminAuth) {
      // 检查管理员token
      const adminToken = getValidToken(true);
      const isAdminLoggedIn = isLoggedIn(true);

      if (!adminToken || !isAdminLoggedIn) {
        console.log('管理员token无效或过期，重定向到登录页');
        handleTokenExpire(true, to.fullPath, { showMessage: false });
        return;
      }
    } else {
      // 检查普通用户token（对于需要登录的页面）
      const needsAuth = to.matched.some(record => record.meta.requireAuth);

      if (needsAuth) {
        const userToken = getValidToken(false);
        const isUserLoggedIn = isLoggedIn(false);

        if (!userToken || !isUserLoggedIn) {
          console.log('用户token无效或过期，重定向到登录页');
          handleTokenExpire(false, to.fullPath, { showMessage: false });
          return;
        }
      }
    }
  }

  // 处理OAuth登录回调token
  if (to.query.userToken) {
    console.log('检测到OAuth回调token，开始处理登录...');
    const userToken = to.query.userToken;
    const emailCollectionNeeded = to.query.emailCollectionNeeded === 'true';

    console.log('OAuth回调参数:', {
      userToken: userToken ? userToken.substring(0, 20) + '...' : null,
      emailCollectionNeeded: emailCollectionNeeded
    });

    // 使用同步XMLHttpRequest验证token（与poetize-im-ui保持一致）
    try {
      const xhr = new XMLHttpRequest();
      // 使用导入的constant而不是store.state.constant
      const baseURL = constant.baseURL || 'http://localhost:8081';

      // 对token进行AES加密，因为后端期望接收加密的token
      const encryptedToken = common.encrypt(userToken);
      console.log('原始token长度:', userToken.length, '加密后token长度:', encryptedToken.length);

      xhr.open('post', baseURL + "/user/token", false);
      xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
      xhr.send("userToken=" + encryptedToken);

      console.log('Token验证请求状态:', xhr.status, xhr.statusText);
      console.log('Token验证响应:', xhr.responseText);

      if (xhr.status === 200) {
        const result = JSON.parse(xhr.responseText);
        if (result && result.code === 200) {
          console.log('OAuth登录成功，用户信息:', result.data);
          console.log('OAuth响应消息:', result.message);

          // 检查是否需要邮箱收集（通过URL参数或响应消息）
          const needsEmailCollection = emailCollectionNeeded || result.message === 'EMAIL_COLLECTION_NEEDED';

          if (needsEmailCollection) {
            console.log('✅ 检测到需要邮箱收集，准备显示邮箱收集模态框');
            console.log('邮箱收集触发方式:', emailCollectionNeeded ? 'URL参数' : '响应消息');

            // 存储临时的用户信息和token
            // 尝试从用户数据中获取provider，如果没有则从URL参数获取
            const provider = result.data.platformType || to.query.provider || 'unknown';
            const tempUserData = {
              ...result.data,
              needsEmailCollection: true,
              provider: provider
            };

            // 先存储token，但标记为需要完善信息
            localStorage.setItem("userToken", result.data.accessToken);
            localStorage.setItem("adminToken", result.data.accessToken);
            localStorage.setItem("tempUserData", JSON.stringify(tempUserData));

            console.log('已存储临时用户数据:', tempUserData);

            // 重定向到首页，首页会检测到需要邮箱收集并显示模态框
            console.log('重定向到首页并显示邮箱收集模态框');
            next({
              path: '/',
              query: { showEmailCollection: 'true' },
              replace: true
            });
            return;
          }

          // 正常的OAuth登录流程
          // 清除旧的缓存数据
          localStorage.removeItem("currentAdmin");
          localStorage.removeItem("currentUser");

          localStorage.setItem("userToken", result.data.accessToken);
          localStorage.setItem("adminToken", result.data.accessToken);
          store.commit("loadCurrentUser", result.data);
          store.commit("loadCurrentAdmin", result.data);

          // 清除URL中的token参数并重定向到首页
          const cleanPath = to.path === '/' ? '/' : to.path;
          next({ path: cleanPath, replace: true });
          return;
        } else {
          console.error('OAuth token验证失败:', result);
          // token验证失败，清除token参数并继续正常流程
          next({ path: to.path, query: {}, replace: true });
          return;
        }
      } else {
        console.error('OAuth token验证HTTP错误:', xhr.status, xhr.statusText);
        // HTTP错误，清除token参数并继续正常流程
        next({ path: to.path, query: {}, replace: true });
        return;
      }
    } catch (error) {
      console.error('OAuth token验证异常:', error);
      console.error('错误详情:', {
        message: error.message,
        stack: error.stack,
        userToken: userToken ? userToken.substring(0, 10) + '...' : 'undefined',
        baseURL: constant.baseURL
      });
      // 验证异常，清除token参数并继续正常流程
      next({ path: to.path, query: {}, replace: true });
      return;
    }
  }
  
  // 检查是否需要管理员权限
  if (to.matched.some(record => record.meta.requiresAdmin)) {
    // 检查是否有管理员token
    const adminToken = localStorage.getItem("adminToken");
    if (!adminToken) {
      // 如果没有管理员token，跳转到管理员登录页
      next({
        path: '/verify',
        query: { redirect: to.fullPath }
      });
    } else {
      // 如果有管理员token，检查用户类型
      const currentAdmin = store.state.currentAdmin;
      if (currentAdmin && (currentAdmin.userType === 0 || currentAdmin.userType === 1)) {
        next();
      } else {
        // 如果用户不是管理员，跳转到首页
        next('/');
      }
    }
  } else if (to.path === '/user') {
    // 用户路由特殊处理，允许未登录用户访问
    next();
  } else {
    next();
  }
})

export default router
