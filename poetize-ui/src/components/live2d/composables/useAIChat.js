/**
 * AI聊天逻辑 Composable
 * Vue2.7 Composition API
 */
import { ref, computed, watch, nextTick } from 'vue'
import { useAIChatStore } from '@/stores/aiChat'
import { loadMarkdownResources } from '@/components/live2d/utils/resourceLoader'

export function useAIChat() {
  const store = useAIChatStore()
  
  // 响应式状态
  const inputText = ref('')
  const sending = ref(false)
  const error = ref(null)
  
  // 计算属性
  const messages = computed(() => store.messages)
  const config = computed(() => store.config)
  const streaming = computed(() => store.streaming)
  const typing = computed(() => store.typing)
  const requireLogin = computed(() => store.requireLogin)
  const currentUser = computed(() => store.currentUser)
  const themeColor = computed(() => store.themeColor)
  const editingMessageId = computed(() => store.editingMessageId)
  const isEditing = computed(() => !!store.editingMessageId)
  
  // 监听编辑状态，自动填充输入框
  watch(() => store.editingMessageId, (newId) => {
    if (newId) {
      inputText.value = store.editingContent
    }
  })
  
  /**
   * 初始化聊天
   */
  const init = async () => {
    try {
      // 加载配置
      await store.init()
      
      // 加载Markdown渲染资源
      await loadMarkdownResources()
      
      return true
    } catch (err) {
      console.error('AI聊天初始化失败:', err)
      error.value = err.message
      return false
    }
  }
  
  /**
   * 发送消息
   */
  const sendMessage = async (content = null) => {
    
    // 使用传入的content或inputText
    const messageContent = (content || inputText.value).trim()
    
    if (!messageContent) {
      return
    }
    
    sending.value = true
    error.value = null
    
    try {
      let result
      
      // 如果是编辑模式，先更新编辑内容
      if (isEditing.value) {
        store.editingContent = messageContent
        result = await store.saveEditAndResend()
      } else {
        result = await store.sendMessage(messageContent)
      }
      
      if (result.success) {
        // 清空输入框
        inputText.value = ''
      } else if (result.cancelled) {
        // 用户主动取消，不显示错误消息
        inputText.value = ''
      } else {
        // 清空输入框（即使失败也要清空，因为用户消息已经显示了）
        inputText.value = ''
        
        // 显示错误
        error.value = result.message
        
        // 添加系统提示消息
        if (result.error === 'require_login') {
          store.addMessage(
            `💡 提示：这个功能需要登录后才能使用哦～ [点击这里登录](/user) 就能体验所有功能啦！✨`,
            'assistant'
          )
        } else if (result.error === 'rate_limit' || result.error === 'content_filter') {
          // 速率限制和内容过滤的错误，用系统消息显示
          store.addMessage(`⚠️ ${result.message}`, 'system')
        } else {
          store.addMessage(`⚠️ ${result.message}`, 'system')
        }
      }
    } catch (err) {
      console.error('发送消息失败:', err)
      error.value = '网络错误，请稍后重试'
      
      store.addMessage('抱歉，我现在有点累了，请稍后再试试吧～', 'assistant')
    } finally {
      sending.value = false
    }
  }
  
  /**
   * 清空聊天记录
   */
  const clearHistory = () => {
    if (confirm('确定要清空所有聊天记录吗？此操作不可恢复。')) {
      store.clearHistory()
      return true
    }
    return false
  }
  
  /**
   * 重新加载配置
   */
  const reloadConfig = async () => {
    try {
      await store.loadConfig()
      return true
    } catch (err) {
      console.error('重新加载配置失败:', err)
      return false
    }
  }
  
  /**
   * 添加系统消息
   */
  const addSystemMessage = (content) => {
    store.addMessage(content, 'system')
  }
  
  /**
   * 重新加载用户状态
   */
  const reloadUserStatus = () => {
    store.checkUserLogin()
  }
  
  /**
   * 停止AI生成
   */
  const stopGeneration = () => {
    store.stopGeneration()
  }
  
  /**
   * 取消编辑
   */
  const cancelEdit = () => {
    store.cancelEdit()
    inputText.value = ''
  }
  
  return {
    // 状态
    inputText,
    sending,
    error,
    messages,
    config,
    streaming,
    typing,
    requireLogin,
    currentUser,
    themeColor,
    editingMessageId,
    isEditing,
    
    // 方法
    init,
    sendMessage,
    clearHistory,
    reloadConfig,
    addSystemMessage,
    reloadUserStatus,
    stopGeneration,
    cancelEdit
  }
}
