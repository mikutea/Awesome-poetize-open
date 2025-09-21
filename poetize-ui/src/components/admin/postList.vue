<template>
  <div>
    <div class="handle-box">
      <el-select v-model="pagination.recommendStatus" placeholder="是否推荐" style="width: 120px" class="mrb10">
        <el-option key="1" label="是" :value="true"></el-option>
        <el-option key="2" label="否" :value="false"></el-option>
      </el-select>
      <el-select style="width: 140px" class="mrb10" v-model="pagination.sortId" placeholder="请选择分类">
        <el-option
          v-for="item in sorts"
          :key="item.id"
          :label="item.sortName"
          :value="item.id">
        </el-option>
      </el-select>
      <el-select style="width: 140px" class="mrb10" v-model="pagination.labelId" placeholder="请选择标签">
        <el-option
          v-for="item in labelsTemp"
          :key="item.id"
          :label="item.labelName"
          :value="item.id">
        </el-option>
      </el-select>
      <el-input v-model="pagination.searchKey" placeholder="文章标题" class="handle-input mrb10"></el-input>
      <el-button type="primary" icon="el-icon-search" @click="searchArticles()">搜索</el-button>
      <el-button type="danger" @click="clearSearch()">清除参数</el-button>
      <el-button type="primary" @click="$router.push({path: '/postEdit'})">新增文章</el-button>
    </div>
    <el-table :data="articles" border class="table" header-cell-class-name="table-header">
      <el-table-column prop="id" label="ID" width="55" align="center"></el-table-column>
      <el-table-column prop="username" label="作者" align="center"></el-table-column>
      <el-table-column prop="articleTitle" label="文章标题" align="center"></el-table-column>
      <el-table-column prop="sort.sortName" label="分类" align="center"></el-table-column>
      <el-table-column prop="label.labelName" label="标签" align="center"></el-table-column>
      <el-table-column prop="viewCount" label="浏览量" align="center"></el-table-column>
      <el-table-column prop="likeCount" label="点赞数" align="center"></el-table-column>
      <el-table-column label="是否可见" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.viewStatus === false ? 'danger' : 'success'"
                  disable-transitions>
            {{scope.row.viewStatus === false ? '不可见' : '可见'}}
          </el-tag>
          <el-switch @click.native="changeStatus(scope.row, 1)" v-model="scope.row.viewStatus"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="封面" align="center">
        <template slot-scope="scope">
          <el-image lazy class="table-td-thumb" :src="scope.row.articleCover" fit="cover"></el-image>
        </template>
      </el-table-column>
      <el-table-column label="是否启用评论" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.commentStatus === false ? 'danger' : 'success'"
                  disable-transitions>
            {{scope.row.commentStatus === false ? '否' : '是'}}
          </el-tag>
          <el-switch @click.native="changeStatus(scope.row, 2)" v-model="scope.row.commentStatus"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="是否推荐" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.recommendStatus === false ? 'danger' : 'success'"
                  disable-transitions>
            {{scope.row.recommendStatus === false ? '否' : '是'}}
          </el-tag>
          <el-switch @click.native="changeStatus(scope.row, 3)" v-model="scope.row.recommendStatus"></el-switch>
        </template>
      </el-table-column>
      <el-table-column prop="commentCount" label="评论数" align="center"></el-table-column>
      <el-table-column prop="createTime" label="创建时间" align="center"></el-table-column>
      <el-table-column prop="updateTime" label="最终修改时间" align="center"></el-table-column>
      <el-table-column label="操作" width="260" align="center">
        <template slot-scope="scope">
          <el-button type="text" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button type="text" icon="el-icon-s-tools" style="color: var(--blue)" @click="handleDeleteTranslation(scope.row)">
            删除翻译
          </el-button>
          <el-button type="text" icon="el-icon-delete" style="color: var(--orangeRed)" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination background layout="total, prev, pager, next"
                     :current-page="pagination.current"
                     :page-size="pagination.size"
                     :total="pagination.total"
                     @current-change="handlePageChange">
      </el-pagination>
    </div>

    <!-- 删除翻译对话框 -->
    <el-dialog title="删除翻译" :visible.sync="deleteTranslationDialog" width="500px" center>
      <div v-if="availableLanguages.length === 0" style="text-align: center; padding: 20px;">
        <i class="el-icon-info" style="font-size: 48px; color: #909399;"></i>
        <p style="margin-top: 15px; color: #606266;">该文章暂无翻译版本</p>
      </div>
      
      <div v-else-if="availableLanguages.length === 1" style="text-align: center; padding: 20px;">
        <i class="el-icon-warning" style="font-size: 48px; color: #E6A23C;"></i>
        <p style="margin-top: 15px; color: #606266;">
          确认删除该文章的 <strong>{{ getLanguageName(availableLanguages[0]) }}</strong> 翻译版本吗？
        </p>
        <p style="color: #909399; font-size: 12px;">删除后将无法恢复，用户访问时会自动显示原文</p>
      </div>
      
      <div v-else>
        <p style="margin-bottom: 15px; color: #606266;">该文章有以下翻译版本，请选择要删除的语言：</p>
        <el-checkbox-group v-model="selectedLanguages">
          <div v-for="lang in availableLanguages" :key="lang" style="margin-bottom: 10px;">
            <el-checkbox :label="lang">{{ getLanguageName(lang) }}</el-checkbox>
          </div>
        </el-checkbox-group>
        <div style="margin-top: 15px;">
          <el-button type="text" @click="selectAllLanguages" style="color: #409EFF;">全选</el-button>
          <el-button type="text" @click="clearSelectedLanguages" style="color: #909399;">清空</el-button>
        </div>
      </div>
      
      <div slot="footer" class="dialog-footer">
        <el-button @click="deleteTranslationDialog = false">取消</el-button>
        <el-button 
          v-if="availableLanguages.length === 1" 
          type="danger" 
          @click="confirmDeleteSingleTranslation"
          :loading="deleteLoading">
          确认删除
        </el-button>
        <el-button 
          v-else-if="availableLanguages.length > 1" 
          type="danger" 
          @click="confirmDeleteMultipleTranslations"
          :disabled="selectedLanguages.length === 0"
          :loading="deleteLoading">
          删除选中的翻译 ({{ selectedLanguages.length }})
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>

  export default {
    data() {
      return {
        pagination: {
          current: 1,
          size: 10,
          total: 0,
          searchKey: "",
          recommendStatus: null,
          sortId: null,
          labelId: null
        },
        articles: [],
        sorts: [],
        labels: [],
        labelsTemp: [],
        // 删除翻译相关数据
        deleteTranslationDialog: false,
        availableLanguages: [],
        selectedLanguages: [],
        currentArticle: null,
        deleteLoading: false,
        // 语言映射
        languageMap: {
          'zh': '中文',
          'en': '英文',
          'zh-TW': '繁体中文',
          'ja': '日文',
          'ko': '韩文',
          'fr': '法文',
          'de': '德文',
          'es': '西班牙文',
          'ru': '俄文',
          'ar': '阿拉伯文',
          'pt': '葡萄牙文',
          'it': '意大利文',
          'th': '泰文',
          'vi': '越南文',
          'hi': '印地文'
        }
      }
    },

    computed: {
      // 使用computed属性确保isBoss值能响应Store变化
      isBoss() {
        return this.$store.state.currentAdmin.isBoss;
      }
    },

    watch: {
      'pagination.sortId'(newVal) {
        this.pagination.labelId = null;
        if (!this.$common.isEmpty(newVal) && !this.$common.isEmpty(this.labels)) {
          this.labelsTemp = this.labels.filter(l => l.sortId === newVal);
        }
      },
      // 监听Store中currentAdmin的变化
      '$store.state.currentAdmin': {
        handler(newAdmin, oldAdmin) {
          // 当管理员信息更新时，重新获取文章数据
          if (newAdmin && newAdmin.isBoss !== oldAdmin?.isBoss) {
            this.getArticles();
          }
        },
        deep: true
      }
    },

    created() {
      this.getArticles();
      this.getSortAndLabel();
    },

    mounted() {
    },

    methods: {
      getSortAndLabel() {
        this.$http.get(this.$constant.baseURL + "/webInfo/listSortAndLabel")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.sorts = res.data.sorts;
              this.labels = res.data.labels;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      clearSearch() {
        this.pagination = {
          current: 1,
          size: 10,
          total: 0,
          searchKey: "",
          recommendStatus: null,
          sortId: null,
          labelId: null
        }
        this.getArticles();
      },
      getArticles() {
        let url = "";
        if (this.isBoss) {
          url = "/admin/article/boss/list";
        } else {
          url = "/admin/article/user/list";
        }
        this.$http.post(this.$constant.baseURL + url, this.pagination, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.articles = res.data.records;
              this.pagination.total = res.data.total;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      handlePageChange(val) {
        this.pagination.current = val;
        this.getArticles();
      },
      searchArticles() {
        this.pagination.total = 0;
        this.pagination.current = 1;
        this.getArticles();
      },
      changeStatus(article, flag) {
        let param;
        if (flag === 1) {
          param = {
            articleId: article.id,
            viewStatus: article.viewStatus
          }
        } else if (flag === 2) {
          param = {
            articleId: article.id,
            commentStatus: article.commentStatus
          }
        } else if (flag === 3) {
          param = {
            articleId: article.id,
            recommendStatus: article.recommendStatus
          }
        }
        this.$http.get(this.$constant.baseURL + "/admin/article/changeArticleStatus", param, true)
          .then((res) => {
            if (flag === 1) {
              this.$message({
                duration: 0,
                showClose: true,
                message: "修改成功！注意，文章不可见时必须设置密码才能访问！",
                type: "warning"
              });
            } else {
              this.$message({
                message: "修改成功！",
                type: "success"
              });
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      handleDelete(item) {
        this.$confirm('确认删除？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/article/deleteArticle", {id: item.id}, true)
            .then((res) => {
              // 刷新文章列表
              this.pagination.current = 1;
              this.getArticles();
              
              this.$message({ message: "删除成功！", type: "success" });
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消删除!'
          });
        });
      },
      // 删除翻译相关方法
      async handleDeleteTranslation(item) {
        this.currentArticle = item;
        this.selectedLanguages = [];
        this.deleteTranslationDialog = true;
        
        // 获取可用翻译语言
        try {
          const response = await this.$http.get(this.$constant.baseURL + "/admin/article/getAvailableLanguages", {
            articleId: item.id
          }, true);
          
          if (response && response.data) {
            this.availableLanguages = response.data;
          } else {
            this.availableLanguages = [];
          }
        } catch (error) {
          this.$message({
            message: "获取翻译语言失败: " + error.message,
            type: "error"
          });
          this.availableLanguages = [];
        }
      },
      
      getLanguageName(langCode) {
        return this.languageMap[langCode] || langCode;
      },
      
      selectAllLanguages() {
        this.selectedLanguages = [...this.availableLanguages];
      },
      
      clearSelectedLanguages() {
        this.selectedLanguages = [];
      },
      
      async confirmDeleteSingleTranslation() {
        if (this.availableLanguages.length !== 1) return;
        
        this.deleteLoading = true;
        const deletedLanguage = this.availableLanguages[0];
        
        try {
          await this.$http.post(this.$constant.baseURL + "/admin/article/deleteTranslation", {
            articleId: this.currentArticle.id,
            language: deletedLanguage
          }, true);
          
          // 更新sitemap - 移除已删除翻译的URL
          await this.updateSitemapAfterTranslationDelete(this.currentArticle.id, [deletedLanguage]);
          
          this.$message({
            message: `成功删除 ${this.getLanguageName(deletedLanguage)} 翻译！`,
            type: "success"
          });
          
          this.deleteTranslationDialog = false;
        } catch (error) {
          this.$message({
            message: "删除翻译失败: " + error.message,
            type: "error"
          });
        } finally {
          this.deleteLoading = false;
        }
      },
      
      async confirmDeleteMultipleTranslations() {
        if (this.selectedLanguages.length === 0) return;
        
        this.deleteLoading = true;
        let successCount = 0;
        let failCount = 0;
        const deletedLanguages = [];
        
        // 如果选择了所有语言，使用删除所有翻译的接口
        if (this.selectedLanguages.length === this.availableLanguages.length) {
          try {
            await this.$http.post(this.$constant.baseURL + "/admin/article/deleteAllTranslations", {
              articleId: this.currentArticle.id
            }, true);
            
            // 更新sitemap - 移除所有翻译的URL
            await this.updateSitemapAfterTranslationDelete(this.currentArticle.id, this.availableLanguages);
            
            this.$message({
              message: "成功删除所有翻译！",
              type: "success"
            });
            
            this.deleteTranslationDialog = false;
            this.deleteLoading = false;
            return;
          } catch (error) {
            this.$message({
              message: "删除所有翻译失败: " + error.message,
              type: "error"
            });
            this.deleteLoading = false;
            return;
          }
        }
        
        // 逐个删除选中的翻译
        for (const language of this.selectedLanguages) {
          try {
            await this.$http.post(this.$constant.baseURL + "/admin/article/deleteTranslation", {
              articleId: this.currentArticle.id,
              language: language
            }, true);
            successCount++;
            deletedLanguages.push(language);
          } catch (error) {
            console.error(`删除 ${language} 翻译失败:`, error);
            failCount++;
          }
        }
        
        // 更新sitemap - 移除成功删除的翻译URL
        if (deletedLanguages.length > 0) {
          await this.updateSitemapAfterTranslationDelete(this.currentArticle.id, deletedLanguages);
        }
        
        // 显示结果消息
        if (failCount === 0) {
          this.$message({
            message: `成功删除 ${successCount} 个翻译！`,
            type: "success"
          });
        } else if (successCount === 0) {
          this.$message({
            message: `删除失败，共 ${failCount} 个翻译删除失败`,
            type: "error"
          });
        } else {
          this.$message({
            message: `部分成功：${successCount} 个删除成功，${failCount} 个删除失败`,
            type: "warning"
          });
        }
        
        this.deleteTranslationDialog = false;
        this.deleteLoading = false;
      },
      

      // 删除翻译后更新sitemap
      async updateSitemapAfterTranslationDelete(articleId, deletedLanguages) {
        try {
          // 调用Java后端的sitemap代理接口，Java会转发给Python服务
          const sitemapUrl = this.$constant.baseURL + '/admin/article/updateSitemap';
          
          // 为每个删除的语言发送移除请求
          for (const language of deletedLanguages) {
            try {
              await this.$http.post(sitemapUrl, {
                articleId: articleId,
                action: 'remove',
                language: language  // 传递语言参数，让Python服务知道要移除哪个语言版本
              }, true);
              
              console.log(`成功移除文章 ${articleId} 的 ${language} 语言sitemap条目`);
            } catch (error) {
              console.error(`移除语言 ${language} 的sitemap条目失败:`, error);
            }
          }
          
          console.log(`Sitemap已更新，移除文章 ${articleId} 的翻译语言:`, deletedLanguages);
        } catch (error) {
          console.error('更新sitemap失败:', error);
          // 不阻塞主流程，只记录错误
        }
      },
      
      handleEdit(item) {
        this.$router.push({path: '/postEdit', query: {id: item.id}});
      }
    }
  }
</script>

<style scoped>

  .handle-box {
    margin-bottom: 20px;
  }

  .handle-input {
    width: 160px;
    display: inline-block;
  }

  .table {
    width: 100%;
    font-size: 14px;
  }

  .mrb10 {
    margin-right: 10px;
    margin-bottom: 10px;
  }

  .table-td-thumb {
    display: block;
    margin: auto;
    width: 40px;
    height: 40px;
  }

  .pagination {
    margin: 20px 0;
    text-align: right;
  }

  .el-switch {
    margin: 5px;
  }
</style>
