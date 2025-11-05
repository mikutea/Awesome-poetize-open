<template>
  <transition name="slide-in">
    <div
      v-if="visible"
      id="waifu-chat"
      ref="panelRef"
      class="ai-chat-panel"
      :class="{ fullscreen: isFullscreen, dragging: isDragging, resizing: isResizing }"
      :style="isFullscreen ? {} : { ...panelStyle, width: currentWidth + 'px', height: currentHeight + 'px' }"
      @mousedown.stop
      @touchstart.stop
    >
      <!-- 头部（可拖拽） -->
      <div 
        ref="headerRef" 
        class="chat-header" 
        :style="{ cursor: isFullscreen ? 'default' : (isDragging ? 'grabbing' : 'grab') }"
        @mousedown.stop="handleDragStart"
        @touchstart.stop="handleDragStart"
      >
        <div class="chat-title">
          <svg class="chat-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
            <path d="M512 0C795.989333 0 1024 230.4 1024 512a507.630933 507.630933 0 0 1-186.1632 395.605333v69.8368c0 23.278933-23.278933 46.557867-46.557867 46.557867H491.042133C218.794667 1012.394667 0 786.6368 0 512 0 230.4 228.010667 0 512 0z m204.8 409.6a51.2 51.2 0 0 0-51.2 50.312533v0.887467a153.6 153.6 0 1 1-307.2 0v-0.887467a51.2 51.2 0 0 0-102.4 0.887467v0.887467a256 256 0 0 0 512-0.887467v-0.887467A51.2 51.2 0 0 0 716.8 409.6z" />
          </svg>
          <span>与{{ chatName }}聊天</span>
        </div>
        <div class="chat-actions">
          <button
            class="chat-btn"
            :title="isFullscreen ? '退出全屏' : '全屏'"
            @click="toggleFullscreen"
          >
            <svg v-if="!isFullscreen" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
              <path d="M95.500388 368.593511c0 11.905658-9.637914 21.543572-21.543573 21.543572-11.877311 0-21.515225-9.637914-21.515225-21.543572V188.704684c0-37.502824 15.307275-71.575684 39.997343-96.265751s58.762928-39.997342 96.265751-39.997343h179.888827c11.905658 0 21.543572 9.637914 21.543572 21.515225 0 11.905658-9.637914 21.543572-21.543572 21.543573H188.704684c-25.625512 0-48.926586 10.488318-65.821282 27.383014s-27.383014 40.19577-27.383014 65.821282v179.888827z m559.906101-273.093123c-11.877311 0-21.515225-9.637914-21.515226-21.543573 0-11.877311 9.637914-21.515225 21.515226-21.515225h179.917174c37.502824 0 71.547337 15.307275 96.237404 39.997343s40.025689 58.762928 40.02569 96.265751v179.888827c0 11.905658-9.637914 21.543572-21.543572 21.543572-11.877311 0-21.515225-9.637914-21.515226-21.543572V188.704684c0-25.625512-10.488318-48.926586-27.411361-65.821282-16.894696-16.894696-40.19577-27.383014-65.792935-27.383014h-179.917174z m273.12147 559.906101c0-11.877311 9.637914-21.515225 21.515226-21.515226 11.905658 0 21.543572 9.637914 21.543572 21.515226v179.917174c0 37.474477-15.335622 71.547337-40.02569 96.237404s-58.734581 39.997342-96.237404 39.997343h-179.917174c-11.877311 0-21.515225-9.637914-21.515226-21.515225s9.637914-21.543572 21.515226-21.543573h179.917174c25.597165 0 48.898239-10.488318 65.792935-27.383014 16.923043-16.894696 27.411361-40.19577 27.411361-65.792935v-179.917174z m-559.934448 273.093123c11.905658 0 21.543572 9.666261 21.543572 21.543573s-9.637914 21.515225-21.543572 21.515225H188.704684c-37.502824 0-71.575684-15.307275-96.265751-39.997343s-39.997342-58.762928-39.997343-96.237404v-179.917174c0-11.877311 9.637914-21.515225 21.515225-21.515226 11.905658 0 21.543572 9.637914 21.543573 21.515226v179.917174c0 25.597165 10.488318 48.898239 27.383014 65.792935s40.19577 27.383014 65.821282 27.383014h179.888827z" fill="currentColor"></path>
            </svg>
            <svg v-else viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
              <path d="M704 864v-96c0-54.4 41.6-96 96-96h96c19.2 0 32-12.8 32-32s-12.8-32-32-32h-96c-89.6 0-160 70.4-160 160v96c0 19.2 12.8 32 32 32s32-12.8 32-32z m-64-704v96c0 89.6 70.4 160 160 160h96c19.2 0 32-12.8 32-32s-12.8-32-32-32h-96c-54.4 0-96-41.6-96-96v-96c0-19.2-12.8-32-32-32s-32 12.8-32 32z m-256 704v-96c0-89.6-70.4-160-160-160h-96c-19.2 0-32 12.8-32 32s12.8 32 32 32h96c54.4 0 96 41.6 96 96v96c0 19.2 12.8 32 32 32s32-12.8 32-32z m-64-704v96c0 54.4-41.6 96-96 96h-96c-19.2 0-32 12.8-32 32s12.8 32 32 32h96c89.6 0 160-70.4 160-160v-96c0-19.2-12.8-32-32-32s-32 12.8-32 32z" fill="currentColor"></path>
            </svg>
          </button>
          <button
            class="chat-btn"
            title="清空历史"
            @click="handleClear"
          >
            <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
              <path d="M416 384c-19.2 0-32 12.8-32 32v320c0 19.2 12.8 32 32 32s32-12.8 32-32v-320c0-19.2-12.8-32-32-32z" fill="currentColor"></path>
              <path d="M928 192h-224v-32c0-54.4-41.6-96-96-96h-192c-54.4 0-96 41.6-96 96v32h-224c-19.2 0-32 12.8-32 32s12.8 32 32 32h64v608c0 54.4 41.6 96 96 96h512c54.4 0 96-41.6 96-96v-608h64c19.2 0 32-12.8 32-32s-12.8-32-32-32z m-544-32c0-19.2 12.8-32 32-32h192c19.2 0 32 12.8 32 32v32h-256v-32z m416 704c0 19.2-12.8 32-32 32h-512c-19.2 0-32-12.8-32-32v-608h576v608z" fill="currentColor"></path>
              <path d="M608 384c-19.2 0-32 12.8-32 32v320c0 19.2 12.8 32 32 32s32-12.8 32-32v-320c0-19.2-12.8-32-32-32z" fill="currentColor"></path>
            </svg>
          </button>
          <button
            class="chat-btn"
            title="关闭"
            @click="handleClose"
          >
            ×
          </button>
        </div>
      </div>
      
      <!-- 消息列表 -->
      <AIChatMessages
        :messages="messages"
        :streaming="streaming"
        :typing="typing"
      />
      
      <!-- 输入框 -->
      <AIChatInput
        v-model="inputText"
        :sending="sending"
        :streaming="chat.streaming.value || chat.typing.value"
        :is-editing="chat.isEditing.value"
        :placeholder="placeholder"
        @send="handleSend"
        @stop="handleStop"
        @cancel-edit="handleCancelEdit"
        @page-attached="handlePageAttached"
        @page-removed="handlePageRemoved"
      />
      
      <!-- 缩放控制点（仅非全屏时显示） -->
      <template v-if="!isFullscreen">
        <div class="resize-handle resize-e" @mousedown.prevent.stop="(e) => handleResizeStart(e, 'e')" @touchstart.prevent.stop="(e) => handleResizeStart(e, 'e')"></div>
        <div class="resize-handle resize-s" @mousedown.prevent.stop="(e) => handleResizeStart(e, 's')" @touchstart.prevent.stop="(e) => handleResizeStart(e, 's')"></div>
        <div class="resize-handle resize-se" @mousedown.prevent.stop="(e) => handleResizeStart(e, 'se')" @touchstart.prevent.stop="(e) => handleResizeStart(e, 'se')"></div>
      </template>
    </div>
  </transition>
</template>

<script>
import { computed, onMounted, ref, watch } from 'vue'
import { useAIChat } from '../composables/useAIChat'
import { useLive2DStore } from '@/stores/live2d'
import { useChatDrag } from '../composables/useChatDrag'
import { useChatResize } from '../composables/useChatResize'
import AIChatMessages from './AIChatMessages.vue'
import AIChatInput from './AIChatInput.vue'

export default {
  name: 'AIChatPanel',
  
  components: {
    AIChatMessages,
    AIChatInput
  },
  
  setup() {
    const live2dStore = useLive2DStore()
    const chat = useAIChat()
    const headerRef = ref(null)
    const panelRef = ref(null)
    
    // 全屏状态
    const isFullscreen = ref(false)
    
    // 拖拽功能
    const { isDragging, panelStyle, handleDragStart: dragStart, resetDragging } = useChatDrag(headerRef)
    
    // 缩放功能
    const { isResizing, currentWidth, currentHeight, handleResizeStart, resetResizing } = useChatResize(panelRef)
    
    // 包装拖拽开始，检查全屏状态
    const handleDragStart = (e) => {
      // 如果点击的是按钮或SVG，不触发拖拽
      if (e.target.closest('button') || e.target.closest('.chat-actions')) {
        return
      }
      
      // 全屏时禁用拖拽
      if (isFullscreen.value) return
      
      // 阻止默认行为和事件冒泡（只在确定要拖拽时）
      e.preventDefault()
      
      dragStart(e)
    }
    
    // 计算属性
    const visible = computed(() => live2dStore.showChat)
    const chatName = computed(() => chat.config.value?.chat_name || 'AI助手')
    const placeholder = computed(() => chat.config.value?.placeholder_text || '输入你想说的话...')
    const typing = computed(() => chat.typing.value)
    
    // 监听聊天窗口状态变化
    watch(visible, (isVisible) => {
      if (isVisible) {
        // 打开时重新检查登录状态（用户可能刚登录）
        chat.reloadUserStatus && chat.reloadUserStatus()
      } else {
        // 关闭时重置拖拽和缩放状态，避免pointer-events残留
        resetDragging()
        resetResizing()
      }
    })
    
    // 定时清理状态（保险措施，防止极端情况下状态卡住）
    let cleanupTimer = null
    watch([isDragging, isResizing], ([dragging, resizing]) => {
      if (dragging || resizing) {
        if (cleanupTimer) clearTimeout(cleanupTimer)
        cleanupTimer = setTimeout(() => {
          resetDragging()
          resetResizing()
        }, 3000)
      } else {
        if (cleanupTimer) {
          clearTimeout(cleanupTimer)
          cleanupTimer = null
        }
      }
    })
    
    /**
     * 发送消息
     */
    const handleSend = async () => {
      await chat.sendMessage()
    }
    
    /**
     * 停止AI生成
     */
    const handleStop = () => {
      chat.stopGeneration()
      live2dStore.showMessage('已停止生成', 2000, 10)
    }
    
    /**
     * 取消编辑
     */
    const handleCancelEdit = () => {
      chat.cancelEdit()
      live2dStore.showMessage('已取消编辑', 2000, 9)
    }
    
    /**
     * 清空历史
     */
    const handleClear = () => {
      const success = chat.clearHistory()
      if (success) {
        live2dStore.showMessage('聊天记录已清空！', 2000, 8)
      }
    }
    
    /**
     * 关闭聊天
     */
    const handleClose = () => {
      // 立即退出全屏，避免z-index阻挡页面
      isFullscreen.value = false
      // 立即重置拖拽和缩放状态，避免pointer-events残留
      resetDragging()
      resetResizing()
      live2dStore.toggleChat()
    }
    
    /**
     * 切换全屏
     */
    const toggleFullscreen = () => {
      // 切换全屏时重置拖拽和缩放状态
      resetDragging()
      resetResizing()
      isFullscreen.value = !isFullscreen.value
    }
    
    /**
     * 页面附加成功
     */
    const handlePageAttached = (pageContext) => {
      live2dStore.showMessage(`📎 已附加：${pageContext.title}`, 3000, 10)
    }
    
    /**
     * 页面移除
     */
    const handlePageRemoved = () => {
      live2dStore.showMessage('🗑️ 已移除附加的页面', 2000, 10)
    }
    
    // 初始化
    onMounted(async () => {
      await chat.init()
    })
    
    return {
      headerRef,
      panelRef,
      isDragging,
      isResizing,
      panelStyle,
      currentWidth,
      currentHeight,
      visible,
      chatName,
      placeholder,
      messages: chat.messages,
      inputText: chat.inputText,
      sending: chat.sending,
      streaming: chat.streaming,
      typing,
      isFullscreen,
      handleSend,
      handleStop,
      handleCancelEdit,
      handleClear,
      handleClose,
      toggleFullscreen,
      handleDragStart,
      handleResizeStart,
      handlePageAttached,
      handlePageRemoved,
      chat
    }
  }
}
</script>

<style scoped>
.ai-chat-panel {
  position: fixed;
  bottom: 200px;
  left: 300px;
  /* width和height通过style动态设置 */
  min-width: 300px;
  min-height: 400px;
  max-width: 800px;
  max-height: 800px;
  /* 彩色渐变背景 */
  /* background: linear-gradient(-45deg, #e8d8b9, #eccec5, #a3e9eb, #bdbdf0, #eec1ea);
  background-size: 400% 400%;
  animation: gradientShift 15s ease infinite; */
  /* 白色背景 */
  background: #ecf0f1;
  border-radius: 20px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  z-index: 1000;
  overflow: hidden;
  backdrop-filter: blur(10px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 拖拽/缩放时禁用transition，实现即时响应 */
.ai-chat-panel.dragging,
.ai-chat-panel.resizing {
  transition: none !important;
  user-select: none !important;
}

.ai-chat-panel.dragging > *:not(.chat-header):not(.resize-handle),
.ai-chat-panel.resizing > *:not(.chat-header):not(.resize-handle) {
  user-select: none !important;
  pointer-events: none !important;
}

.ai-chat-panel.dragging .chat-header,
.ai-chat-panel.dragging .chat-header *,
.ai-chat-panel.resizing .chat-header,
.ai-chat-panel.resizing .chat-header *,
.ai-chat-panel.resizing .resize-handle {
  pointer-events: auto !important;
}

/* 确保按钮始终可点击 */
.chat-actions,
.chat-actions *,
.chat-btn,
.chat-btn * {
  pointer-events: auto !important;
}

/* 缩放控制点 */
.resize-handle {
  position: absolute;
  z-index: 10;
}

.resize-e {
  right: 0;
  top: 0;
  width: 8px;
  height: 100%;
  cursor: ew-resize;
}

.resize-s {
  bottom: 0;
  left: 0;
  width: 100%;
  height: 8px;
  cursor: ns-resize;
}

.resize-se {
  right: 0;
  bottom: 0;
  width: 20px;
  height: 20px;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 0%, transparent 50%, rgba(0, 0, 0, 0.1) 50%, rgba(0, 0, 0, 0.1) 100%);
  border-bottom-right-radius: 20px;
}

.resize-se::after {
  content: '';
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 8px;
  height: 8px;
  border-right: 2px solid rgba(0, 0, 0, 0.3);
  border-bottom: 2px solid rgba(0, 0, 0, 0.3);
}

.resize-handle:hover {
  background-color: rgba(102, 126, 234, 0.1);
}

/* 全屏样式 */
.ai-chat-panel.fullscreen {
  top: 0 !important;
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
  border-radius: 0 !important;
  max-width: 100% !important;
  max-height: 100% !important;
  z-index: 9999 !important;
}

/* 输入框容器在较大窗口时居中内容 */
.ai-chat-panel >>> .chat-input-container {
  justify-content: center;
}

/* 输入框限制最大宽度（适用于所有模式） */
.ai-chat-panel >>> .chat-input {
  max-width: 550px;
}

/* 发送按钮保持原有样式 */
.ai-chat-panel >>> .send-btn {
  flex-shrink: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 20px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  color: #2c3e50;
  user-select: none;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: bold;
}

.chat-icon {
  width: 20px;
  height: 20px;
  fill: currentColor;
}

.chat-actions {
  display: flex;
  gap: 8px;
}

.chat-btn {
  width: 32px;
  height: 32px;
  background: rgba(255, 255, 255, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 8px;
  color: #2c3e50;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
}

.chat-btn:hover {
  background: rgba(255, 255, 255, 0.5);
  transform: scale(1.05);
}

.chat-btn:active {
  transform: scale(0.95);
  background: rgba(255, 255, 255, 0.6);
}

.chat-btn svg {
  width: 16px;
  height: 16px;
  fill: currentColor;
}

/* 滑入动画 */
.slide-in-enter-active,
.slide-in-leave-active {
  transition: all 0.3s ease;
}

.slide-in-leave-active {
  pointer-events: none !important; /* 离开动画时立即禁用点击，避免阻挡页面 */
}

.slide-in-enter,
.slide-in-leave-to {
  opacity: 0;
  transform: translateX(50px);
}

/* 深色模式 */
.dark-mode .ai-chat-panel {
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  color: #ecf0f1;
}

.dark-mode .chat-header {
  color: #ecf0f1;
}

.dark-mode .chat-btn {
  color: #ecf0f1;
}

/* 移动端适配 - 默认全屏，但允许拖拽缩放调整 */
@media screen and (max-width: 768px) {
  .ai-chat-panel {
    /* 移动端默认位置，用户可以通过拖拽调整 */
    bottom: 0;
    right: 0;
    left: 0;
    top: 0;
    width: 100%;
    height: 100vh;
    border-radius: 0;
  }
}
</style>
