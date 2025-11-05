<template>
  <div>
    <!-- Live2D看板娘模式 -->
    <Live2DWidget v-if="mode === 'live2d'" />
    
    <!-- 简单按钮模式 -->
    <AIChatButton v-else-if="mode === 'button'" />
    
    <!-- AI聊天面板（懒加载） -->
    <AIChatPanel v-if="showChat" />
  </div>
</template>

<script>
import { computed } from 'vue'
import { useLive2DStore } from '@/stores/live2d'
import Live2DWidget from './Live2DWidget.vue'
import AIChatButton from './AIChatButton.vue'

export default {
  name: 'Live2DIndex',
  
  components: {
    Live2DWidget,
    AIChatButton,
    // AI聊天面板懒加载
    AIChatPanel: () => import('./AIChat/index.vue')
  },
  
  props: {
    // 显示模式：'live2d' | 'button' | 'auto'
    // 'auto' 会根据live2d.enabled自动选择
    mode: {
      type: String,
      default: 'auto',
      validator: (value) => ['live2d', 'button', 'auto'].includes(value)
    }
  },
  
  setup(props) {
    const store = useLive2DStore()
    
    // 是否显示聊天窗口
    const showChat = computed(() => store.showChat)
    
    // 实际显示模式
    const mode = computed(() => {
      if (props.mode === 'auto') {
        // 自动模式：如果live2d启用则显示live2d，否则显示按钮
        return store.enabled ? 'live2d' : 'button'
      }
      return props.mode
    })
    
    return {
      showChat,
      mode
    }
  }
}
</script>

<style>
/* 全局样式导入 */
@import './styles/live2d.css';
</style>
