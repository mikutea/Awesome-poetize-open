<template>
  <transition name="bounce">
    <div
      id="waifu-toggle"
      class="waifu-toggle"
      :class="{ 'waifu-toggle-active': isActive }"
      @click="handleClick"
    >
      <span class="waifu-toggle-text">看板娘</span>
    </div>
  </transition>
</template>

<script>
import { defineComponent, ref } from 'vue'

export default defineComponent({
  name: 'Live2DToggle',
  
  emits: ['click'],
  
  setup(props, { emit }) {
    const isActive = ref(false)
    
    const handleClick = () => {
      isActive.value = false
      emit('click')
    }
    
    return {
      isActive,
      handleClick
    }
  }
})
</script>

<style scoped>
.waifu-toggle {
  position: fixed;
  bottom: 30px;
  right: 30px;
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 998;
  transition: all 0.3s ease;
  animation: pulse 2s infinite;
}

.waifu-toggle:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.waifu-toggle:active {
  transform: scale(0.95);
}

.waifu-toggle-text {
  color: #fff;
  font-size: 12px;
  font-weight: bold;
  text-align: center;
  line-height: 1.2;
  user-select: none;
}

.waifu-toggle-active {
  opacity: 1;
  pointer-events: auto;
}

/* 脉动动画 */
@keyframes pulse {
  0%, 100% {
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
  }
  50% {
    box-shadow: 0 4px 25px rgba(102, 126, 234, 0.6);
  }
}

/* 弹跳动画 */
.bounce-enter-active {
  animation: bounceIn 0.6s ease;
}

.bounce-leave-active {
  animation: bounceOut 0.4s ease;
}

@keyframes bounceIn {
  0% {
    opacity: 0;
    transform: scale(0) translateY(50px);
  }
  50% {
    transform: scale(1.1) translateY(-10px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@keyframes bounceOut {
  0% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0;
    transform: scale(0);
  }
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .waifu-toggle {
    width: 50px;
    height: 50px;
    bottom: 20px;
    right: 20px;
  }
  
  .waifu-toggle-text {
    font-size: 10px;
  }
}
</style>
