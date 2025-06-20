<template>
  <div>
    <div style="margin-bottom: 30px;">
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        预渲染管理
      </el-tag>
    </div>

    <!-- 实时监控面板 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6">
        <el-card class="status-card">
          <div class="status-item">
            <div class="status-icon">
              <i :class="[serviceStatus.online ? 'el-icon-success' : 'el-icon-error', 
                         serviceStatus.online ? 'status-online' : 'status-offline']"></i>
            </div>
            <div class="status-info">
              <div class="status-title">服务状态</div>
              <div class="status-value">{{ serviceStatus.online ? '在线' : '离线' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="status-card">
          <div class="status-item">
            <div class="status-icon">
              <i class="el-icon-time status-info-color"></i>
            </div>
            <div class="status-info">
              <div class="status-title">活跃任务</div>
              <div class="status-value">{{ dashboardData.stats?.currentTasks?.length || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="status-card">
          <div class="status-item">
            <div class="status-icon">
              <i class="el-icon-cpu status-warn-color"></i>
            </div>
            <div class="status-info">
              <div class="status-title">错误数量</div>
              <div class="status-value">{{ dashboardData.stats?.recentActivity?.errorCount || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="status-card">
          <div class="status-item">
            <div class="status-icon">
              <i class="el-icon-stopwatch status-success-color"></i>
            </div>
            <div class="status-info">
              <div class="status-title">运行时间</div>
              <div class="status-value">{{ dashboardData.systemInfo?.uptime || '0s' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab导航 -->
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <!-- 控制面板 -->
      <el-tab-pane label="控制面板" name="control">
        <!-- 快速操作 -->
        <el-card class="box-card" shadow="never" style="margin-bottom: 20px;">
          <div slot="header" class="clearfix">
            <span>快速操作</span>
          </div>
          <div>
            <!-- 第一行：基础页面 -->
            <el-row :gutter="15" style="margin-bottom: 15px;">
              <el-col :span="8">
                <el-card shadow="hover" class="quick-action-card">
                  <div class="quick-action">
                    <div class="action-icon home-icon">
                      <i class="el-icon-s-home"></i>
                    </div>
                    <div class="action-title">渲染首页</div>
                    <div class="action-desc">重新生成首页预渲染文件</div>
                    <el-button 
                      type="primary" 
                      size="small" 
                      :loading="isRendering.home"
                      @click="renderHomePage">
                      开始渲染
                    </el-button>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="hover" class="quick-action-card">
                  <div class="quick-action">
                    <div class="action-icon favorite-icon">
                      <i class="el-icon-star-on"></i>
                    </div>
                    <div class="action-title">渲染百宝箱</div>
                    <div class="action-desc">重新生成百宝箱页面预渲染文件</div>
                    <el-button 
                      type="success" 
                      size="small" 
                      :loading="isRendering.favorite"
                      @click="renderFavoritePage">
                      开始渲染
                    </el-button>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="hover" class="quick-action-card">
                  <div class="quick-action">
                    <div class="action-icon sortlist-icon">
                      <i class="el-icon-s-grid"></i>
                    </div>
                    <div class="action-title">渲染分类索引</div>
                    <div class="action-desc">生成分类导航页面 (/sort)</div>
                    <el-button 
                      type="info" 
                      size="small" 
                      :loading="isRendering.sortIndex"
                      @click="renderSortIndexPage">
                      开始渲染
                    </el-button>
                  </div>
                </el-card>
              </el-col>
            </el-row>
            
            <!-- 第二行：批量操作 -->
            <el-row :gutter="15">
              <el-col :span="12">
                <el-card shadow="hover" class="quick-action-card">
                  <div class="quick-action">
                    <div class="action-icon sort-icon">
                      <i class="el-icon-menu"></i>
                    </div>
                    <div class="action-title">批量渲染分类详情</div>
                    <div class="action-desc">为每个分类生成详情页面 (/sort/{id})</div>
                    <el-button 
                      type="warning" 
                      size="small" 
                      :loading="isRendering.allCategories"
                      @click="renderAllCategoryPages">
                      开始渲染
                    </el-button>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="hover" class="quick-action-card">
                  <div class="quick-action">
                    <div class="action-icon all-icon">
                      <i class="el-icon-refresh"></i>
                    </div>
                    <div class="action-title">全量重建</div>
                    <div class="action-desc">重新生成所有页面预渲染文件</div>
                    <el-button 
                      type="danger" 
                      size="small" 
                      :loading="isRendering.all"
                      @click="renderAllPages">
                      开始渲染
                    </el-button>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </div>
        </el-card>

        <!-- 分类页面管理 -->
        <el-card class="box-card" shadow="never">
          <div slot="header" class="clearfix">
            <span>分类页面管理</span>
            <el-button style="float: right; padding: 3px 0" type="text" @click="loadSortList">
              <i class="el-icon-refresh"></i>
              刷新列表
            </el-button>
          </div>
          <div>
            <el-table :data="sortList" stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="80">
              </el-table-column>
              <el-table-column prop="sortName" label="分类名称" width="180">
              </el-table-column>
              <el-table-column prop="sortDescription" label="描述">
              </el-table-column>
              <el-table-column prop="articleCount" label="文章数量" width="100">
                <template slot-scope="scope">
                  {{ scope.row.countOfSort || 0 }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200">
                <template slot-scope="scope">
                  <el-button 
                    type="text" 
                    size="small"
                    :loading="isRendering['category_' + scope.row.id]"
                    @click="renderCategoryPage(scope.row.id)">
                    渲染详情页
                  </el-button>
                  <el-button 
                    type="text" 
                    size="small"
                    style="color: #f56c6c;"
                    @click="clearSortCache(scope.row.id)">
                    清理缓存
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 实时监控 -->
      <el-tab-pane label="实时监控" name="monitor">
        <el-card class="box-card" shadow="never" style="margin-bottom: 20px;">
          <div slot="header" class="clearfix">
            <span>系统状态监控</span>
            <div style="float: right;">
              <el-button size="small" @click="loadDashboardData" :loading="statusLoading" style="margin-right: 10px;">
                <i class="el-icon-refresh"></i>
                手动刷新
              </el-button>
              <el-switch
                v-model="autoRefresh"
                active-text="自动刷新"
                @change="toggleAutoRefresh">
              </el-switch>
            </div>
          </div>
          <div>
            <el-row :gutter="20" style="margin-bottom: 20px;">
              <el-col :span="12">
                <div class="monitor-section">
                  <h4><i class="el-icon-monitor"></i> 服务统计</h4>
                  <div class="stats-grid">
                    <div class="stat-item">
                      <span class="stat-label">总请求数</span>
                      <span class="stat-value">{{ dashboardData.stats?.totalRequests || 0 }}</span>
                    </div>
                    <div class="stat-item">
                      <span class="stat-label">成功率</span>
                      <span class="stat-value">{{ getSuccessRate() }}%</span>
                    </div>
                    <div class="stat-item">
                      <span class="stat-label">平均响应时间</span>
                      <span class="stat-value">{{ dashboardData.stats?.averageRenderTime || 0 }}ms</span>
                    </div>
                    <div class="stat-item">
                      <span class="stat-label">内存使用</span>
                      <span class="stat-value">{{ getMemoryUsage() }}MB</span>
                    </div>
                  </div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="monitor-section">
                  <h4><i class="el-icon-warning"></i> 近期错误</h4>
                  <div class="error-list">
                    <div 
                      v-for="(error, index) in dashboardData.recentErrors?.slice(0, 5)" 
                      :key="index"
                      class="error-item">
                      <span class="error-time">{{ formatTime(error.timestamp) }}</span>
                      <span class="error-message">{{ error.error }}</span>
                    </div>
                    <div v-if="!dashboardData.recentErrors?.length" class="no-errors">
                      暂无错误记录
                    </div>
                  </div>
                </div>
              </el-col>
            </el-row>
            
            <!-- 当前任务 -->
            <div class="monitor-section">
              <h4><i class="el-icon-loading"></i> 当前任务 ({{ dashboardData.stats?.currentTasks?.length || 0 }})</h4>
              <el-table :data="dashboardData.stats?.currentTasks || []" style="width: 100%">
                <el-table-column prop="taskId" label="任务ID" width="180" show-overflow-tooltip>
                </el-table-column>
                <el-table-column prop="type" label="类型" width="120">
                </el-table-column>
                <el-table-column prop="status" label="状态" width="120">
                  <template slot-scope="scope">
                    <el-tag :type="getTaskStatusType(scope.row.status)">
                      {{ scope.row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="开始时间" :formatter="formatTableTime">
                </el-table-column>
                <el-table-column label="耗时">
                  <template slot-scope="scope">
                    {{ getTaskDuration(scope.row.startTime) }}
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!dashboardData.stats?.currentTasks?.length" class="no-tasks">
                暂无运行中的任务
              </div>
            </div>

            <!-- 最近任务历史 -->
            <div class="monitor-section">
              <h4><i class="el-icon-time"></i> 最近任务历史</h4>
              <el-table :data="dashboardData.stats?.recentTasks?.slice().reverse() || []" style="width: 100%">
                <el-table-column prop="taskId" label="任务ID" width="180" show-overflow-tooltip>
                </el-table-column>
                <el-table-column prop="type" label="类型" width="120">
                </el-table-column>
                <el-table-column prop="status" label="状态" width="120">
                  <template slot-scope="scope">
                    <el-tag :type="getTaskStatusType(scope.row.status)">
                      {{ getStatusText(scope.row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="开始时间" :formatter="formatTableTime" width="160">
                </el-table-column>
                <el-table-column prop="endTime" label="结束时间" :formatter="formatTableTime" width="160">
                </el-table-column>
                <el-table-column prop="duration" label="耗时" width="100">
                </el-table-column>
                <el-table-column label="详情" min-width="200">
                  <template slot-scope="scope">
                    <div v-if="scope.row.error" class="task-error">
                      <span class="error-text">{{ scope.row.error }}</span>
                    </div>
                    <div v-else-if="scope.row.details" class="task-details">
                      <span v-if="scope.row.details.count">{{ scope.row.details.count }} 项</span>
                      <span v-if="scope.row.details.successCount">成功: {{ scope.row.details.successCount }}</span>
                      <span v-if="scope.row.details.failCount">失败: {{ scope.row.details.failCount }}</span>
                    </div>
                    <div v-else class="task-params">
                      {{ formatTaskParams(scope.row.params) }}
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!dashboardData.stats?.recentTasks?.length" class="no-tasks">
                暂无任务历史
              </div>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 实时日志 -->
      <el-tab-pane label="实时日志" name="logs">
        <el-card class="box-card" shadow="never">
          <div slot="header" class="clearfix">
            <span>实时日志</span>
            <div style="float: right;">
              <el-select v-model="logFilter" size="small" style="width: 100px; margin-right: 10px;" @change="loadLogs">
                <el-option label="全部" value=""></el-option>
                <el-option label="错误" value="error"></el-option>
                <el-option label="警告" value="warn"></el-option>
                <el-option label="信息" value="info"></el-option>
                <el-option label="调试" value="debug"></el-option>
              </el-select>
              <el-button size="small" @click="loadLogs">
                <i class="el-icon-refresh"></i>
                刷新
              </el-button>
              <el-button size="small" @click="clearDisplayLogs">
                清空显示
              </el-button>
            </div>
          </div>
          <div class="logs-container-enhanced">
            <div 
              v-for="(log, index) in displayLogs" 
              :key="index"
              :class="['log-item-enhanced', 'log-' + log.level]">
              <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
              <span :class="['log-level', 'level-' + log.level]">{{ log.level.toUpperCase() }}</span>
              <span class="log-service">{{ log.service }}</span>
              <span class="log-message">{{ log.message }}</span>
              <div v-if="log.requestId" class="log-meta">
                <span class="log-request-id">{{ log.requestId }}</span>
              </div>
            </div>
            <div v-if="displayLogs.length === 0" class="no-logs">
              暂无日志记录
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 历史日志 -->
      <el-tab-pane label="历史日志" name="history">
        <el-card class="box-card" shadow="never">
          <div slot="header" class="clearfix">
            <span>历史日志文件</span>
            <el-button style="float: right; padding: 3px 0" type="text" @click="loadLogFiles">
              <i class="el-icon-refresh"></i>
              刷新列表
            </el-button>
          </div>
          <div>
            <el-table :data="logFiles" style="width: 100%">
              <el-table-column prop="name" label="文件名" width="200">
              </el-table-column>
              <el-table-column prop="date" label="日期" width="120">
              </el-table-column>
              <el-table-column prop="size" label="大小" width="100">
              </el-table-column>
              <el-table-column prop="modified" label="修改时间" :formatter="formatTableTime">
              </el-table-column>
              <el-table-column label="操作" width="200">
                <template slot-scope="scope">
                  <el-button 
                    type="text" 
                    size="small"
                    @click="viewLogFile(scope.row.name)">
                    查看
                  </el-button>
                  <el-button 
                    type="text" 
                    size="small"
                    @click="downloadLogFile(scope.row.name)">
                    下载
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 系统设置 -->
      <el-tab-pane label="系统设置" name="settings">
        <el-card class="box-card" shadow="never">
          <div slot="header" class="clearfix">
            <span>预渲染系统设置</span>
          </div>
          <div>
            <el-row :gutter="20">
              <el-col :span="12">
                <div class="settings-section">
                  <h4><i class="el-icon-setting"></i> 监控配置</h4>
                  <el-form label-width="120px" size="small">
                    <el-form-item label="自动刷新间隔">
                      <el-select v-model="refreshInterval" @change="updateRefreshInterval">
                        <el-option label="3秒" :value="3000"></el-option>
                        <el-option label="5秒" :value="5000"></el-option>
                        <el-option label="10秒" :value="10000"></el-option>
                        <el-option label="30秒" :value="30000"></el-option>
                        <el-option label="1分钟" :value="60000"></el-option>
                      </el-select>
                    </el-form-item>
                    <el-form-item label="日志显示数量">
                      <el-input-number v-model="logDisplayLimit" :min="50" :max="1000" :step="50"></el-input-number>
                    </el-form-item>
                  </el-form>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="settings-section">
                  <h4><i class="el-icon-delete"></i> 清理操作</h4>
                  <el-button type="danger" size="small" @click="cleanupService" :loading="isCleaningUp">
                    清理预渲染缓存
                  </el-button>
                  <p class="settings-tip">
                    清理所有预渲染文件和缓存数据，下次访问时会重新生成
                  </p>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 日志文件查看对话框 -->
    <el-dialog
      title="查看日志文件"
      :visible.sync="logFileDialogVisible"
      width="80%"
      top="5vh">
      <div class="log-file-viewer">
        <div class="log-file-header">
          <span>文件：{{ currentLogFile }}</span>
          <div style="float: right;">
            <el-select v-model="fileLogFilter" size="small" style="width: 100px; margin-right: 10px;" @change="loadLogFileContent">
              <el-option label="全部" value=""></el-option>
              <el-option label="错误" value="error"></el-option>
              <el-option label="警告" value="warn"></el-option>
              <el-option label="信息" value="info"></el-option>
            </el-select>
            <el-button size="small" @click="loadLogFileContent">刷新</el-button>
          </div>
        </div>
        <div class="log-file-content">
          <div 
            v-for="(log, index) in logFileContent" 
            :key="index"
            :class="['log-item-enhanced', 'log-' + log.level]">
            <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
            <span :class="['log-level', 'level-' + log.level]">{{ log.level.toUpperCase() }}</span>
            <span class="log-service">{{ log.service }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  data() {
    return {
      serviceStatus: {
        online: false,
        lastCheck: null
      },
      renderQueue: [],
      lastUpdateTime: '',
      statusLoading: false,
      isRendering: {
        home: false,
        favorite: false,
        sortIndex: false,      // 替代 defaultSort
        allCategories: false,  // 替代 allSorts  
        all: false
      },
      sortList: [],
      renderLogs: [],
      
      // 新增的监控数据
      activeTab: 'control',
      autoRefresh: true,
      refreshInterval: 3000, // 改为3秒刷新一次，更及时地显示任务状态
      logDisplayLimit: 100,
      logFilter: '',
      fileLogFilter: '',
      
      // 对话框控制
      logFileDialogVisible: false,
      currentLogFile: '',
      logFileContent: [],
      
      // 数据存储
      dashboardData: {
        stats: {
          totalRequests: 0,
          successfulRenders: 0,
          failedRenders: 0,
          averageRenderTime: 0,
          currentTasks: [],
          recentActivity: {
            totalLogs: 0,
            errorCount: 0,
            warnCount: 0,
            infoCount: 0
          }
        },
        systemInfo: {
          uptime: '0s',
          memory: {
            used: 0,
            total: 0
          }
        },
        recentErrors: [],
        recentLogs: []
      },
      displayLogs: [],
      logFiles: [],
      
      // 状态控制
      isCleaningUp: false,
      refreshTimer: null
    }
  },

  created() {
    this.checkServiceStatus();
    this.loadSortList();
    this.loadDashboardData();
    this.loadLogs();
  },

  mounted() {
    // 启动自动刷新
    if (this.autoRefresh) {
      this.startAutoRefresh();
    }
  },

  beforeDestroy() {
    // 清理定时器
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  },

  methods: {
    // 检查预渲染服务状态
    async checkServiceStatus() {
      this.statusLoading = true;
      try {
        const response = await this.$http.get(this.$constant.baseURL + '/webInfo/prerender/status', {}, true);
        this.serviceStatus.online = response.data && response.data.status !== 'error';
        this.serviceStatus.lastCheck = new Date().toLocaleString();
        this.lastUpdateTime = new Date().toLocaleString();
        
        if (this.serviceStatus.online) {
          this.addLog('info', '预渲染服务状态正常');
        } else {
          this.addLog('error', '预渲染服务离线或不可用');
        }
      } catch (error) {
        this.serviceStatus.online = false;
        this.addLog('error', '检查预渲染服务状态失败: ' + error.message);
      } finally {
        this.statusLoading = false;
      }
    },

    // 渲染首页
    async renderHomePage() {
      this.isRendering.home = true;
      try {
        await this.$http.post(this.$constant.baseURL + '/webInfo/renderHomePage', {}, true);
        this.addLog('success', '首页预渲染任务已提交');
        this.$message.success('首页渲染任务已提交');
        
        // 立即刷新仪表板数据以显示新任务
        await this.loadDashboardData();
      } catch (error) {
        this.addLog('error', '提交首页渲染任务失败: ' + error.message);
        this.$message.error('提交首页渲染任务失败');
      } finally {
        this.isRendering.home = false;
      }
    },

    // 渲染百宝箱页面
    async renderFavoritePage() {
      this.isRendering.favorite = true;
      try {
        await this.$http.post(this.$constant.baseURL + '/webInfo/renderFavoritePage', {}, true);
        this.addLog('success', '百宝箱页面预渲染任务已提交');
        this.$message.success('百宝箱渲染任务已提交');
        
        // 立即刷新仪表板数据以显示新任务
        await this.loadDashboardData();
      } catch (error) {
        this.addLog('error', '提交百宝箱渲染任务失败: ' + error.message);
        this.$message.error('提交百宝箱渲染任务失败');
      } finally {
        this.isRendering.favorite = false;
      }
    },

    // 渲染分类索引页面（分类导航页面）
    async renderSortIndexPage() {
      this.isRendering.sortIndex = true;
      try {
        await this.$http.post(this.$constant.baseURL + '/webInfo/renderSortIndexPage', {}, true);
        this.addLog('success', '分类索引页面预渲染任务已提交');
        this.$message.success('分类索引页面渲染任务已提交');
        
        // 立即刷新仪表板数据以显示新任务
        await this.loadDashboardData();
      } catch (error) {
        this.addLog('error', '提交分类索引页面渲染任务失败: ' + error.message);
        this.$message.error('提交分类索引页面渲染任务失败');
      } finally {
        this.isRendering.sortIndex = false;
      }
    },

    // 渲染指定分类详情页面
    async renderCategoryPage(sortId) {
      this.isRendering['category_' + sortId] = true;
      try {
        await this.$http.post(this.$constant.baseURL + '/webInfo/renderCategoryPage', { sortId }, true);
        this.addLog('success', `分类ID ${sortId} 详情页面预渲染任务已提交`);
        this.$message.success('分类详情页面渲染任务已提交');
        
        // 立即刷新仪表板数据以显示新任务
        await this.loadDashboardData();
      } catch (error) {
        this.addLog('error', `提交分类 ${sortId} 详情页面渲染任务失败: ` + error.message);
        this.$message.error('提交分类详情页面渲染任务失败');
      } finally {
        this.isRendering['category_' + sortId] = false;
      }
    },

    // 批量渲染所有分类详情页面
    async renderAllCategoryPages() {
      this.isRendering.allCategories = true;
      try {
        const sortIds = this.sortList.map(sort => sort.id);
        await this.$http.post(this.$constant.baseURL + '/webInfo/renderAllCategoryPages', { sortIds }, true);
        this.addLog('success', `所有分类详情页面预渲染任务已提交 (${sortIds.length}个)`);
        this.$message.success('所有分类详情页面渲染任务已提交');
        
        // 立即刷新仪表板数据以显示新任务
        await this.loadDashboardData();
      } catch (error) {
        this.addLog('error', '提交所有分类详情页面渲染任务失败: ' + error.message);
        this.$message.error('提交所有分类详情页面渲染任务失败');
      } finally {
        this.isRendering.allCategories = false;
      }
    },

    // ===== 兼容性方法 (向后兼容) =====
    
    /**
     * @deprecated 使用 renderSortIndexPage() 替代
     */
    async renderDefaultSortPage() {
      return this.renderSortIndexPage();
    },

    /**
     * @deprecated 使用 renderCategoryPage() 替代
     */
    async renderSortPage(sortId) {
      return this.renderCategoryPage(sortId);
    },

    /**
     * @deprecated 使用 renderAllCategoryPages() 替代
     */
    async renderAllSortPages() {
      return this.renderAllCategoryPages();
    },

    // 全量重建所有页面
    async renderAllPages() {
      this.isRendering.all = true;
      try {
        await this.$http.post(this.$constant.baseURL + '/webInfo/renderAllPages', {}, true);
        this.addLog('success', '全量重建任务已提交 (包括首页、收藏夹、分类索引、所有分类详情页和文章页)');
        this.$message.success('全量重建任务已提交，这可能需要一些时间');
        
        // 立即刷新仪表板数据以显示新任务
        await this.loadDashboardData();
      } catch (error) {
        this.addLog('error', '提交全量重建任务失败: ' + error.message);
        this.$message.error('提交全量重建任务失败');
      } finally {
        this.isRendering.all = false;
      }
    },

    // 清理分类缓存
    async clearSortCache(sortId) {
      try {
        await this.$http.post(this.$constant.baseURL + '/webInfo/clearSortCache', { sortId }, true);
        this.addLog('info', `分类ID ${sortId} 缓存已清理`);
        this.$message.success('分类缓存已清理');
      } catch (error) {
        this.addLog('error', `清理分类 ${sortId} 缓存失败: ` + error.message);
        this.$message.error('清理分类缓存失败');
      }
    },

    // 加载分类列表
    async loadSortList() {
      try {
        const response = await this.$http.get(this.$constant.baseURL + '/webInfo/listSortForPrerender', {}, true);
        this.sortList = response.data || [];
      } catch (error) {
        this.addLog('error', '加载分类列表失败: ' + error.message);
      }
    },

    // 添加日志
    addLog(type, message) {
      this.renderLogs.unshift({
        type,
        message,
        time: new Date().toLocaleString()
      });
      
      // 限制日志数量
      if (this.renderLogs.length > 100) {
        this.renderLogs = this.renderLogs.slice(0, 100);
      }
    },

    // 清空日志
    clearLogs() {
      this.renderLogs = [];
      this.$message.success('日志已清空');
    },

    // Tab切换处理
    handleTabClick(tab) {
      this.activeTab = tab.name;
      
      switch (tab.name) {
        case 'monitor':
          this.loadDashboardData();
          break;
        case 'logs':
          this.loadLogs();
          break;
        case 'history':
          this.loadLogFiles();
          break;
      }
    },

    // 加载仪表板数据
    async loadDashboardData() {
      try {
        const response = await this.$http.get(this.$constant.baseURL + '/webInfo/prerender/dashboard', {}, true);
        if (response.data) {
          this.dashboardData = response.data;
          this.serviceStatus.online = true;
          
          // 调试信息：输出任务数据
          console.log('仪表板数据已更新:', {
            currentTasks: this.dashboardData.stats?.currentTasks?.length || 0,
            recentTasks: this.dashboardData.stats?.recentTasks?.length || 0,
            totalRequests: this.dashboardData.stats?.totalRequests || 0,
            successfulRenders: this.dashboardData.stats?.successfulRenders || 0,
            timestamp: new Date().toLocaleTimeString()
          });
          
          // 如果有当前任务，打印详细信息
          if (this.dashboardData.stats?.currentTasks?.length > 0) {
            console.log('当前运行中的任务:', this.dashboardData.stats.currentTasks);
          }
          
          // 如果有最近任务，打印最近3个
          if (this.dashboardData.stats?.recentTasks?.length > 0) {
            console.log('最近完成的任务 (最近3个):', this.dashboardData.stats.recentTasks.slice(-3));
          }
        }
      } catch (error) {
        console.error('加载仪表板数据失败:', error);
        this.serviceStatus.online = false;
      }
    },

    // 加载实时日志
    async loadLogs() {
      try {
        const params = {
          limit: this.logDisplayLimit
        };
        if (this.logFilter) {
          params.level = this.logFilter;
        }
        
        const response = await this.$http.get(this.$constant.baseURL + '/webInfo/prerender/logs', params, true);
        if (response.data && response.data.logs) {
          this.displayLogs = response.data.logs;
        }
      } catch (error) {
        console.error('加载日志失败:', error);
      }
    },

    // 清空显示的日志
    clearDisplayLogs() {
      this.displayLogs = [];
      this.$message.success('显示日志已清空');
    },

    // 加载日志文件列表
    async loadLogFiles() {
      try {
        const response = await this.$http.get(this.$constant.baseURL + '/webInfo/prerender/logs/files', {}, true);
        if (response.data && response.data.files) {
          this.logFiles = response.data.files;
        }
      } catch (error) {
        console.error('加载日志文件列表失败:', error);
        this.$message.error('加载日志文件列表失败');
      }
    },

    // 查看日志文件
    async viewLogFile(filename) {
      this.currentLogFile = filename;
      this.logFileDialogVisible = true;
      await this.loadLogFileContent();
    },

    // 加载日志文件内容
    async loadLogFileContent() {
      try {
        const params = {
          lines: 1000
        };
        if (this.fileLogFilter) {
          params.level = this.fileLogFilter;
        }
        
        const response = await this.$http.get(
          this.$constant.baseURL + `/webInfo/prerender/logs/files/${this.currentLogFile}`, 
          params, 
          true
        );
        
        if (response.data && response.data.logs) {
          this.logFileContent = response.data.logs;
        }
      } catch (error) {
        console.error('加载日志文件内容失败:', error);
        this.$message.error('加载日志文件内容失败');
      }
    },

    // 下载日志文件
    async downloadLogFile(filename) {
      try {
        const url = this.$constant.baseURL + `/webInfo/prerender/logs/download/${filename}`;
        window.open(url, '_blank');
        this.$message.success('日志文件下载已开始');
      } catch (error) {
        console.error('下载日志文件失败:', error);
        this.$message.error('下载日志文件失败');
      }
    },

    // 清理预渲染服务
    async cleanupService() {
      this.isCleaningUp = true;
      try {
        await this.$http.post(this.$constant.baseURL + '/webInfo/prerender/cleanup', {}, true);
        this.$message.success('预渲染缓存已清理');
        this.addLog('info', '预渲染缓存清理完成');
      } catch (error) {
        console.error('清理预渲染服务失败:', error);
        this.$message.error('清理预渲染服务失败');
        this.addLog('error', '清理预渲染服务失败: ' + error.message);
      } finally {
        this.isCleaningUp = false;
      }
    },

    // 切换自动刷新
    toggleAutoRefresh(enabled) {
      this.autoRefresh = enabled;
      if (enabled) {
        this.startAutoRefresh();
      } else {
        this.stopAutoRefresh();
      }
    },

    // 启动自动刷新
    startAutoRefresh() {
      this.stopAutoRefresh(); // 先停止现有的定时器
      this.refreshTimer = setInterval(() => {
        if (this.activeTab === 'monitor') {
          this.loadDashboardData();
        } else if (this.activeTab === 'logs') {
          this.loadLogs();
        }
        this.checkServiceStatus();
      }, this.refreshInterval);
    },

    // 停止自动刷新
    stopAutoRefresh() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer);
        this.refreshTimer = null;
      }
    },

    // 更新刷新间隔
    updateRefreshInterval() {
      if (this.autoRefresh) {
        this.startAutoRefresh();
      }
    },

    // 获取成功率
    getSuccessRate() {
      const stats = this.dashboardData.stats;
      if (!stats || stats.totalRequests === 0) return 0;
      
      const successRate = ((stats.successfulRenders || 0) / stats.totalRequests) * 100;
      return Math.round(successRate);
    },

    // 获取内存使用情况
    getMemoryUsage() {
      const memory = this.dashboardData.systemInfo?.memory;
      if (!memory || !memory.used) return 0;
      
      const usedMB = Math.round(memory.used / 1024 / 1024);
      return usedMB;
    },

    // 格式化时间
    formatTime(timestamp) {
      if (!timestamp) return '--';
      const date = new Date(timestamp);
      return date.toLocaleTimeString();
    },

    // 格式化表格时间
    formatTableTime(row, column, cellValue) {
      if (!cellValue) return '--';
      const date = new Date(cellValue);
      return date.toLocaleString();
    },

    // 格式化日志时间
    formatLogTime(timestamp) {
      if (!timestamp) return '--';
      const date = new Date(timestamp);
      return date.toLocaleString();
    },

    // 获取任务状态类型
    getTaskStatusType(status) {
      switch (status) {
        case 'running':
          return 'warning';
        case 'completed':
          return 'success';
        case 'failed':
          return 'danger';
        default:
          return 'info';
      }
    },

    // 获取状态文本
    getStatusText(status) {
      switch (status) {
        case 'running':
          return '运行中';
        case 'completed':
          return '已完成';
        case 'failed':
          return '失败';
        default:
          return status;
      }
    },

    // 格式化任务参数
    formatTaskParams(params) {
      if (!params) return '--';
      
      if (params.type) {
        return `页面类型: ${params.type}`;
      } else if (params.ids && Array.isArray(params.ids)) {
        return `文章ID: ${params.ids.join(', ')}`;
      } else if (params.sortId) {
        return `分类ID: ${params.sortId}`;
      }
      
      return JSON.stringify(params);
    },

    // 获取任务持续时间
    getTaskDuration(startTime) {
      if (!startTime) return '--';
      
      const start = new Date(startTime);
      const now = new Date();
      const duration = Math.floor((now - start) / 1000);
      
      if (duration < 60) {
        return `${duration}秒`;
      } else if (duration < 3600) {
        return `${Math.floor(duration / 60)}分钟`;
      } else {
        return `${Math.floor(duration / 3600)}小时`;
      }
    }
  }
}
</script>

<style scoped>
.my-tag {
  margin-bottom: 20px !important;
  width: 100%;
  text-align: left;
  background: var(--lightYellow);
  border: none;
  height: 40px;
  line-height: 40px;
  font-size: 16px;
  color: var(--black);
}

.status-item {
  text-align: center;
  padding: 10px;
}

.status-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.status-value {
  font-size: 14px;
  font-weight: 500;
}

.status-online {
  color: #67c23a;
}

.status-offline {
  color: #f56c6c;
}

.quick-action-card {
  height: 180px;
}

.quick-action {
  text-align: center;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.action-icon {
  font-size: 32px;
  margin-bottom: 10px;
  height: 50px;
  line-height: 50px;
  border-radius: 25px;
  margin: 0 auto 10px;
  width: 50px;
}

.home-icon {
  background: #e6f7ff;
  color: #1890ff;
}

.favorite-icon {
  background: #f6ffed;
  color: #52c41a;
}

.sortlist-icon {
  background: #f0f5ff;
  color: #722ed1;
}

.sort-icon {
  background: #fff7e6;
  color: #fa8c16;
}

.all-icon {
  background: #fff2f0;
  color: #ff4d4f;
}

.action-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 5px;
  color: #333;
}

.action-desc {
  font-size: 12px;
  color: #666;
  margin-bottom: 15px;
  line-height: 1.4;
}

.logs-container {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  background: #fafafa;
}

.log-item {
  display: flex;
  margin-bottom: 8px;
  padding: 5px;
  border-radius: 3px;
  font-size: 12px;
}

.log-time {
  margin-right: 10px;
  color: #909399;
  min-width: 150px;
}

.log-message {
  flex: 1;
}

.config-info {
  font-size: 13px;
  line-height: 1.6;
}

.info-section h4 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.info-section ul {
  margin: 0;
  padding-left: 20px;
}

.info-section li {
  margin-bottom: 5px;
  color: #666;
}

.no-logs {
  text-align: center;
  color: #909399;
  padding: 20px;
}

.log-success {
  background: #f0f9ff;
  border-left: 3px solid #67c23a;
}

.log-error {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
}

.log-info {
  background: #f4f4f5;
  border-left: 3px solid #909399;
}

.no-logs {
  text-align: center;
  color: #909399;
  padding: 20px;
}

.rotating {
  animation: rotate 2s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.status-card {
  height: 100%;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
}

.status-icon {
  font-size: 24px;
  margin-right: 10px;
}

.status-info {
  flex: 1;
}

.status-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 5px;
  color: #333;
}

.status-value {
  font-size: 16px;
  font-weight: 500;
}

.status-info-color {
  color: #67c23a;
}

.status-warn-color {
  color: #faad14;
}

.status-success-color {
  color: #67c23a;
}

.monitor-section {
  margin-bottom: 20px;
}

.stats-grid {
  display: flex;
  justify-content: space-between;
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
}

.error-list {
  margin-top: 10px;
}

.error-item {
  margin-bottom: 5px;
  color: #f56c6c;
}

.error-time {
  margin-right: 10px;
}

.error-message {
  flex: 1;
}

.monitor-section {
  margin-bottom: 20px;
}

.monitor-section h4 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.stats-grid {
  display: flex;
  justify-content: space-between;
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
}

.error-list {
  margin-top: 10px;
}

.error-item {
  margin-bottom: 5px;
  color: #f56c6c;
}

.error-time {
  margin-right: 10px;
}

.error-message {
  flex: 1;
}

.logs-container-enhanced {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  background: #fafafa;
}

.log-item-enhanced {
  display: flex;
  margin-bottom: 8px;
  padding: 5px;
  border-radius: 3px;
  font-size: 12px;
}

.log-time {
  margin-right: 10px;
  color: #909399;
  min-width: 150px;
}

.log-level {
  margin-right: 10px;
  color: #909399;
  min-width: 50px;
}

.log-service {
  flex: 1;
}

.log-message {
  flex: 1;
}

.log-meta {
  margin-left: 10px;
  color: #909399;
}

.log-request-id {
  margin-left: 10px;
  color: #909399;
}

.level-error {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
}

.level-warn {
  background: #fff7e6;
  border-left: 3px solid #faad14;
}

.level-info {
  background: #f0f9ff;
  border-left: 3px solid #1890ff;
}

.level-debug {
  background: #f9f0ff;
  border-left: 3px solid #722ed1;
}

.settings-section {
  margin-bottom: 20px;
}

.settings-section h4 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.settings-tip {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
}

.no-tasks {
  text-align: center;
  color: #909399;
  padding: 20px;
  font-size: 14px;
}

.task-error .error-text {
  color: #f56c6c;
  font-size: 12px;
}

.task-details span {
  margin-right: 10px;
  font-size: 12px;
  color: #67c23a;
}

.task-params {
  font-size: 12px;
  color: #909399;
}
</style> 