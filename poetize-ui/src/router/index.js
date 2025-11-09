import Vue from 'vue'
import VueRouter from 'vue-router'
import { useMainStore } from '../stores/main'
import constant from '../utils/constant'
import common from '../utils/common'
import { handleTokenExpire, isLoggedIn, getValidToken } from '../utils/tokenExpireHandler'

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
    redirect: to => {
      // 所有访问 /verify 的请求都重定向到 /user 登录页
      // 保留原有的 redirect 参数
      const redirect = to.query.redirect;
      const query = { fromVerify: 'true' };
      if (redirect) {
        query.redirect = redirect;
      }
      return {
        path: '/user',
        query: query
      };
    }
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

router.beforeEach(async (to, from, next) => {
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
          handleTokenExpire(false, to.fullPath, { showMessage: false });
          return;
        }
      }
    }
  }

  // 处理OAuth登录回调token
  if (to.query.userToken) {
    // 使用Promise来异步处理token验证
    await handleOAuthToken(to, from, next);
    return;
  }

  // 继续正常的路由流程
  next();
})

/**
 * 处理OAuth登录回调的异步token验证
 */
async function handleOAuthToken(to, from, next) {
  const userToken = to.query.userToken;
  const emailCollectionNeeded = to.query.emailCollectionNeeded === 'true';
  const baseURL = constant.baseURL;

  try {
    // 异步加密token
    const encryptedToken = await common.encrypt(userToken);

    // 使用异步请求验证token
    const response = await fetch(baseURL + "/user/token", {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: "userToken=" + encryptedToken
    });

    if (response.ok) {
      const result = await response.json();
      if (result && result.code === 200) {
        // 检查是否需要邮箱收集（通过URL参数或响应消息）
        const needsEmailCollection = emailCollectionNeeded || result.message === 'EMAIL_COLLECTION_NEEDED';

        if (needsEmailCollection) {
          // 存储临时的用户信息和token
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

          // 获取原始重定向路径
          const redirectPath = to.query.redirect || from.query.redirect || sessionStorage.getItem('oauthRedirectPath') || '/';
          
          // 重定向到原始页面，并添加showEmailCollection参数
          next({
            path: redirectPath,
            query: { showEmailCollection: 'true' },
            replace: true
          });
          return;
        }

        // 正常的OAuth登录流程
        localStorage.removeItem("currentAdmin");
        localStorage.removeItem("currentUser");

        // 存储token
        localStorage.setItem("userToken", result.data.accessToken);
        localStorage.setItem("adminToken", result.data.accessToken);
        const mainStore = useMainStore();
        mainStore.loadCurrentUser(result.data);
        mainStore.loadCurrentAdmin(result.data);

        // 获取原始重定向路径
        const redirectPath = to.query.redirect || from.query.redirect || sessionStorage.getItem('oauthRedirectPath') || '/';
        
        // 清除URL中的token参数并重定向到原始页面
        next({ 
          path: redirectPath, 
          query: { ...to.query, token: undefined, state: undefined },
          replace: true 
        });
        return;
      } else {
        console.error('OAuth token验证失败:', result);
        next({ path: to.path, query: {}, replace: true });
        return;
      }
    } else {
      console.error('OAuth token验证HTTP错误:', response.status);
      next({ path: to.path, query: {}, replace: true });
      return;
    }
  } catch (error) {
    console.error('OAuth token验证异常:', error);
    next({ path: to.path, query: {}, replace: true });
    return;
  }
}

export default router
