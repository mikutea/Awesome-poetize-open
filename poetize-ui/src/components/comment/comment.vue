<template>
  <div>
    <!-- 评论框 -->
    <div style="margin-bottom: 40px">
      <div class="comment-head">
        <i class="el-icon-edit-outline" style="font-weight: bold;font-size: 22px;"></i> 留言
      </div>
      <div>
        <!-- 文字评论 -->
        <div v-show="!isGraffiti">
          <commentBox ref="commentBox"
                      @showGraffiti="isGraffiti = !isGraffiti"
                      @submitComment="submitComment">
          </commentBox>
        </div>
        <!-- 画笔 -->
<!--        <div v-show="isGraffiti">-->
<!--          <graffiti @showComment="isGraffiti = !isGraffiti"-->
<!--                    @addGraffitiComment="addGraffitiComment">-->
<!--          </graffiti>-->
<!--        </div>-->
      </div>
    </div>

    <!-- 评论内容 -->
    <div v-if="comments.length > 0">
      <!-- 评论数量 -->
      <div class="commentInfo-title">
        <span style="font-size: 1.15rem">Comments | </span>
        <span>{{ total }} 条留言</span>
      </div>
      <!-- 评论详情 -->
      <div id="comment-content" class="commentInfo-detail"
           v-for="(item, index) in comments"
           :key="index">
        <!-- 头像 -->
        <el-avatar shape="square" class="commentInfo-avatar" :size="35" :src="$common.getAvatarUrl(item.avatar)">
          <img :src="$getDefaultAvatar()" />
        </el-avatar>

        <div style="flex: 1;padding-left: 12px">
          <!-- 评论信息 -->
          <div style="display: flex;justify-content: space-between">
            <div>
              <span class="commentInfo-username">{{ item.username }}</span>
              <span class="commentInfo-master" v-if="item.userId === userId">主人翁</span>
              <span class="commentInfo-location" v-if="item.location">{{ item.location }}</span>
              <span class="commentInfo-other">{{ $common.getDateDiff(item.createTime) }}</span>
            </div>
            <div class="commentInfo-reply" @click="replyDialog(item, item)">
              <span v-if="item.childComments && item.childComments.total > 0">{{item.childComments.total}} </span><span>回复</span>
            </div>
          </div>
          <!-- 评论内容 -->
          <div class="commentInfo-content">
            <span v-html="item.commentContent"></span>
          </div>
          <!-- 🔧 新UI：懒加载子评论展示 -->
          <div v-if="item.childComments && item.childComments.total > 0">
            <!-- 展开按钮（当回复未展开时显示） -->
            <div v-if="!item.expanded" class="pagination-wrap">
              <div class="expand-replies-btn" @click="expandReplies(item)" :disabled="item.loadingReplies">
                <span class="expand-text" v-if="!item.loadingReplies">
                  展开{{ item.childComments.total }}条回复
                </span>
                <span class="expand-text" v-else>
                  <i class="el-icon-loading"></i> 加载中...
                </span>
                <i class="el-icon-arrow-down expand-icon" v-if="!item.loadingReplies"></i>
              </div>
            </div>

            <!-- 子评论列表（展开时显示） -->
            <div v-if="item.expanded && item.childComments && item.childComments.records && item.childComments.records.length > 0">
              <div class="commentInfo-detail"
                   v-for="replyItem in item.childComments.records"
                   :key="replyItem.id">
                <!-- 头像 -->
                <el-avatar shape="square" class="commentInfo-avatar" :size="30" :src="$common.getAvatarUrl(replyItem.avatar)">
                  <img :src="$getDefaultAvatar()" />
                </el-avatar>

                <div style="flex: 1;padding-left: 12px">
                  <!-- 评论信息 -->
                  <div style="display: flex;justify-content: space-between">
                    <div>
                      <span class="commentInfo-username-small">{{ replyItem.username }}</span>
                      <span class="commentInfo-master" v-if="replyItem.userId === userId">主人翁</span>
                      <span class="commentInfo-location-small" v-if="replyItem.location">{{ replyItem.location }}</span>
                      <span class="commentInfo-other">{{ $common.getDateDiff(replyItem.createTime) }}</span>
                    <span class="commentInfo-reply-indicator" style="color: #666;"
                          v-if="shouldShowReplyIndicator(replyItem, item)">
                      回复了 {{ replyItem.parentUsername }}
                    </span>
                    </div>
                    <div>
                      <span class="commentInfo-reply" @click="replyDialog(replyItem, item)">
                        回复
                      </span>
                    </div>
                  </div>
                  <!-- 评论内容 -->
                  <div class="commentInfo-content">
                    <span v-html="replyItem.commentContent"></span>
                  </div>
                </div>
              </div>

              <!-- 加载更多回复按钮 -->
              <div class="pagination-wrap" v-if="item.hasMoreReplies">
                <div class="pagination" @click="loadMoreReplies(item)" :disabled="item.loadingReplies">
                  <span v-if="!item.loadingReplies">加载更多回复</span>
                  <span v-else><i class="el-icon-loading"></i> 加载中...</span>
                </div>
              </div>

              <!-- 折叠回复按钮 -->
              <div class="pagination-wrap">
                <div class="collapse-replies-btn" @click="collapseReplies(item)">
                  <span class="collapse-text">折叠回复</span>
                  <i class="el-icon-arrow-up collapse-icon"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <!-- 🔧 懒加载UI：替换传统分页 -->
      <div v-if="enableLazyLoad" class="lazy-load-container">
        <!-- 加载更多按钮 -->
        <div v-if="hasMoreComments && !isLoadingMore" class="load-more-btn-container">
          <el-button
            type="text"
            class="load-more-btn"
            @click="loadMoreComments"
            :disabled="isLoadingMore">
            <i class="el-icon-arrow-down"></i>
            加载更多评论
          </el-button>
        </div>

        <!-- 加载中状态 -->
        <div v-if="isLoadingMore" class="loading-container">
          <i class="el-icon-loading"></i>
          <span>正在加载更多评论...</span>
        </div>

        <!-- 没有更多评论提示 -->
        <div v-if="!hasMoreComments && comments.length > 0" class="no-more-comments">
          <span>没有更多评论了</span>
        </div>
      </div>

      <!-- 🔧 传统分页（备用，可通过enableLazyLoad控制） -->
      <proPage v-if="!enableLazyLoad"
               :current="pagination.current"
               :size="pagination.size"
               :total="pagination.total"
               :buttonSize="6"
               :color="$constant.commentPageColor"
               @toPage="toPage">
      </proPage>
    </div>

    <div v-else class="myCenter" style="color: var(--greyFont)">
      <i>来发第一个留言啦~</i>
    </div>

    <el-dialog title="留言"
               :visible.sync="replyDialogVisible"
               width="30%"
               :before-close="handleClose"
               :append-to-body="true"
               custom-class="centered-dialog"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <commentBox ref="replyCommentBox"
                    :disableGraffiti="true"
                    @submitComment="submitReply">
        </commentBox>
      </div>
    </el-dialog>
  </div>
</template>

<script>
  import { useMainStore } from '@/stores/main';

  // const graffiti = () => import( "./graffiti");
  const commentBox = () => import( "./commentBox");
  const proPage = () => import( "../common/proPage");
  import { checkCaptchaWithCache } from '@/utils/captchaUtil';

  export default {
    components: {
      // graffiti,
      commentBox,
      proPage
    },
    props: {
      source: {
        type: Number
      },
      type: {
        type: String
      },
      userId: {
        type: Number
      }
    },
    data() {
      return {
        isGraffiti: false,
        total: 0,
        replyDialogVisible: false,
        floorComment: {},
        replyComment: {},
        comments: [],
        pagination: {
          current: 1,
          size: 15, // 一级评论每页显示数量，适当增加
          total: 0,
          source: this.source,
          commentType: this.type,
          floorCommentId: null
        },
        // 折叠显示相关状态
        expandedComments: {}, // 记录每个一级评论的展开状态 {commentId: {expanded: boolean, displayCount: number}}
        pageSize: 10, // 每次展开显示的回复数量
        // 🔧 新增：懒加载相关状态
        isLoadingMore: false, // 是否正在加载更多评论
        hasMoreComments: true, // 是否还有更多评论
        enableLazyLoad: true, // 是否启用懒加载模式
        scrollThreshold: 200, // 距离底部多少像素时触发加载
        scrollTimer: null // 滚动防抖定时器
      };
    },

    computed: {
      mainStore() {
        return useMainStore();
      },},

    created() {
      // 🔧 关键修复：强制重置组件状态
      this.expandedComments = {};
      this.comments = [];
      this.isLoadingMore = false;
      this.hasMoreComments = true;

      this.getComments(this.pagination);
      this.getTotal();
    },

    mounted() {
      // 🔧 添加滚动监听
      if (this.enableLazyLoad) {
        this.addScrollListener();
      }

      // 🔧 新策略：监听页面状态恢复事件
      this.$bus.$on('restore-page-state', this.handleRestorePageState);
    },

    beforeDestroy() {
      // 🔧 移除滚动监听
      if (this.enableLazyLoad) {
        this.removeScrollListener();
      }
      // 清理定时器
      if (this.scrollTimer) {
        clearTimeout(this.scrollTimer);
      }
      // 🔧 新策略：移除页面状态恢复事件监听
      this.$bus.$off('restore-page-state', this.handleRestorePageState);
    },
    methods: {
      toPage(page) {
        this.pagination.current = page;
        window.scrollTo({
          top: document.getElementById('comment-content').offsetTop
        });
        this.getComments(this.pagination);
      },
      getTotal() {
        this.$http.get(this.$constant.baseURL + "/comment/getCommentCount", {source: this.source, type: this.type})
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.total = res.data;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      toChildPage(floorComment) {
        if (!floorComment.childComments.current) {
          floorComment.childComments.current = 1;
        }
        floorComment.childComments.current += 1;
        let pagination = {
          current: floorComment.childComments.current,
          size: 5,
          total: 0,
          source: this.source,
          commentType: this.type,
          floorCommentId: floorComment.id
        }
        this.getComments(pagination, floorComment, true);
      },

      // 折叠/展开相关方法
      // 🔧 移除旧的展开状态检查方法，改用comment.expanded属性

      // 🔧 移除旧的展开/折叠方法，改用新的懒加载机制

      /**
       * 加载更多回复
       */
      loadMoreReplies(comment) {
        const currentState = this.expandedComments[comment.id];
        const newDisplayCount = Math.min(
          currentState.displayCount + this.pageSize,
          comment.flatReplies.length
        );

        // 如果当前显示的回复数量已经等于已加载的回复数量，且还有更多回复，则需要从服务器加载
        if (currentState.displayCount >= comment.flatReplies.length &&
            comment.flatReplies.length < (comment.totalReplies || comment.childComments.total)) {
          this.loadMoreRepliesFromServer(comment);
        } else {
          // 直接显示更多已加载的回复
          this.$set(this.expandedComments, comment.id, {
            expanded: true,
            displayCount: newDisplayCount
          });
        }
      },

      /**
       * 从服务器加载更多回复数据
       */
      loadMoreRepliesFromServer(comment) {
        if (!comment.childComments.current) {
          comment.childComments.current = 1;
        }
        comment.childComments.current += 1;

        let pagination = {
          current: comment.childComments.current,
          size: this.pageSize,
          total: 0,
          source: this.source,
          commentType: this.type,
          floorCommentId: comment.id
        }

        this.getComments(pagination, comment, true);
      },

      /**
       * 加载回复数据
       */
      loadRepliesData(comment) {
        let pagination = {
          current: 1,
          size: this.pageSize,
          total: 0,
          source: this.source,
          commentType: this.type,
          floorCommentId: comment.id
        }

        this.getComments(pagination, comment, false);
      },

      /**
       * 获取当前应该显示的回复列表
       */
      getDisplayedReplies(comment) {
        if (!comment.flatReplies) return [];

        const expandState = this.expandedComments[comment.id];
        if (!expandState) {
          // 🔧 修复：如果没有展开状态，返回所有平铺回复（用于调试）
          return comment.flatReplies;
        }

        const displayCount = expandState.displayCount || comment.flatReplies.length;
        const result = comment.flatReplies.slice(0, displayCount);
        return result;
      },

      /**
       * 检查是否还有更多回复可以展开
       */
      hasMoreReplies(comment) {
        const expandState = this.expandedComments[comment.id];
        if (!expandState || !expandState.expanded) return false;

        const totalReplies = comment.totalReplies || (comment.childComments ? comment.childComments.total : 0);
        const currentDisplayCount = expandState.displayCount || 0;
        const loadedRepliesCount = comment.flatReplies ? comment.flatReplies.length : 0;

        // 检查是否还有更多回复需要显示
        // 条件1：当前显示数量小于总回复数量
        // 条件2：当前显示数量小于已加载的回复数量（有缓存的回复未显示）
        return currentDisplayCount < totalReplies || currentDisplayCount < loadedRepliesCount;
      },

      emoji(comments, flag) {
        comments.forEach(c => {
          c.commentContent = c.commentContent.replace(/\n/g, '<br/>');
          c.commentContent = this.$common.faceReg(c.commentContent);
          c.commentContent = this.$common.pictureReg(c.commentContent);
          if (flag) {
            if (!this.$common.isEmpty(c.childComments) && !this.$common.isEmpty(c.childComments.records)) {
              c.childComments.records.forEach(cc => {
                c.commentContent = c.commentContent.replace(/\n/g, '<br/>');
                cc.commentContent = this.$common.faceReg(cc.commentContent);
                cc.commentContent = this.$common.pictureReg(cc.commentContent);
              });
            }
          }
        });
      },

      /**
       * 计算评论的直接回复数量
       * @param {Object} comment - 评论对象
       * @param {Array} allReplies - 所有回复列表
       * @returns {Number} - 直接回复数量
       */
      calculateDirectReplyCount(comment, allReplies) {
        if (!comment || !allReplies || !allReplies.length) {
          return 0;
        }

        // 只统计parentCommentId等于当前评论id的直接回复
        return allReplies.filter(reply => reply.parentCommentId === comment.id).length;
      },

      /**
       * 🔧 新方法：处理主评论数据，只处理统计信息，不平铺子评论
       * @param {Array} comments - 主评论列表
       */
      processMainComments(comments) {
        if (!comments || !comments.length) return;

        comments.forEach((comment, index) => {

          // 🔧 新逻辑：只处理子评论统计信息，不加载子评论内容
          if (comment.childComments && comment.childComments.total > 0) {

            // 初始化懒加载状态
            comment.expanded = false;
            comment.loadingReplies = false;
            comment.currentPage = 1;
            comment.hasMoreReplies = comment.childComments.total > 10; // 假设每页10条

            // 确保childComments.records为空数组（懒加载模式）
            if (!comment.childComments.records) {
              comment.childComments.records = [];
            }
          } else {
            comment.expanded = false;
            comment.loadingReplies = false;
            comment.hasMoreReplies = false;

            if (!comment.childComments) {
              comment.childComments = {
                records: [],
                total: 0
              };
            }
          }
        });
      },

      /**
       * 展开子评论（懒加载）
       * @param {Object} comment - 主评论对象
       */
      async expandReplies(comment) {
        if (comment.loadingReplies) return;

        comment.loadingReplies = true;

        try {
          const baseUrl = this.$constant.baseURL + "/comment/listChildComments";
          const urlParams = new URLSearchParams({
            parentCommentId: comment.id.toString(),
            current: '1',
            size: '10'
          });
          const fullUrl = `${baseUrl}?${urlParams.toString()}`;

          const requestBody = {
            source: this.source,
            commentType: this.type
          };

          const response = await this.$http.post(fullUrl, requestBody);

          let childCommentsData = null;
          if (response.data && response.data.data && response.data.data.records) {
            childCommentsData = response.data.data;
          } else if (response.data && response.data.records) {
            childCommentsData = response.data;
          }

          if (childCommentsData && childCommentsData.records) {
            this.$set(comment.childComments, 'records', childCommentsData.records);
            this.$set(comment, 'expanded', true);
            this.$set(comment, 'currentPage', 1);
            this.$set(comment, 'hasMoreReplies', childCommentsData.records.length < childCommentsData.total);
            this.$forceUpdate();
          } else {
            this.$message({
              type: 'error',
              message: '数据格式错误，请重试'
            });
          }
        } catch (error) {
          let errorMessage = '加载回复失败，请重试';
          if (error.response && error.response.data && error.response.data.message) {
            errorMessage = `加载失败: ${error.response.data.message}`;
          }

          this.$message({
            type: 'error',
            message: errorMessage
          });
        } finally {
          comment.loadingReplies = false;
        }
      },

      /**
       * 加载更多子评论
       * @param {Object} comment - 主评论对象
       */
      async loadMoreReplies(comment) {
        if (comment.loadingReplies) return;

        comment.loadingReplies = true;

        try {
          const baseUrl = this.$constant.baseURL + "/comment/listChildComments";
          const urlParams = new URLSearchParams({
            parentCommentId: comment.id.toString(),
            current: (comment.currentPage + 1).toString(),
            size: '10'
          });
          const fullUrl = `${baseUrl}?${urlParams.toString()}`;

          const requestBody = {
            source: this.source,
            commentType: this.type
          };

          const response = await this.$http.post(fullUrl, requestBody);

          let childCommentsData = null;
          if (response.data && response.data.data && response.data.data.records) {
            childCommentsData = response.data.data;
          } else if (response.data && response.data.records) {
            childCommentsData = response.data;
          }

          if (childCommentsData && childCommentsData.records) {
            const newRecords = [...comment.childComments.records, ...childCommentsData.records];
            this.$set(comment.childComments, 'records', newRecords);
            this.$set(comment, 'currentPage', comment.currentPage + 1);

            const totalLoaded = newRecords.length;
            this.$set(comment, 'hasMoreReplies', totalLoaded < comment.childComments.total);
            this.$forceUpdate();
          } else {
            this.$message({
              type: 'error',
              message: '加载更多数据格式错误'
            });
          }
        } catch (error) {
          let errorMessage = '加载更多回复失败，请重试';
          if (error.response && error.response.data && error.response.data.message) {
            errorMessage = `加载更多失败: ${error.response.data.message}`;
          }

          this.$message({
            type: 'error',
            message: errorMessage
          });
        } finally {
          comment.loadingReplies = false;
        }
      },

      /**
       * 收起子评论
       * @param {Object} comment - 主评论对象
       */
      collapseReplies(comment) {
        this.$set(comment, 'expanded', false);
        this.$forceUpdate();
      },

      /**
       * 判断是否应该显示回复指示器
       * 只在嵌套回复时显示，直接回复主评论时隐藏
       * @param {Object} replyItem - 子评论对象
       * @param {Object} mainComment - 主评论对象
       * @return {Boolean} 是否显示回复指示器
       */
      shouldShowReplyIndicator(replyItem, mainComment) {
        if (!replyItem.parentUsername) {
          return false;
        }

        // 如果是直接回复主评论，隐藏指示器
        if (replyItem.parentCommentId === mainComment.id) {
          return false;
        }

        // 如果是嵌套回复，显示指示器
        return true;
      },

      /**
       * 递归提取所有嵌套评论的ID（用于数据分析）
       */
      extractNestedCommentIds(comment) {
        const ids = [];
        if (!comment.childComments || !comment.childComments.records) {
          return ids;
        }

        comment.childComments.records.forEach(child => {
          ids.push(child.id);
          ids.push(...this.extractNestedCommentIds(child));
        });

        return ids;
      },

      /**
       * 递归计算嵌套评论总数
       */
      countNestedComments(comment) {
        if (!comment.childComments) {
          return 0;
        }

        return comment.childComments.total || 0;
      },
      getComments(pagination, floorComment = {}, isToPage = false, isLazyLoad = false) {
        if (this.$common.isEmpty(floorComment)) {
          // 🔧 懒加载模式：不清空已有评论，而是追加
          if (!isLazyLoad) {
            this.expandedComments = {};
            this.comments = [];
          }
        }

        this.$http.post(this.$constant.baseURL + "/comment/listComment", pagination)
          .then((res) => {
            if (!this.$common.isEmpty(res.data) && !this.$common.isEmpty(res.data.records)) {
              if (this.$common.isEmpty(floorComment)) {
                // 🔧 懒加载模式处理
                if (isLazyLoad) {
                  // 追加新评论到现有列表
                  this.comments = this.comments.concat(res.data.records);
                  // 更新懒加载状态
                  this.hasMoreComments = res.data.records.length === pagination.size;
                  this.isLoadingMore = false;
                } else {
                  // 初始加载或传统分页模式
                  this.comments = res.data.records;
                  this.hasMoreComments = res.data.records.length === pagination.size;
                  // 非懒加载模式下也要重置isLoadingMore状态
                  this.isLoadingMore = false;
                }
                pagination.total = res.data.total;

                this.processMainComments(isLazyLoad ? res.data.records : this.comments);
                this.emoji(isLazyLoad ? res.data.records : this.comments, true);
              } else {
                if (isToPage === false) {
                  const newReplies = res.data.records;
                  newReplies.sort((a, b) => new Date(a.createTime) - new Date(b.createTime));

                  floorComment.flatReplies = newReplies;
                  floorComment.totalReplies = res.data.total;
                  floorComment.childComments = {
                    records: [],
                    total: res.data.total
                  };

                  this.$set(this.expandedComments, floorComment.id, {
                    expanded: true,
                    displayCount: Math.min(this.pageSize, newReplies.length)
                  });
                } else {
                  const newReplies = res.data.records;

                  floorComment.flatReplies = floorComment.flatReplies.concat(newReplies);
                  floorComment.flatReplies.sort((a, b) => new Date(a.createTime) - new Date(b.createTime));
                  floorComment.totalReplies = res.data.total; // 使用服务器返回的总数
                  floorComment.childComments.total = res.data.total;

                  // 更新展开状态，显示更多回复
                  const currentState = this.expandedComments[floorComment.id];
                  this.$set(this.expandedComments, floorComment.id, {
                    expanded: true,
                    displayCount: Math.min(currentState.displayCount + this.pageSize, floorComment.flatReplies.length)
                  });
                }
                this.emoji(floorComment.flatReplies, false);
              }
              this.$nextTick(() => {
                this.$common.imgShow("#comment-content .pictureReg");
              });
            } else {
              // 即使没有评论数据，也要重置isLoadingMore状态
              if (this.$common.isEmpty(floorComment)) {
                this.isLoadingMore = false;
                this.hasMoreComments = false;
              }
            }
          })
          .catch((error) => {
            // 🔧 懒加载错误处理
            if (isLazyLoad) {
              this.isLoadingMore = false;
              this.pagination.current -= 1; // 回退页码
              this.$message({
                message: '加载更多评论失败：' + error.message,
                type: "error"
              });
            } else {
              this.$message({
                message: error.message,
                type: "error"
              });
            }
          });
      },
      addGraffitiComment(graffitiComment) {
        this.submitComment(graffitiComment);
      },
      submitComment(commentContent) {
        let comment = {
          source: this.source,
          type: this.type,
          commentContent: commentContent
        };

        // 保存评论内容到内存中，以便验证码取消时恢复
        this.pendingCommentContent = commentContent;

        // 检查是否需要验证码
        checkCaptchaWithCache('comment').then(required => {
          if (required) {
            // 需要验证码：立即清空评论框，显示验证码组件
            this.clearCommentBox();

            this.mainStore.setVerifyParams({
              action: 'comment',
              isReplyComment: false,  // 主评论
              onSuccess: (token) => this.saveCommentToServer(comment, token),
              onCancel: () => this.restorePendingComment()
            });
            this.mainStore.showCaptcha(true);
          } else {
            // 不需要验证码，直接发表评论并清空评论框
            this.clearCommentBox();
            this.saveCommentToServer(comment);
          }
        });
      },
      
      // 将评论保存到服务器
      saveCommentToServer(comment, verificationToken) {
        // 如果有验证token，添加到请求中
        if (verificationToken) {
          comment.verificationToken = verificationToken;
        }
        
        this.$http.post(this.$constant.baseURL + "/comment/saveComment", comment)
          .then((res) => {
            this.$message({
              type: 'success',
              message: '保存成功！'
            });

            // 🔧 修复：评论提交成功后，确保评论框被清空
            this.pendingCommentContent = null;
            this.clearCommentBox();

            // 重置懒加载状态，防止显示"正在加载更多评论..."
            this.isLoadingMore = false;
            this.hasMoreComments = true; // 重置为true，等待getComments更新

            this.pagination = {
              current: 1,
              size: 10,
              total: 0,
              source: this.source,
              commentType: this.type,
              floorCommentId: null
            }
            this.getComments(this.pagination);
            this.getTotal();
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });

            // 评论提交失败时，恢复评论内容
            this.restorePendingComment();
          });
      },
      submitReply(commentContent) {
        // 🔧 简化：此时用户必须已登录（因为未登录用户不会看到回复对话框）
        let comment = {
          source: this.source,
          type: this.type,
          floorCommentId: this.floorComment.id,
          commentContent: commentContent,
          parentCommentId: this.replyComment.id,
          parentUserId: this.replyComment.userId
        };

        let floorComment = this.floorComment;

        // 保存回复内容和对话框状态，以便验证码取消时恢复
        this.pendingReplyContent = {
          content: commentContent,
          floorComment: { ...floorComment },  // 深拷贝避免引用问题
          replyComment: { ...this.replyComment }  // 深拷贝避免引用问题
        };

        // 检查是否需要验证码
        checkCaptchaWithCache('comment').then(required => {
          if (required) {
            // 需要验证码：先关闭回复对话框，显示验证码组件
            this.handleClose();
            this.mainStore.setVerifyParams({
              action: 'comment',
              isReplyComment: true,  // 回复评论
              onSuccess: (token) => this.saveReplyToServer(comment, floorComment, token),
              onCancel: () => this.restorePendingReply()
            });
            this.mainStore.showCaptcha(true);
          } else {
            // 不需要验证码，直接发表回复并关闭对话框
            this.saveReplyToServer(comment, floorComment);
            this.handleClose();
            // 清除待恢复的回复内容
            this.pendingReplyContent = null;
          }
        });
      },
      
      // 将回复保存到服务器
      saveReplyToServer(comment, floorComment, verificationToken) {
        // 如果有验证token，添加到请求中
        if (verificationToken) {
          comment.verificationToken = verificationToken;
        }

        this.$http.post(this.$constant.baseURL + "/comment/saveComment", comment)
          .then((res) => {
            this.$message({
              type: 'success',
              message: '回复成功！'
            });

            // 🔧 修复：回复提交成功后，确保对话框关闭
            this.pendingReplyContent = null;
            this.handleClose();

            // 根据评论类型选择合适的刷新策略

            if (comment.parentCommentId === floorComment.id) {
              // 二级评论：直接回复一级评论，刷新楼层评论
              let pagination = {
                current: 1,
                size: 5,
                total: 0,
                source: this.source,
                commentType: this.type,
                floorCommentId: floorComment.id
              }
              this.getComments(pagination, floorComment);
            } else {
              // 三级及以上评论：回复的是子评论，需要使用懒加载接口刷新
              this.refreshNestedReplies(floorComment);
            }

            // 更新总评论数
            this.getTotal();
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });

            // 回复提交失败时，恢复回复内容
            this.restorePendingReply();
          });
      },

      /**
       * 🔧 新方法：刷新嵌套回复（用于三级评论提交后的显示更新）
       * @param {Object} floorComment - 楼层评论对象
       */
      async refreshNestedReplies(floorComment) {

        // 🔧 添加楼层评论对象验证
        if (!floorComment || !floorComment.id) {
          console.error('楼层评论对象无效:', floorComment);
          return;
        }

        try {
          // 使用懒加载接口重新获取所有子评论
          const baseUrl = this.$constant.baseURL + "/comment/listChildComments";
          const urlParams = new URLSearchParams({
            parentCommentId: floorComment.id.toString(),
            current: '1',
            size: '50' // 获取更多评论确保新评论能显示
          });
          const fullUrl = `${baseUrl}?${urlParams.toString()}`;

          const requestBody = {
            source: this.source,
            commentType: this.type
          };

          const response = await this.$http.post(fullUrl, requestBody);

          let childCommentsData = null;
          if (response.data && response.data.data && response.data.data.records) {
            childCommentsData = response.data.data;
          } else if (response.data && response.data.records) {
            childCommentsData = response.data;
          }

          if (childCommentsData && childCommentsData.records) {

            // 🔧 确保楼层评论有childComments属性
            if (!floorComment.childComments) {
              this.$set(floorComment, 'childComments', { records: [], total: 0 });
            }

            // 更新楼层评论的子评论数据
            this.$set(floorComment.childComments, 'records', childCommentsData.records);
            this.$set(floorComment.childComments, 'total', childCommentsData.total);
            this.$set(floorComment, 'expanded', true);
            this.$set(floorComment, 'currentPage', 1);
            this.$set(floorComment, 'hasMoreReplies', childCommentsData.records.length < childCommentsData.total);

            // 强制更新视图
            this.$forceUpdate();

          } else {
          }
        } catch (error) {
          console.error('刷新嵌套回复失败:', error);
          // 如果懒加载失败，回退到传统方式
          let pagination = {
            current: 1,
            size: 5,
            total: 0,
            source: this.source,
            commentType: this.type,
            floorCommentId: floorComment.id
          }
          this.getComments(pagination, floorComment);
        }
      },
      replyDialog(comment, floorComment) {
        // 🔧 新策略：检查用户登录状态
        if (this.$common.isEmpty(this.mainStore.currentUser)) {
          // 未登录用户：保存页面状态并直接跳转到登录页面
          this.savePageStateAndRedirectToLogin(comment, floorComment);
          return;
        }

        // 已登录用户：正常打开回复对话框
        this.replyComment = comment;
        this.floorComment = floorComment;
        this.replyDialogVisible = true;
      },

      /**
       * 🔧 新方法：保存页面状态并跳转到登录页面
       * @param {Object} comment - 被回复的评论对象
       * @param {Object} floorComment - 楼层评论对象
       */
      savePageStateAndRedirectToLogin(comment, floorComment) {
        const articleId = this.$route.params.id;

        // 保存页面状态到localStorage
        const pageState = {
          timestamp: Date.now(),
          articleUrl: window.location.href,
          scrollPosition: window.pageYOffset || document.documentElement.scrollTop,
          // 保存回复上下文
          replyContext: {
            replyComment: {
              id: comment.id,
              userId: comment.userId,
              username: comment.username,
              commentContent: comment.commentContent
            },
            floorComment: {
              id: floorComment.id,
              username: floorComment.username,
              expanded: floorComment.expanded || false
            }
          },
          // 保存当前展开的评论状态
          expandedComments: { ...this.expandedComments }
        };

        localStorage.setItem(`pageState_${articleId}`, JSON.stringify(pageState));

        // 使用统一的登录跳转函数
        this.$common.redirectToLogin(this.$router, {
          extraQuery: { hasReplyAction: 'true' },
          message: '请先登录！'
        }, this);
      },
      handleClose() {
        this.replyDialogVisible = false;
        this.floorComment = {};
        this.replyComment = {};
      },

      // 清空评论框内容
      clearCommentBox() {
        if (this.$refs.commentBox) {
          this.$refs.commentBox.clearComment();
        }
      },

      // 恢复待提交的评论内容（验证码取消时调用）
      restorePendingComment() {
        if (this.pendingCommentContent && this.$refs.commentBox) {
          this.$refs.commentBox.restoreComment(this.pendingCommentContent);
          this.pendingCommentContent = null;
        }
      },

      // 恢复待提交的回复内容（验证码取消时调用）
      restorePendingReply() {
        if (this.pendingReplyContent) {

          // 重新打开回复对话框并恢复状态
          this.replyComment = this.pendingReplyContent.replyComment;
          this.floorComment = this.pendingReplyContent.floorComment;
          this.replyDialogVisible = true;

          // 等待对话框完全打开后，恢复输入框内容
          this.$nextTick(() => {
            setTimeout(() => {
              if (this.$refs.replyCommentBox) {
                this.$refs.replyCommentBox.restoreComment(this.pendingReplyContent.content);
              } else {
              }
              // 清除待恢复的回复内容
              this.pendingReplyContent = null;
            }, 200); // 增加延迟确保组件完全渲染
          });
        } else {
        }
      },

      /**
       * 🔧 新方法：处理登录后的页面状态恢复
       * @param {Object} stateData - 保存的页面状态数据
       */
      handleRestorePageState(stateData) {

        if (!stateData || !stateData.replyContext) {
          return;
        }

        // 恢复展开的评论状态
        if (stateData.expandedComments) {
          this.expandedComments = { ...stateData.expandedComments };
          this.$forceUpdate();
        }

        const context = stateData.replyContext;

        // 🔧 优化：确保楼层评论的展开状态正确恢复
        const targetFloorComment = this.comments.find(c => c.id === context.floorComment.id);
        if (targetFloorComment) {
          if (context.floorComment.expanded && !targetFloorComment.expanded) {
            // 如果原本是展开的但现在未展开，则展开它
            this.expandReplies(targetFloorComment);
          } else if (context.floorComment.expanded && targetFloorComment.expanded) {
            // 如果原本就是展开的且现在也是展开的，确保子评论数据是最新的
            this.refreshNestedReplies(targetFloorComment);
          }
        }

        // 🔧 优化：使用更智能的等待机制确保评论列表完全加载
        const waitForCommentAndOpenDialog = (retryCount = 0) => {
          const maxRetries = 10; // 最多重试10次
          const retryDelay = 300; // 每次重试间隔300ms

          // 🔧 关键修复：从实际的评论列表中找到完整的楼层评论对象
          const actualFloorComment = this.comments.find(c => c.id === context.floorComment.id);

          if (!actualFloorComment) {
            if (retryCount < maxRetries) {
              setTimeout(() => waitForCommentAndOpenDialog(retryCount + 1), retryDelay);
              return;
            } else {
              console.error('无法找到楼层评论，状态恢复失败');
              return;
            }
          }

          // 构造回复对象
          this.replyComment = {
            id: context.replyComment.id,
            userId: context.replyComment.userId,
            username: context.replyComment.username,
            commentContent: context.replyComment.commentContent
          };

          // 🔧 关键修复：使用实际的楼层评论对象，确保包含所有必要的属性和状态
          this.floorComment = actualFloorComment;

          // 打开回复对话框
          this.replyDialogVisible = true;

        };

        // 延迟一点时间确保评论列表已更新，然后开始等待和打开对话框
        setTimeout(() => waitForCommentAndOpenDialog(), 500);
      },

      // 🔧 懒加载相关方法
      /**
       * 加载更多一级评论
       */
      loadMoreComments() {
        if (this.isLoadingMore || !this.hasMoreComments) {
          return;
        }

        this.isLoadingMore = true;
        this.pagination.current += 1;

        // 调用getComments，传入isLazyLoad=true
        this.getComments(this.pagination, {}, false, true);
      },

      /**
       * 添加滚动监听
       */
      addScrollListener() {
        window.addEventListener('scroll', this.handleScroll);
      },

      /**
       * 移除滚动监听
       */
      removeScrollListener() {
        window.removeEventListener('scroll', this.handleScroll);
      },

      /**
       * 处理滚动事件（带防抖）
       */
      handleScroll() {
        // 清除之前的定时器
        if (this.scrollTimer) {
          clearTimeout(this.scrollTimer);
        }

        // 设置防抖定时器
        this.scrollTimer = setTimeout(() => {
          if (this.isLoadingMore || !this.hasMoreComments) {
            return;
          }

          const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
          const windowHeight = window.innerHeight;
          const documentHeight = document.documentElement.scrollHeight;

          // 当滚动到距离底部scrollThreshold像素时触发加载
          if (scrollTop + windowHeight >= documentHeight - this.scrollThreshold) {
            this.loadMoreComments();
          }
        }, 100); // 100ms防抖
      }
    }
  }
</script>

<style scoped>

  .comment-head {
    display: flex;
    align-items: center;
    font-size: 20px;
    font-weight: bold;
    margin: 40px 0 20px 0;
    user-select: none;
    color: var(--themeBackground);
  }

  .commentInfo-title {
    margin-bottom: 20px;
    color: var(--greyFont);
    user-select: none;
  }

  #comment-content {
    border-bottom: 1px solid rgba(0, 0, 0, 0.1);
    margin-bottom: 20px;
  }

  .commentInfo-detail {
    display: flex;
  }

  .commentInfo-avatar {
    border-radius: 5px;
  }

  .commentInfo-username {
    color: var(--orangeRed);
    font-size: 16px;
    font-weight: 600;
    margin-right: 5px;
  }

  .commentInfo-username-small {
    color: var(--orangeRed);
    font-size: 14px;
    font-weight: 600;
    margin-right: 5px;
  }

  .commentInfo-master {
    color: var(--green);
    border: 1px solid var(--green);
    border-radius: 0.2rem;
    font-size: 12px;
    padding: 2px 4px;
    margin-right: 5px;
  }

  .commentInfo-location {
    color: var(--greyFont);
    font-size: 12px;
    background: var(--lightGray);
    border-radius: 0.2rem;
    padding: 2px 6px;
    margin-right: 5px;
    user-select: none;
  }

  .commentInfo-location-small {
    color: var(--greyFont);
    font-size: 11px;
    background: var(--lightGray);
    border-radius: 0.2rem;
    padding: 1px 4px;
    margin-right: 5px;
    user-select: none;
  }

  .commentInfo-other {
    font-size: 12px;
    color: var(--greyFont);
    user-select: none;
  }

  .commentInfo-reply-indicator {
    font-size: 12px;
    color: var(--blue);
    margin-left: 8px;
    user-select: none;
  }

  .commentInfo-reply {
    font-size: 12px;
    cursor: pointer;
    user-select: none;
    color: var(--white);
    background: var(--themeBackground);
    border-radius: 0.2rem;
    padding: 3px 6px;
  }

  .commentInfo-content {
    margin: 15px 0 25px;
    padding: 18px 20px;
    background: var(--commentContent);
    border-radius: 12px;
    color: var(--black);
    word-break: break-word;
  }

  /* 暗色模式下使用浅灰色背景 */
  .dark-mode .commentInfo-content {
    background: #D4D4D4 !important;
  }

  .pagination-wrap {
    display: flex;
    justify-content: center;
    margin-bottom: 10px;
  }

  .pagination {
    padding: 6px 20px;
    border: 1px solid var(--lightGray);
    border-radius: 3rem;
    color: var(--greyFont);
    user-select: none;
    cursor: pointer;
    text-align: center;
    font-size: 12px;
  }

  .pagination:hover {
    border: 1px solid var(--themeBackground);
    color: var(--themeBackground);
    box-shadow: 0 0 5px var(--themeBackground);
  }

  /* 展开回复按钮样式 - 统一与pagination样式 */
  .pagination-wrap .expand-replies-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 6px 20px !important;
    margin: 0 !important;
    border: 1px solid var(--lightGray) !important;
    border-radius: 3rem !important;
    background: var(--background) !important;
    cursor: pointer;
    user-select: none;
    text-align: center;
    font-size: 12px !important;
    color: var(--greyFont) !important;
    /* 性能优化: 只监听背景和颜色变化，不需要GPU */
    transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
    width: auto;
    min-width: 120px;
  }

  .pagination-wrap .expand-replies-btn:hover {
    border: 1px solid var(--themeBackground) !important;
    color: var(--themeBackground) !important;
    box-shadow: 0 0 5px var(--themeBackground) !important;
    background: var(--background) !important;
  }

  .expand-text {
    font-size: 12px;
    color: inherit;
    margin-right: 5px;
  }

  .expand-icon {
    font-size: 12px;
    color: inherit;
    transition: transform 0.3s ease;
  }

  .pagination-wrap .expand-replies-btn:hover .expand-icon {
    transform: translateY(1px);
  }

  /* 折叠回复按钮样式 - 统一与pagination样式 */
  .pagination-wrap .collapse-replies-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 6px 20px !important;
    margin: 0 !important;
    border: 1px solid var(--lightGray) !important;
    border-radius: 3rem !important;
    background: var(--background) !important;
    cursor: pointer;
    user-select: none;
    text-align: center;
    font-size: 12px !important;
    color: var(--greyFont) !important;
    /* 性能优化: 只监听背景和颜色变化，不需要GPU */
    transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
    width: auto;
    min-width: 120px;
  }

  .pagination-wrap .collapse-replies-btn:hover {
    border: 1px solid var(--orangeRed) !important;
    color: var(--orangeRed) !important;
    box-shadow: 0 0 5px var(--orangeRed) !important;
    background: var(--background) !important;
  }

  .collapse-text {
    font-size: 12px;
    color: inherit;
    margin-right: 4px;
  }

  .collapse-icon {
    font-size: 12px;
    color: inherit;
    transition: transform 0.3s ease;
  }

  .pagination-wrap .collapse-replies-btn:hover .collapse-icon {
    transform: translateY(-1px);
  }

  /* 🔧 懒加载相关样式 */
  .lazy-load-container {
    margin-top: 20px;
    text-align: center;
  }

  .load-more-btn-container {
    margin: 20px 0;
  }

  .load-more-btn {
    padding: 10px 30px !important;
    border: 1px solid var(--lightGray) !important;
    border-radius: 20px !important;
    background: var(--background) !important;
    color: var(--greyFont) !important;
    font-size: 14px !important;
    /* 性能优化: 只监听背景和颜色变化，不需要GPU */
    transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
  }

  .load-more-btn:hover {
    background: var(--lightGray) !important;
    color: var(--fontColor) !important;
    transform: translateY(-1px);
  }

  .loading-container {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    color: var(--greyFont);
    font-size: 14px;
  }

  .loading-container i {
    margin-right: 8px;
    font-size: 16px;
  }

  .no-more-comments {
    padding: 20px;
    color: var(--greyFont);
    font-size: 12px;
    margin-top: 10px;
  }
</style>
