const CHECK_INTERVAL = 500;
const SIZE_THRESHOLD = 160;

let intervalId = null;
let forceDebuggerTimer = null;
let lastKnownOpen = false;

function isLikelyDevToolsOpen() {
  if (typeof window === 'undefined') {
    return false;
  }

  const widthGap = Math.abs(window.outerWidth - window.innerWidth);
  const heightGap = Math.abs(window.outerHeight - window.innerHeight);

  if (widthGap > SIZE_THRESHOLD || heightGap > SIZE_THRESHOLD) {
    return true;
  }

  // 尝试控制台打印检测（仅在开发者工具打开时会触发 getter）
  let detected = false;
  const element = new Image();
  Object.defineProperty(element, 'id', {
    get() {
      detected = true;
      return '';
    }
  });
  console.log(element);
  console.clear();

  return detected;
}

function scheduleDebuggerLoop() {
  if (forceDebuggerTimer) {
    return;
  }

  const runDebugger = () => {
    try {
      // eslint-disable-next-line no-debugger
      debugger;
    } catch (error) {
      // 忽略调用 debugger 可能抛出的异常
    }
  };

  runDebugger();
  forceDebuggerTimer = window.setInterval(() => {
    if (!lastKnownOpen) {
      clearInterval(forceDebuggerTimer);
      forceDebuggerTimer = null;
      return;
    }
    runDebugger();
  }, 300);
}

function stopDebuggerLoop() {
  if (forceDebuggerTimer) {
    clearInterval(forceDebuggerTimer);
    forceDebuggerTimer = null;
  }
}

function onPossibleDevToolsChange(isOpen) {
  lastKnownOpen = isOpen;
  if (isOpen) {
    scheduleDebuggerLoop();
  } else {
    stopDebuggerLoop();
  }
}

function checkDevTools() {
  const detected = isLikelyDevToolsOpen();
  onPossibleDevToolsChange(detected);
}

function handleKeydown(event) {
  const key = event.key?.toLowerCase();
  const forbidden = (
    key === 'f12' ||
    (event.ctrlKey && event.shiftKey && ['i', 'j', 'c', 'u'].includes(key)) ||
    (event.ctrlKey && key === 'u')
  );

  if (forbidden) {
    event.preventDefault();
    event.stopPropagation();
    scheduleDebuggerLoop();
  }
}

function handleContextMenu(event) {
  // 拦截右键菜单，防止通过右键"检查"打开开发者工具
  event.preventDefault();
  event.stopPropagation();
  scheduleDebuggerLoop();
  return false;
}

export function initAntiDebug({ enableInDev = false } = {}) {
  if (typeof window === 'undefined') {
    return () => {};
  }

  const shouldEnable = enableInDev || process.env.NODE_ENV === 'production';
  if (!shouldEnable) {
    return () => {};
  }

  if (intervalId) {
    return () => {
      clearInterval(intervalId);
      intervalId = null;
    };
  }

  checkDevTools();
  intervalId = window.setInterval(checkDevTools, CHECK_INTERVAL);

  window.addEventListener('resize', checkDevTools, true);
  window.addEventListener('focus', checkDevTools, true);
  window.addEventListener('blur', checkDevTools, true);
  window.addEventListener('keydown', handleKeydown, true);
  window.addEventListener('contextmenu', handleContextMenu, true);

  return () => {
    if (intervalId) {
      clearInterval(intervalId);
      intervalId = null;
    }
    stopDebuggerLoop();
    window.removeEventListener('resize', checkDevTools, true);
    window.removeEventListener('focus', checkDevTools, true);
    window.removeEventListener('blur', checkDevTools, true);
    window.removeEventListener('keydown', handleKeydown, true);
    window.removeEventListener('contextmenu', handleContextMenu, true);
  };
}

export default initAntiDebug;


