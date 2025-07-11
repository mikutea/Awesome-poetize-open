import {useStore} from 'vuex';

import {useDialog} from 'naive-ui';

import {nextTick} from 'vue';

import {ElMessage} from "element-plus";

import {reactive, getCurrentInstance, onMounted, onBeforeUnmount, watchEffect, toRefs, onErrorCaptured} from 'vue';

// 声明全局jQuery变量
/* global $ */

// 错误处理工具函数
function handleError(error, context = '') {
  console.group('🚨 ImUtil Hook错误');
  console.error('错误上下文:', context);
  console.error('错误信息:', error.message);
  console.error('错误堆栈:', error.stack);
  console.error('发生时间:', new Date().toLocaleString());
  console.groupEnd();
  
  ElMessage({
    message: `操作失败: ${error.message}`,
    type: 'error',
    duration: 5000
  });
}

export default function () {
  const globalProperties = getCurrentInstance().appContext.config.globalProperties;
  const $common = globalProperties.$common;
  const $http = globalProperties.$http;
  const $constant = globalProperties.$constant;
  const store = useStore();
  const dialog = useDialog();

  let imUtilData = reactive({
    //系统消息
    systemMessages: [],
    showBodyLeft: true,
    //表情包
    imageList: []
  })

  // 组件错误捕获
  onErrorCaptured((err, instance, info) => {
    handleError(err, `ImUtil组件错误 - ${info}`);
    return false; // 阻止错误继续传播
  });

  onMounted(() => {
    try {
      // 确保jQuery已加载
      if (typeof $ !== 'undefined' && $common.mobile()) {
        $(".friend-aside").click(function () {
          imUtilData.showBodyLeft = true;
          mobileRight();
        });

        $(".body-right").click(function () {
          imUtilData.showBodyLeft = false;
          mobileRight();
        });
      }
      mobileRight();
    } catch (error) {
      handleError(error, 'ImUtil初始化');
    }
  })

  function changeAside() {
    imUtilData.showBodyLeft = !imUtilData.showBodyLeft;
    mobileRight();
  }

  function mobileRight() {
    try {
      if (typeof $ !== 'undefined' && imUtilData.showBodyLeft && $common.mobile()) {
        $(".body-right").addClass("mobile-right");
      } else if (typeof $ !== 'undefined' && !imUtilData.showBodyLeft && $common.mobile()) {
        $(".body-right").removeClass("mobile-right");
      }
    } catch (error) {
      handleError(error, 'mobileRight函数');
    }
  }

  function getSystemMessages() {
    try {
      $http.get($constant.baseURL + "/imChatUserMessage/listSystemMessage")
        .then((res) => {
          if (!$common.isEmpty(res.data) && !$common.isEmpty(res.data.records)) {
            imUtilData.systemMessages = res.data.records;
          }
        })
        .catch((error) => {
          handleError(error, '获取系统消息');
        });
    } catch (error) {
      handleError(error, 'getSystemMessages函数调用');
    }
  }

  function hiddenBodyLeft() {
    try {
      if (typeof $ !== 'undefined' && $common.mobile()) {
        $(".body-right").click(function () {
          imUtilData.showBodyLeft = false;
          mobileRight();
        });
      }
    } catch (error) {
      handleError(error, 'hiddenBodyLeft函数');
    }
  }

  function imgShow() {
    try {
      if (typeof $ === 'undefined') {
        console.warn('jQuery未加载，无法初始化图片显示功能');
        return;
      }
      
      $(".message img").click(function () {
        try {
          let src = $(this).attr("src");
          $("#bigImg").attr("src", src);

          /** 获取当前点击图片的真实大小，并显示弹出层及大图 */
          $("<img/>").attr("src", src).load(function () {
            try {
              let windowW = $(window).width();//获取当前窗口宽度
              let windowH = $(window).height();//获取当前窗口高度
              let realWidth = this.width;//获取图片真实宽度
              let realHeight = this.height;//获取图片真实高度
              let imgWidth, imgHeight;
              let scale = 0.8;//缩放尺寸，当图片真实宽度和高度大于窗口宽度和高度时进行缩放

              if (realHeight > windowH * scale) {//判断图片高度
                imgHeight = windowH * scale;//如大于窗口高度，图片高度进行缩放
                imgWidth = imgHeight / realHeight * realWidth;//等比例缩放宽度
                if (imgWidth > windowW * scale) {//如宽度仍大于窗口宽度
                  imgWidth = windowW * scale;//再对宽度进行缩放
                }
              } else if (realWidth > windowW * scale) {//如图片高度合适，判断图片宽度
                imgWidth = windowW * scale;//如大于窗口宽度，图片宽度进行缩放
                imgHeight = imgWidth / realWidth * realHeight;//等比例缩放高度
              } else {//如果图片真实高度和宽度都符合要求，高宽不变
                imgWidth = realWidth;
                imgHeight = realHeight;
              }
              $("#bigImg").css("width", imgWidth);//以最终的宽度对图片缩放

              let w = (windowW - imgWidth) / 2;//计算图片与窗口左边距
              let h = (windowH - imgHeight) / 2;//计算图片与窗口上边距
              $("#innerImg").css({"top": h, "left": w});//设置top和left属性
              $("#outerImg").fadeIn("fast");//淡入显示
            } catch (error) {
              handleError(error, '图片加载和显示');
            }
          }).error(function() {
            handleError(new Error('图片加载失败'), '图片加载');
          });

          $("#outerImg").click(function () {//再次点击淡出消失弹出层
            try {
              $(this).fadeOut("fast");
            } catch (error) {
              handleError(error, '图片弹出层关闭');
            }
          });
        } catch (error) {
          handleError(error, '图片点击事件');
        }
      });
    } catch (error) {
      handleError(error, 'imgShow函数初始化');
    }
  }

  function getImageList() {
    try {
      $http.get($constant.baseURL + "/resource/getImageList")
        .then((res) => {
          if (!$common.isEmpty(res.data)) {
            imUtilData.imageList = res.data;
          }
        })
        .catch((error) => {
          handleError(error, '获取图片列表');
        });
    } catch (error) {
      handleError(error, 'getImageList函数调用');
    }
  }

  function parseMessage(content) {
    content = content.replace(/\n{2,}/g, '<div style="height: 12px"></div>');
    content = content.replace(/\n/g, '<br/>');
    content = $common.faceReg(content);
    content = $common.pictureReg(content);
    return content;
  }

  return {
    imUtilData,
    changeAside,
    mobileRight,
    getSystemMessages,
    hiddenBodyLeft,
    imgShow,
    getImageList,
    parseMessage
  }
}
