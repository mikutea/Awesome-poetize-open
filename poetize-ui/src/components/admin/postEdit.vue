<template>
  <div>
    <el-tag effect="dark" class="my-tag">
      <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -3px;">
        <path d="M0 0h1024v1024H0V0z" fill="#202425" opacity=".01"></path>
        <path
          d="M682.666667 204.8h238.933333a34.133333 34.133333 0 0 1 34.133333 34.133333v648.533334a68.266667 68.266667 0 0 1-68.266666 68.266666h-204.8V204.8z"
          fill="#FFAA44"></path>
        <path
          d="M68.266667 921.6a34.133333 34.133333 0 0 0 34.133333 34.133333h785.066667a68.266667 68.266667 0 0 1-68.266667-68.266666V102.4a34.133333 34.133333 0 0 0-34.133333-34.133333H102.4a34.133333 34.133333 0 0 0-34.133333 34.133333v819.2z"
          fill="#11AA66"></path>
        <path
          d="M238.933333 307.2a34.133333 34.133333 0 0 0 0 68.266667h136.533334a34.133333 34.133333 0 1 0 0-68.266667H238.933333z m0 204.8a34.133333 34.133333 0 1 0 0 68.266667h409.6a34.133333 34.133333 0 1 0 0-68.266667H238.933333z m0 204.8a34.133333 34.133333 0 1 0 0 68.266667h204.8a34.133333 34.133333 0 1 0 0-68.266667H238.933333z"
          fill="#FFFFFF"></path>
      </svg>
      文章信息
    </el-tag>
    <el-form :model="article" :rules="rules" ref="ruleForm" label-width="150px"
             class="demo-ruleForm">
      <el-form-item label="标题" prop="articleTitle">
        <el-input v-model="article.articleTitle" maxlength="500" show-word-limit></el-input>
      </el-form-item>

      <el-form-item label="视频链接" prop="videoUrl">
        <el-input maxlength="1000" v-model="article.videoUrl"></el-input>
      </el-form-item>

      <el-form-item label="内容" prop="articleContent">
        <mavon-editor ref="md" @imgAdd="imgAdd" v-model="article.articleContent"/>
      </el-form-item>

      <!-- 翻译编辑按钮和跳过开关 -->
      <el-form-item>
        <div style="display: flex; align-items: center; gap: 20px;">
          <div>
            <el-button type="info" icon="el-icon-edit" @click="openTranslationEditor">编辑翻译</el-button>
            <span style="margin-left: 10px; color: #909399; font-size: 12px;">
              编辑文章的翻译版本
            </span>
          </div>

          <div style="display: flex; align-items: center;">
            <el-switch
              v-model="skipAiTranslation"
              active-text="跳过AI自动翻译"
              inactive-text="启用AI自动翻译"
              :active-color="skipAiTranslation ? '#F56C6C' : '#13CE66'"
              style="margin-right: 10px;">
            </el-switch>
            <el-tooltip content="开启后保存文章时不会执行AI自动翻译" placement="top">
              <i class="el-icon-question" style="color: #909399; cursor: help;"></i>
            </el-tooltip>
          </div>
        </div>

        <!-- 暂存翻译提示 -->
        <div v-if="hasPendingTranslation" style="margin-top: 8px;">
          <el-tag type="warning" size="mini">
            <i class="el-icon-edit"></i>
            有未保存的翻译内容 ({{ getLanguageName(pendingTranslation.language) }})
          </el-tag>
        </div>
      </el-form-item>

      <el-form-item label="是否启用评论" prop="commentStatus">
        <el-tag :type="article.commentStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.commentStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.commentStatus"></el-switch>
      </el-form-item>

      <el-form-item label="是否推荐" prop="recommendStatus">
        <el-tag :type="article.recommendStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.recommendStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.recommendStatus"></el-switch>
      </el-form-item>

      <el-form-item label="是否可见" prop="viewStatus">
        <el-tag :type="article.viewStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.viewStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.viewStatus"></el-switch>
      </el-form-item>

      <el-form-item label="推送至搜索引擎" prop="submitToSearchEngine">
        <el-tag :type="article.submitToSearchEngine === false ? 'info' : 'success'"
                disable-transitions>
          {{article.submitToSearchEngine === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.submitToSearchEngine"></el-switch>
        <div class="tip-text">
          <i class="el-icon-info"></i> 
          是否在保存后自动推送文章到搜索引擎（百度、谷歌等）以便提高收录速度
        </div>
      </el-form-item>

      <el-form-item v-if="article.viewStatus === false" label="不可见时的访问密码" prop="password">
        <el-input maxlength="30" v-model="article.password"></el-input>
      </el-form-item>

      <el-form-item v-if="article.viewStatus === false" label="密码提示" prop="tips">
        <el-input maxlength="60" v-model="article.tips"></el-input>
      </el-form-item>

      <el-form-item label="封面" prop="articleCover">
        <div style="display: flex">
          <el-input v-model="article.articleCover"></el-input>
          <el-image class="table-td-thumb"
                    lazy
                    style="margin-left: 10px"
                    :preview-src-list="[article.articleCover]"
                    :src="article.articleCover"
                    fit="cover"></el-image>
        </div>
        <uploadPicture :isAdmin="true" :prefix="'articleCover'" style="margin-top: 10px" @addPicture="addArticleCover"
                       :maxSize="2"
                       :maxNumber="1"></uploadPicture>
      </el-form-item>
      <el-form-item label="分类" prop="sortId">
        <el-select v-model="article.sortId" placeholder="请选择分类" @change="handleSortChange">
          <el-option
            v-for="item in sorts"
            :key="item.id"
            :label="item.sortName"
            :value="item.id">
          </el-option>
          <el-option
            key="new-sort"
            label="+ 新建分类"
            value="new-sort"
            style="color: #409EFF; font-weight: bold;">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="标签" prop="labelId">
        <el-select v-model="article.labelId" placeholder="请选择标签" @change="handleLabelChange">
          <el-option
            v-for="item in labelsTemp"
            :key="item.id"
            :label="item.labelName"
            :value="item.id">
          </el-option>
          <el-option
            v-if="article.sortId && article.sortId !== 'new-sort'"
            key="new-label"
            label="+ 新建标签"
            value="new-label"
            style="color: #409EFF; font-weight: bold;">
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <div class="myCenter" style="margin-bottom: 22px">
      <el-button type="primary" @click="submitForm('ruleForm')">保存并等待</el-button>
      <el-button type="success" @click="submitFormAsync('ruleForm')" :loading="asyncSaveLoading">
        <i class="el-icon-loading" v-if="asyncSaveLoading"></i>
        <i class="el-icon-check" v-else></i>
        保存并离开
      </el-button>
      <el-button type="danger" @click="resetForm('ruleForm')">重置所有修改</el-button>
    </div>

    <!-- 新建分类对话框 -->
    <el-dialog title="新建分类" :visible.sync="newSortDialog" width="500px" :close-on-click-modal="false">
      <el-form ref="newSortForm" :model="newSortForm" :rules="newSortRules" label-width="100px">
        <el-form-item label="分类名称" prop="sortName">
          <el-input v-model="newSortForm.sortName" placeholder="请输入分类名称" maxlength="32" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="分类描述" prop="sortDescription">
          <el-input v-model="newSortForm.sortDescription" placeholder="请输入分类描述" maxlength="256" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="newSortForm.priority" :min="1" :max="999" placeholder="数字越小越靠前"></el-input-number>
          <div class="tip-text">
            <i class="el-icon-info"></i> 
            数字越小的分类在前端显示时越靠前
          </div>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="cancelNewSort">取 消</el-button>
        <el-button type="primary" @click="createNewSort" :loading="newSortLoading">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 新建标签对话框 -->
    <el-dialog title="新建标签" :visible.sync="newLabelDialog" width="500px" :close-on-click-modal="false">
      <el-form ref="newLabelForm" :model="newLabelForm" :rules="newLabelRules" label-width="100px">
        <el-form-item label="所属分类">
          <el-input :value="getCurrentSortName()" disabled></el-input>
        </el-form-item>
        <el-form-item label="标签名称" prop="labelName">
          <el-input v-model="newLabelForm.labelName" placeholder="请输入标签名称" maxlength="32" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="标签描述" prop="labelDescription">
          <el-input v-model="newLabelForm.labelDescription" placeholder="请输入标签描述" maxlength="256" show-word-limit></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="cancelNewLabel">取 消</el-button>
        <el-button type="primary" @click="createNewLabel" :loading="newLabelLoading">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 翻译编辑弹窗 -->
    <el-dialog
      title="编辑文章翻译"
      :visible.sync="translationDialogVisible"
      width="80%"
      :close-on-click-modal="false"
      :close-on-press-escape="false">

      <el-form :model="translationForm" ref="translationForm" label-width="120px">
        <!-- 目标语言选择 -->
        <el-form-item label="目标语言">
          <el-select v-model="translationForm.targetLanguage" @change="onTargetLanguageChange">
            <el-option label="English (英文)" value="en"></el-option>
            <el-option label="日本語 (日文)" value="ja"></el-option>
            <el-option label="繁體中文 (繁体中文)" value="zh-TW"></el-option>
            <el-option label="한국어 (韩文)" value="ko"></el-option>
            <el-option label="Français (法文)" value="fr"></el-option>
            <el-option label="Deutsch (德文)" value="de"></el-option>
            <el-option label="Español (西班牙文)" value="es"></el-option>
            <el-option label="Русский (俄文)" value="ru"></el-option>
          </el-select>
          <span style="margin-left: 10px; color: #909399; font-size: 12px;">
            修改后将同时更新系统默认目标语言
          </span>
        </el-form-item>

        <!-- 翻译标题 -->
        <el-form-item label="翻译标题" prop="translatedTitle">
          <el-input
            v-model="translationForm.translatedTitle"
            maxlength="500"
            show-word-limit
            placeholder="请输入翻译后的文章标题">
          </el-input>
        </el-form-item>

        <!-- 翻译内容 -->
        <el-form-item label="翻译内容" prop="translatedContent">
          <mavon-editor
            ref="translationMd"
            v-model="translationForm.translatedContent"
            placeholder="请输入翻译后的文章内容（支持Markdown格式）"/>
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button @click="closeTranslationDialog">取 消</el-button>
        <el-button type="primary" @click="saveTranslation" :loading="translationSaving">
          保 存
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
  const uploadPicture = () => import("../common/uploadPicture");
  import axios from 'axios';

  export default {
    components: {
      uploadPicture
    },
    data() {
      return {
        id: this.$route.query.id,
        loading: null,
        asyncSaveLoading: false,
        currentTaskId: null,
        newSortLoading: false,
        newLabelLoading: false,
        currentStoreType: null, // 添加currentStoreType属性
        article: {
          articleTitle: "",
          articleContent: "",
          commentStatus: true,
          recommendStatus: false,
          viewStatus: true,
          submitToSearchEngine: true,
          password: "",
          tips: "",
          articleCover: "",
          videoUrl: "",
          sortId: null,
          labelId: null
        },
        sorts: [],
        labels: [],
        labelsTemp: [],
        // 新建分类对话框
        newSortDialog: false,
        newSortForm: {
          sortName: '',
          sortDescription: '',
          priority: 1
        },
        newSortRules: {
          sortName: [
            { required: true, message: '请输入分类名称', trigger: 'blur' },
            { max: 32, message: '分类名称长度不能超过32个字符', trigger: 'blur' }
          ],
          sortDescription: [
            { required: true, message: '请输入分类描述', trigger: 'blur' },
            { max: 256, message: '分类描述长度不能超过256个字符', trigger: 'blur' }
          ],
          priority: [
            { required: true, message: '请输入优先级', trigger: 'blur' },
            { type: 'number', message: '优先级必须为数字值', trigger: 'blur' }
          ]
        },
        // 新建标签对话框
        newLabelDialog: false,
        newLabelForm: {
          labelName: '',
          labelDescription: '',
          sortId: null
        },
        newLabelRules: {
          labelName: [
            { required: true, message: '请输入标签名称', trigger: 'blur' },
            { max: 32, message: '标签名称长度不能超过32个字符', trigger: 'blur' }
          ],
          labelDescription: [
            { required: true, message: '请输入标签描述', trigger: 'blur' },
            { max: 256, message: '标签描述长度不能超过256个字符', trigger: 'blur' }
          ]
        },
        // 翻译编辑相关数据
        translationDialogVisible: false,
        translationSaving: false,
        translationForm: {
          targetLanguage: 'en',
          translatedTitle: '',
          translatedContent: ''
        },
        // 跳过AI翻译开关
        skipAiTranslation: false,
        // 暂存的翻译数据
        pendingTranslation: {
          title: '',
          content: '',
          language: ''
        },
        rules: {
          articleTitle: [
            {required: true, message: '请输入标题', trigger: 'change'},
            {max: 500, message: '标题长度不能超过500个字符', trigger: 'change'}
          ],
          articleContent: [
            {required: true, message: '请输入内容', trigger: 'change'}
          ],
          commentStatus: [
            {required: true, message: '是否启用评论', trigger: 'change'}
          ],
          recommendStatus: [
            {required: true, message: '是否推荐', trigger: 'change'}
          ],
          viewStatus: [
            {required: true, message: '是否可见', trigger: 'change'}
          ],
          articleCover: [
            {required: true, message: '封面', trigger: 'change'}
          ],
          sortId: [
            {required: true, message: '分类', trigger: 'change'}
          ],
          labelId: [
            {required: true, message: '标签', trigger: 'blur'}
          ]
        }
      };
    },

    computed: {
      // 检查是否有暂存的翻译数据
      hasPendingTranslation() {
        return this.pendingTranslation.title &&
               this.pendingTranslation.content &&
               this.pendingTranslation.language;
      }
    },

    watch: {
      'article.sortId'(newVal, oldVal) {
        if (oldVal !== null) {
          this.article.labelId = null;
        }
        if (!this.$common.isEmpty(newVal) && !this.$common.isEmpty(this.labels)) {
          this.labelsTemp = this.labels.filter(l => l.sortId === newVal);
        }
      }
    },

    created() {
      this.getSortAndLabel();
      
      // 监听系统配置更新事件
      this.$bus.$on('sysConfigUpdated', this.handleSysConfigUpdate);
    },
    
    beforeDestroy() {
      // 移除事件监听，避免内存泄漏
      this.$bus.$off('sysConfigUpdated', this.handleSysConfigUpdate);
    },



    methods: {
      // 处理系统配置更新事件
      handleSysConfigUpdate(config) {
        console.log("文章编辑器收到系统配置更新事件");
        if (config && config['store.type']) {
          this.currentStoreType = config['store.type'];
          console.log("存储类型已更新为:", this.currentStoreType);
        }
      },
      
      // 图片上传处理
      imgAdd(pos, file) {
        try {
          let suffix = file.name.lastIndexOf('.') !== -1 ? file.name.substring(file.name.lastIndexOf('.')) : "";
          let key = "articlePicture" + "/" + this.$store.state.currentAdmin.username.replace(/[^a-zA-Z]/g, '') 
                    + this.$store.state.currentAdmin.id + new Date().getTime() 
                    + Math.floor(Math.random() * 1000) + suffix;

          // 获取当前存储类型，优先使用更新后的配置
          let storeType = this.currentStoreType || this.$store.state.sysConfig['store.type'] || "local";

          let fd = new FormData();
          fd.append("file", file);
          fd.append("originalName", file.name);
          fd.append("key", key);
          fd.append("relativePath", key);
          fd.append("type", "articlePicture");
          fd.append("storeType", storeType);

          if (storeType === "local") {
            this.saveLocal(pos, fd);
          } else if (storeType === "qiniu") {
            this.saveQiniu(pos, fd);
          } else if (storeType === "lsky") {
            this.saveLsky(pos, fd);
          } else if (storeType === "easyimage") {
            this.saveLsky(pos, fd);
          }
        } catch (error) {
          this.showError("图片上传准备失败", error);
        }
      },
      
      // 本地保存图片
      saveLocal(pos, fd) {
        this.$http.upload(this.$constant.baseURL + "/resource/upload", fd, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.$refs.md.$img2Url(pos, res.data);
            } else {
              this.showError("图片上传失败", "服务器未返回有效的图片URL");
            }
          })
          .catch((error) => {
            this.showError("图片本地上传失败", error);
          });
      },
      
      // 七牛云保存图片
      saveQiniu(pos, fd) {
        this.$http.get(this.$constant.baseURL + "/qiniu/getUpToken", {key: fd.get("key")}, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              fd.append("token", res.data);

              this.$http.uploadQiniu(this.$store.state.sysConfig.qiniuUrl, fd)
                .then((res) => {
                  if (!this.$common.isEmpty(res.key)) {
                    let url = this.$store.state.sysConfig['qiniu.downloadUrl'] + res.key;
                    let file = fd.get("file");
                    this.$common.saveResource(this, "articlePicture", url, file.size, file.type, file.name, "qiniu", true);
                    this.$refs.md.$img2Url(pos, url);
                  } else {
                    this.showError("七牛云上传失败", "未返回有效的图片密钥");
                  }
                })
                .catch((error) => {
                  this.showError("七牛云上传请求失败", error);
                });
            } else {
              this.showError("获取七牛云上传Token失败", "服务器未返回有效的Token");
            }
          })
          .catch((error) => {
            this.showError("获取七牛云上传Token失败", error);
          });
      },
      
      // 兰空图床保存图片
      saveLsky(pos, fd) {
        this.$http.post(this.$constant.baseURL + "/resource/upload", fd, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              // 获取返回的图片URL
              let url = res.data;
              let file = fd.get("file");
              let storeType = fd.get("storeType") || "lsky";
              this.$common.saveResource(this, "articlePicture", url, file.size, file.type, file.name, storeType, true);
              this.$refs.md.$img2Url(pos, url);
            } else {
              this.showError("图床上传失败", "服务器未返回有效的图片URL");
            }
          })
          .catch((error) => {
            this.showError("图床上传失败", error);
          });
      },
      
      // 添加文章封面
      addArticleCover(res) {
        this.article.articleCover = res;
      },
      
      // 获取分类和标签
      getSortAndLabel() {
        this.startLoading("加载分类与标签中...");
        return this.$http.get(this.$constant.baseURL + "/webInfo/listSortAndLabel")
          .then((res) => {
            this.stopLoading();
            if (!this.$common.isEmpty(res.data)) {
              this.sorts = res.data.sorts;
              this.labels = res.data.labels;
              if (!this.$common.isEmpty(this.id)) {
                this.getArticle();
              }
              return res.data;
            } else {
              this.showError("获取分类与标签失败", "服务器返回数据为空");
              throw new Error("服务器返回数据为空");
            }
          })
          .catch((error) => {
            this.stopLoading();
            this.showError("获取分类与标签失败", error);
            throw error;
          });
      },
      
      // 获取文章信息
      getArticle() {
        this.startLoading("加载文章信息中...");
        this.$http.get(this.$constant.baseURL + "/admin/article/getArticleById", {id: this.id}, true)
          .then((res) => {
            this.stopLoading();
            if (!this.$common.isEmpty(res.data)) {
              this.article = res.data;
            } else {
              this.showError("获取文章信息失败", "服务器返回数据为空");
            }
          })
          .catch((error) => {
            this.stopLoading();
            this.showError("获取文章信息失败", error);
          });
      },
      
      // 提交表单（同步版本）
      submitForm(formName) {
        // 表单验证前预检查
        if (this.article.viewStatus === false && this.$common.isEmpty(this.article.password)) {
          this.showError("验证失败", "文章不可见时必须输入密码");
          return;
        }
        
        if (this.article.articleTitle && this.article.articleTitle.length > 500) {
          this.showError("验证失败", "文章标题长度不能超过500个字符");
          return;
        }

        // 正式表单验证
        this.$refs[formName].validate((valid) => {
          if (valid) {
            let url = this.$common.isEmpty(this.id) 
              ? "/article/saveArticle"
              : "/article/updateArticle";
              
            if (!this.$common.isEmpty(this.id)) {
              this.article.id = this.id;
            }
            
            this.saveArticle(this.article, url);
          } else {
            this.showError("验证失败", "请完善必填项");
          }
        });
      },

      // 提交表单（异步版本）
      submitFormAsync(formName) {
        // 表单验证前预检查
        if (this.article.viewStatus === false && this.$common.isEmpty(this.article.password)) {
          this.showError("验证失败", "文章不可见时必须输入密码");
          return;
        }
        
        if (this.article.articleTitle && this.article.articleTitle.length > 500) {
          this.showError("验证失败", "文章标题长度不能超过500个字符");
          return;
        }

        // 正式表单验证
        this.$refs[formName].validate((valid) => {
          if (valid) {
            if (!this.$common.isEmpty(this.id)) {
              this.article.id = this.id;
            }
            
            this.saveArticleAsync(this.article);
          } else {
            this.showError("验证失败", "请完善必填项");
          }
        });
      },
      
      // 重置表单
      resetForm(formName) {
        this.$refs[formName].resetFields();
        if (!this.$common.isEmpty(this.id)) {
          this.getArticle();
        }
      },
      
      // 保存文章
      saveArticle(article, url) {
        this.$confirm('确认保存文章？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          // 显示加载中
          this.startLoading("保存文章中...");
          
          // 记录保存请求数据，便于调试
          console.log('准备保存文章:', JSON.stringify(article, null, 2));
          
          // 准备请求参数
          const params = new URLSearchParams();
          params.append('skipAiTranslation', this.skipAiTranslation);

          // 添加暂存翻译数据
          if (this.hasPendingTranslation) {
            params.append('pendingTranslationTitle', this.pendingTranslation.title);
            params.append('pendingTranslationContent', this.pendingTranslation.content);
            params.append('pendingTranslationLanguage', this.pendingTranslation.language);
          }

          // 发送保存请求
          this.$http.post(this.$constant.baseURL + url + '?' + params.toString(), article, true)
            .then(res => {
              this.stopLoading();
              
              // 记录完整响应用于调试
              console.log('文章保存响应:', JSON.stringify(res, null, 2));
              
              // 检查保存是否成功
              if (res.code === 200 || res.data === true) {
                // 显示成功通知
                this.$message({
                  message: '文章保存成功，翻译将在后台自动完成',
                  type: 'success',
                  duration: 3000,
                  offset: 20
                });
                
                // SEO推送提示（现在由后端异步处理）
                if (article.viewStatus && article.submitToSearchEngine) {
                  this.$message({
                    message: '文章保存成功，搜索引擎推送将在后台自动处理',
                    type: 'info',
                    duration: 3000,
                    offset: 80
                  });
                }
                
                // 清空暂存翻译数据
                this.clearPendingTranslation();

                // 快速跳转，无需等待
                setTimeout(() => {
                  this.$router.push({path: "/postList"});
                }, 800);
              } else {
                // 处理保存失败的情况
                this.handleSaveError(res);
              }
            })
            .catch(error => {
              this.stopLoading();
              console.error('保存文章网络请求失败:', error);
              
              this.showError("保存失败", error.message || "网络请求错误");
            });
        }).catch(() => {
          // 用户取消保存，无需任何操作
        });
      },
      
      // 异步保存文章
      saveArticleAsync(article) {
        this.$confirm('文章将在后台保存，您可以立即返回文章列表，保存状态会显示在右侧通知中。', '确认异步保存', {
          confirmButtonText: '保存并离开',
          cancelButtonText: '取消',
          type: 'info',
          center: true
        }).then(() => {
          this.asyncSaveLoading = true;
          
          // 记录保存请求数据
          console.log('准备异步保存文章:', JSON.stringify(article, null, 2));
          
          // 根据是否有id选择不同的异步接口
          let url = this.$common.isEmpty(this.id)
            ? "/article/saveArticleAsync"
            : "/article/updateArticleAsync";

          // 准备请求参数
          const params = new URLSearchParams();
          params.append('skipAiTranslation', this.skipAiTranslation);

          // 添加暂存翻译数据
          if (this.hasPendingTranslation) {
            params.append('pendingTranslationTitle', this.pendingTranslation.title);
            params.append('pendingTranslationContent', this.pendingTranslation.content);
            params.append('pendingTranslationLanguage', this.pendingTranslation.language);
          }

          // 发送异步保存请求
          this.$http.post(this.$constant.baseURL + url + '?' + params.toString(), article, true)
            .then(res => {
              this.asyncSaveLoading = false;
              
              // 记录响应
              console.log('异步保存响应:', JSON.stringify(res, null, 2));
              
              if (res.code === 200 && res.data) {
                // 获取任务ID
                this.currentTaskId = res.data;
                console.log('获取到任务ID:', this.currentTaskId);
                
                // 添加通知（会自动启动轮询）
                this.$notify.loading('保存文章', '正在保存文章，请稍候...', this.currentTaskId);

                // 清空暂存翻译数据
                this.clearPendingTranslation();

                // 延迟跳转，确保全局通知组件已接管轮询
                setTimeout(() => {
                  console.log('准备跳转到文章列表页面');
                  this.$router.push({path: "/postList"});
                }, 1000); // 延长到1秒，确保轮询已启动
              } else {
                console.error('异步保存失败:', res);
                this.handleSaveError(res);
              }
            })
            .catch(error => {
              this.asyncSaveLoading = false;
              console.error('异步保存请求失败:', error);
              this.showError("启动异步保存失败", error.message || "网络请求错误");
            });
        }).catch(() => {
          // 用户取消，无需任何操作
        });
      },


      
      // 注意：SEO推送功能已移至后端异步处理，以下方法已不再需要
      
      // 处理保存失败的情况
      handleSaveError(res) {
        // 详细记录错误
        console.error('保存文章失败，响应:', JSON.stringify(res, null, 2));
        
        // 提取错误消息
        let errorMsg = '服务器返回未知错误';
        if (res.message) {
          errorMsg = res.message;
        } else if (typeof res.data === 'string') {
          errorMsg = res.data;
        } else if (res.data && res.data.message) {
          errorMsg = res.data.message;
        }
        
        this.showError("保存失败", errorMsg);
      },
      
      // 辅助方法：显示加载中
      startLoading(text = "加载中...") {
        if (this.loading) {
          this.loading.close();
        }
        this.loading = this.$loading({
          lock: true,
          text: text,
          spinner: 'el-icon-loading',
          background: 'rgba(0, 0, 0, 0.7)'
        });
      },
      
      // 辅助方法：停止加载
      stopLoading() {
        if (this.loading) {
          this.loading.close();
          this.loading = null;
        }
      },
      
      // 辅助方法：显示成功通知
      showSuccess(title, message) {
        this.$message({
          message: message,
          type: 'success',
          offset: 50 // 显示在顶部，首个通知
        });
      },
      
      // 辅助方法：显示错误通知
      showError(title, error) {
        let errorMessage = typeof error === 'string' 
          ? error 
          : (error && error.message ? error.message : '未知错误');
        
        console.error(title + ':', errorMessage);
        
        this.$message({
          message: errorMessage,
          type: 'error',
          offset: 50 // 显示在顶部，首个通知
        });
      },

      // 处理分类选择变化
      handleSortChange(value) {
        if (value === 'new-sort') {
          // 重置分类选择
          this.article.sortId = null;
          // 打开新建分类对话框
          this.openNewSortDialog();
        }
      },

      // 处理标签选择变化
      handleLabelChange(value) {
        if (value === 'new-label') {
          // 重置标签选择
          this.article.labelId = null;
          // 打开新建标签对话框
          this.openNewLabelDialog();
        }
      },

      // 打开新建分类对话框
      openNewSortDialog() {
        this.newSortForm = {
          sortName: '',
          sortDescription: '',
          priority: 1
        };
        this.newSortDialog = true;
        // 清除表单验证
        this.$nextTick(() => {
          if (this.$refs.newSortForm) {
            this.$refs.newSortForm.clearValidate();
          }
        });
      },

      // 取消新建分类
      cancelNewSort() {
        this.newSortDialog = false;
        this.newSortForm = {
          sortName: '',
          sortDescription: '',
          priority: 1
        };
      },

      // 创建新分类
      createNewSort() {
        this.$refs.newSortForm.validate((valid) => {
          if (valid) {
            this.newSortLoading = true;
            
            this.$http.post(this.$constant.baseURL + "/webInfo/saveSort", this.newSortForm, true)
              .then((res) => {
                this.newSortLoading = false;
                if (res.code === 200) {
                  this.$message({
                    message: '分类创建成功',
                    type: 'success'
                  });
                  
                  // 重新获取分类列表
                  this.getSortAndLabel().then(() => {
                    // 自动选中新创建的分类
                    const newSort = this.sorts.find(sort => sort.sortName === this.newSortForm.sortName);
                    if (newSort) {
                      this.article.sortId = newSort.id;
                    }
                  });
                  
                  // 关闭对话框
                  this.cancelNewSort();
                } else {
                  this.showError("创建分类失败", res.message || "未知错误");
                }
              })
              .catch((error) => {
                this.newSortLoading = false;
                this.showError("创建分类失败", error);
              });
          }
        });
      },

      // 打开新建标签对话框
      openNewLabelDialog() {
        if (!this.article.sortId) {
          this.$message({
            message: '请先选择分类',
            type: 'warning'
          });
          return;
        }

        this.newLabelForm = {
          labelName: '',
          labelDescription: '',
          sortId: this.article.sortId
        };
        this.newLabelDialog = true;
        // 清除表单验证
        this.$nextTick(() => {
          if (this.$refs.newLabelForm) {
            this.$refs.newLabelForm.clearValidate();
          }
        });
      },

      // 取消新建标签
      cancelNewLabel() {
        this.newLabelDialog = false;
        this.newLabelForm = {
          labelName: '',
          labelDescription: '',
          sortId: null
        };
      },

      // 创建新标签
      createNewLabel() {
        this.$refs.newLabelForm.validate((valid) => {
          if (valid) {
            this.newLabelLoading = true;
            
            this.$http.post(this.$constant.baseURL + "/webInfo/saveLabel", this.newLabelForm, true)
              .then((res) => {
                this.newLabelLoading = false;
                if (res.code === 200) {
                  this.$message({
                    message: '标签创建成功',
                    type: 'success'
                  });
                  
                  // 重新获取分类和标签列表
                  this.getSortAndLabel().then(() => {
                    // 新增：强制刷新labelsTemp，确保下拉框显示新标签
                    this.labelsTemp = this.labels.filter(l => l.sortId === this.article.sortId);
                    // 自动选中新创建的标签
                    const newLabel = this.labels.find(label => 
                      label.labelName === this.newLabelForm.labelName && 
                      label.sortId === this.newLabelForm.sortId
                    );
                    if (newLabel) {
                      this.article.labelId = newLabel.id;
                    }
                  });
                  
                  // 关闭对话框
                  this.cancelNewLabel();
                } else {
                  this.showError("创建标签失败", res.message || "未知错误");
                }
              })
              .catch((error) => {
                this.newLabelLoading = false;
                this.showError("创建标签失败", error);
              });
          }
        });
      },

      // 获取当前选中分类的名称
      getCurrentSortName() {
        if (!this.article.sortId) return '';
        const sort = this.sorts.find(s => s.id === this.article.sortId);
        return sort ? sort.sortName : '';
      },

      // 翻译编辑相关方法
      async openTranslationEditor() {
        try {
          // 获取默认目标语言配置
          await this.loadDefaultTargetLanguage();

          // 如果是编辑已有文章（有ID），加载现有翻译内容
          if (!this.$common.isEmpty(this.id)) {
            await this.loadExistingTranslation();
          } else {
            // 新文章，以空白状态打开
            this.translationForm.translatedTitle = '';
            this.translationForm.translatedContent = '';
          }

          // 显示弹窗
          this.translationDialogVisible = true;
        } catch (error) {
          console.error('打开翻译编辑器失败:', error);
          this.$message.error('打开翻译编辑器失败: ' + error.message);
        }
      },

      async loadDefaultTargetLanguage() {
        try {
          const response = await this.$http.get(this.$constant.pythonBaseURL + "/api/translation/default-lang");
          if (response.code === 200 && response.data) {
            this.translationForm.targetLanguage = response.data.default_target_lang || 'en';
          }
        } catch (error) {
          console.warn('获取默认目标语言失败，使用默认值:', error);
          this.translationForm.targetLanguage = 'en';
        }
      },

      async loadExistingTranslation() {
        // 确保有文章ID才执行数据库查询
        if (this.$common.isEmpty(this.id)) {
          console.warn('无文章ID，跳过翻译内容加载');
          this.translationForm.translatedTitle = '';
          this.translationForm.translatedContent = '';
          return;
        }

        try {
          const response = await this.$http.get(this.$constant.baseURL + "/article/getTranslation", {
            id: this.id,
            language: this.translationForm.targetLanguage
          });

          if (response.code === 200 && response.data && response.data.status === 'success') {
            this.translationForm.translatedTitle = response.data.title || '';
            this.translationForm.translatedContent = response.data.content || '';
            console.log('成功加载现有翻译内容，语言:', this.translationForm.targetLanguage);
          } else {
            // 该语言没有翻译内容，清空表单
            this.translationForm.translatedTitle = '';
            this.translationForm.translatedContent = '';
            console.log('该语言暂无翻译内容，语言:', this.translationForm.targetLanguage);
          }
        } catch (error) {
          console.warn('加载现有翻译失败:', error);
          // 加载失败，清空表单
          this.translationForm.translatedTitle = '';
          this.translationForm.translatedContent = '';
        }
      },

      async onTargetLanguageChange(newLanguage) {
        try {
          // 更新系统默认目标语言
          await this.updateDefaultTargetLanguage(newLanguage);

          // 只有在文章已保存（有ID）时才加载翻译内容
          if (!this.$common.isEmpty(this.id)) {
            await this.loadExistingTranslation();
          } else {
            // 新文章或无ID，清空翻译表单
            this.translationForm.translatedTitle = '';
            this.translationForm.translatedContent = '';
          }
        } catch (error) {
          console.error('切换目标语言失败:', error);
          this.$message.error('切换目标语言失败: ' + error.message);
        }
      },

      async updateDefaultTargetLanguage(targetLanguage) {
        try {
          const response = await this.$http.post(this.$constant.pythonBaseURL + "/api/translation/config", {
            default_target_lang: targetLanguage
          });

          if (response.code === 200) {
            this.$message.success('默认目标语言已更新为: ' + this.getLanguageName(targetLanguage));
          }
        } catch (error) {
          console.warn('更新默认目标语言失败:', error);
        }
      },

      getLanguageName(langCode) {
        const langMap = {
          'en': 'English (英文)',
          'ja': '日本語 (日文)',
          'zh-TW': '繁體中文 (繁体中文)',
          'ko': '한국어 (韩文)',
          'fr': 'Français (法文)',
          'de': 'Deutsch (德文)',
          'es': 'Español (西班牙文)',
          'ru': 'Русский (俄文)'
        };
        return langMap[langCode] || langCode;
      },

      async saveTranslation() {
        // 验证表单
        if (!this.translationForm.translatedTitle.trim()) {
          this.$message.warning('请输入翻译标题');
          return;
        }

        if (!this.translationForm.translatedContent.trim()) {
          this.$message.warning('请输入翻译内容');
          return;
        }

        // 暂存翻译数据
        this.pendingTranslation = {
          title: this.translationForm.translatedTitle.trim(),
          content: this.translationForm.translatedContent.trim(),
          language: this.translationForm.targetLanguage
        };

        // 自动开启跳过AI翻译开关
        this.skipAiTranslation = true;

        // 显示成功消息
        this.$message.success('翻译内容已暂存，请保存文章以应用翻译');

        // 关闭弹窗
        this.closeTranslationDialog();
      },

      closeTranslationDialog() {
        this.translationDialogVisible = false;
        this.translationForm.translatedTitle = '';
        this.translationForm.translatedContent = '';
      },

      // 清空暂存的翻译数据
      clearPendingTranslation() {
        this.pendingTranslation = {
          title: '',
          content: '',
          language: ''
        };
      }
    }
  }
</script>

<style scoped>
  .my-tag {
    margin-bottom: 20px;
    width: 100%;
    text-align: left;
    background: var(--lightYellow);
    border: none;
    height: 40px;
    line-height: 40px;
    font-size: 16px;
    color: var(--black);
  }

  .table-td-thumb {
    border-radius: 2px;
    width: 40px;
    height: 40px;
  }

  .el-switch {
    margin-left: 10px;
  }

  .tip-text {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    display: inline-block;
  }

  .el-form-item {
    margin-bottom: 40px;
  }
</style>
