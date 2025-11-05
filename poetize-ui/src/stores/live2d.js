/**
 * Live2D看板娘 - Pinia Store
 * Vue2.7版本
 */
import { defineStore } from 'pinia'
import constant from '@/utils/constant'

export const useLive2DStore = defineStore('live2d', {
  state: () => ({
    // 基础状态
    enabled: false,
    visible: true,
    initialized: false,
    
    // 模型相关
    currentModelId: parseInt(localStorage.getItem('modelId')) || 5,
    currentTextureId: parseInt(localStorage.getItem('modelTexturesId')) || 0,
    modelList: null,
    modelLoading: false,
    
    // 位置和交互
    position: (() => {
      const saved = localStorage.getItem('waifu-position')
      if (!saved) return { x: null, y: null }
      const parsed = JSON.parse(saved)
      // 如果保存的是{x:0, y:0}，也认为是未设置（历史数据兼容）
      if (parsed.x === 0 && parsed.y === 0) return { x: null, y: null }
      return parsed
    })(),
    dragging: false,
    
    // 聊天窗口
    showChat: false,
    
    // 提示消息
    messageQueue: [],
    currentMessage: null,
    messageTimer: null
  }),

  getters: {
    /**
     * 是否处于激活状态
     */
    isActive: (state) => state.enabled && state.visible,
    
    /**
     * 是否可以交互
     */
    canInteract: (state) => state.enabled && state.visible && !state.dragging,
    
    /**
     * 当前模型信息
     */
    currentModel: (state) => {
      if (!state.modelList || !state.modelList.models) return null
      const models = state.modelList.models[state.currentModelId]
      if (!models) return null
      // 兼容字符串和数组格式
      return Array.isArray(models) ? (models[state.currentTextureId] || models[0]) : models
    },
    
  },

  actions: {
    /**
     * 初始化看板娘
     */
    async init() {
      
      try {
        // 检查屏幕尺寸
        if (window.screen.width <= 768) {
          this.enabled = false
          return false
        }
        
        // 检查后端状态
        const enabled = await this.checkEnabled()
        this.enabled = enabled
        
        if (!enabled) {
          return false
        }
        
        // 检查用户是否手动关闭
        const displayTime = localStorage.getItem('waifu-display')
        if (displayTime && Date.now() - parseInt(displayTime) <= 86400000) {
          this.visible = false
          return false
        }
        
        this.initialized = true
        
        return true
        
      } catch (error) {
        console.error('Live2D Store初始化失败:', error)
        this.enabled = false
        return false
      }
    },
    
    /**
     * 从后端检查是否启用看板娘
     */
    async checkEnabled() {
      try {
        const response = await fetch(constant.baseURL + '/webInfo/getWaifuStatus')
        const result = await response.json()
        
        if (result.code === 200) {
          return result.data?.enableWaifu === true
        }
        
        // 降级：从localStorage读取
        const webInfo = localStorage.getItem('webInfo')
        if (webInfo) {
          const data = JSON.parse(webInfo)
          return data.data?.enableWaifu === true || data.enableWaifu === true
        }
        
        return false
      } catch (error) {
        console.error('检查看板娘状态失败:', error)
        return false
      }
    },
    
    /**
     * 加载模型列表
     */
    async loadModelList() {
      // 检查缓存
      const cacheKey = 'model-list-cache'
      const cacheTimeKey = 'model-list-cache-time'
      const cacheDuration = 24 * 60 * 60 * 1000 // 1天
      
      const cachedTime = localStorage.getItem(cacheTimeKey)
      const now = Date.now()
      
      if (cachedTime && (now - parseInt(cachedTime)) < cacheDuration) {
        try {
          const cachedData = localStorage.getItem(cacheKey)
          if (cachedData) {
            this.modelList = JSON.parse(cachedData)
            return this.modelList
          }
        } catch (e) {
          console.error('缓存解析失败:', e)
        }
      }
      
      try {
        const cdnPath = constant.cdnPath
        const response = await fetch(`${cdnPath}model_list.json?t=${now}`)
        
        if (!response.ok) {
          throw new Error(`模型列表响应错误: ${response.status}`)
        }
        
        this.modelList = await response.json()
        
        // 更新缓存
        localStorage.setItem(cacheKey, JSON.stringify(this.modelList))
        localStorage.setItem(cacheTimeKey, now.toString())
        
        return this.modelList
        
      } catch (error) {
        console.error('模型列表加载失败:', error)
        
        // 使用备用数据
        this.modelList = {
          models: [['HyperdimensionNeptunia/blanc_swimwear']],
          messages: ['我是备用模型']
        }
        
        return this.modelList
      }
    },
    
    /**
     * 加载指定模型
     */
    async loadModel(modelId, message) {
      if (!this.modelList) {
        await this.loadModelList()
      }
      
      this.modelLoading = true
      this.currentModelId = modelId
      localStorage.setItem('modelId', modelId)
      
      // 验证并重置材质ID（确保不超过新模型的材质数量）
      const models = this.modelList.models[modelId]
      const maxTextureId = Array.isArray(models) ? models.length - 1 : 0
      if (this.currentTextureId > maxTextureId) {
        this.currentTextureId = 0
        localStorage.setItem('modelTexturesId', 0)
      }
      
      if (message) {
        this.showMessage(message, 4000, 10)
      }
      
      // 触发模型加载（实际渲染由Canvas组件处理）
      
      this.modelLoading = false
    },
    
    /**
     * 切换材质
     */
    changeTexture() {
      if (!this.modelList) return
      
      const textures = this.modelList.models[this.currentModelId]
      // 检查是否是数组且有多个材质
      if (!Array.isArray(textures) || textures.length <= 1) {
        this.showMessage('当前模型没有其他材质哦～', 3000, 10)
        return
      }
      
      this.currentTextureId = (this.currentTextureId + 1) % textures.length
      localStorage.setItem('modelTexturesId', this.currentTextureId)
      
      this.showMessage('新衣服好看吗？', 3000, 10)
      this.loadModel(this.currentModelId)
    },
    
    /**
     * 随机切换模型
     */
    loadRandomModel() {
      if (!this.modelList) return
      
      const modelCount = this.modelList.models.length
      let newModelId = Math.floor(Math.random() * modelCount)
      
      // 避免重复
      if (newModelId === this.currentModelId && modelCount > 1) {
        newModelId = (newModelId + 1) % modelCount
      }
      
      this.currentTextureId = 0
      localStorage.setItem('modelTexturesId', 0)
      
      this.loadModel(newModelId, '看看我的新造型吧！')
    },
    
    /**
     * 显示消息
     */
    showMessage(text, timeout = 3000, priority = 5) {
      if (!text) return
      
      // 优先级检查
      const currentPriority = parseInt(sessionStorage.getItem('waifu-text') || '0')
      if (currentPriority > priority) return
      
      // 清除旧定时器
      if (this.messageTimer) {
        clearTimeout(this.messageTimer)
        this.messageTimer = null
      }
      
      // 随机选择文本（如果是数组）
      if (Array.isArray(text)) {
        text = text[Math.floor(Math.random() * text.length)]
      }
      
      this.currentMessage = { text, priority }
      sessionStorage.setItem('waifu-text', priority.toString())
      
      // 设置自动清除
      this.messageTimer = setTimeout(() => {
        this.currentMessage = null
        sessionStorage.removeItem('waifu-text')
        this.messageTimer = null
      }, timeout)
    },
    
    /**
     * 切换聊天窗口
     */
    async toggleChat() {
      this.showChat = !this.showChat
      
      if (this.showChat) {
        this.showMessage('来聊天吧！我很想听听你的想法～', 3000, 8)
        
        // 延迟加载AI聊天配置（仅在打开聊天窗口时）
        try {
          const { useAIChatStore } = await import('./aiChat')
          const aiChatStore = useAIChatStore()
          
          // 如果配置未加载，现在加载
          if (!aiChatStore.configLoaded) {
            await aiChatStore.init()
          }
        } catch (error) {
          console.error('加载AI聊天配置失败:', error)
        }
      } else {
        this.showMessage('有什么想聊的随时找我哦！', 3000, 8)
      }
    },
    
    /**
     * 更新位置
     */
    updatePosition(x, y) {
      this.position = { x, y }
      localStorage.setItem('waifu-position', JSON.stringify(this.position))
    },
    
    /**
     * 开始拖拽
     */
    startDragging() {
      this.dragging = true
    },
    
    /**
     * 结束拖拽
     */
    stopDragging() {
      this.dragging = false
    },
    
    /**
     * 显示看板娘
     */
    show() {
      this.visible = true
      localStorage.removeItem('waifu-display')
    },
    
    /**
     * 隐藏看板娘
     */
    hide() {
      this.visible = false
      this.showChat = false
      localStorage.setItem('waifu-display', Date.now().toString())
    }
  }
})
