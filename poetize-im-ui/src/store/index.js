import {createStore} from 'vuex'

// 从localStorage安全地解析JSON数据
function safeParseJSON(key, defaultValue = {}) {
  try {
    const item = localStorage.getItem(key);
    // 处理 null、undefined 或空字符串
    if (!item || item === 'null' || item === 'undefined') {
      return defaultValue;
    }
    const parsed = JSON.parse(item);
    // 确保解析结果不是 null 或 undefined
    return (parsed === null || parsed === undefined) ? defaultValue : parsed;
  } catch (error) {
    console.warn(`解析localStorage中的${key}失败:`, error);
    return defaultValue;
  }
}

// 安全地保存数据到localStorage
function safeSaveToStorage(key, data) {
  try {
    localStorage.setItem(key, JSON.stringify(data));
  } catch (error) {
    console.error(`保存${key}到localStorage失败:`, error);
    // 如果存储空间不足，清理旧的聊天数据
    if (error.name === 'QuotaExceededError') {
      console.warn('localStorage空间不足，清理旧数据...');
      cleanOldChatData();
      // 重试保存
      try {
        localStorage.setItem(key, JSON.stringify(data));
      } catch (retryError) {
        console.error(`重试保存${key}失败:`, retryError);
      }
    }
  }
}

// 清理旧的聊天数据（只清理消息缓存，聊天列表从后端同步）
function cleanOldChatData() {
  const keysToClean = ['imMessages', 'groupMessages'];
  keysToClean.forEach(key => {
    try {
      const data = safeParseJSON(key, {});
      // 只保留最近的消息记录
      const cleanedData = {};
      Object.keys(data).forEach(chatId => {
        if (Array.isArray(data[chatId]) && data[chatId].length > 0) {
          // 只保留最近50条消息
          cleanedData[chatId] = data[chatId].slice(-50);
        }
      });
      localStorage.setItem(key, JSON.stringify(cleanedData));
    } catch (error) {
      console.error(`清理${key}失败:`, error);
    }
  });
  
  // 清理已废弃的聊天列表和未读数缓存
  ['imChats', 'groupChats', 'imMessageBadge', 'groupMessageBadge'].forEach(key => {
    localStorage.removeItem(key);
  });
}

export default createStore({
  state: {
    currentUser: safeParseJSON("currentUser", {}),
    sysConfig: safeParseJSON("sysConfig", {}),
    onlineUserCount: {},  // 存储各群组的在线用户数 {groupId: count}
    
    // 聊天相关数据（聊天列表从后端获取，不再持久化到localStorage）
    imChats: [],           // 私聊列表（从后端同步）
    groupChats: [],        // 群聊列表（从后端同步）
    imMessages: safeParseJSON("imMessages", {}),     // 私聊消息（临时缓存）
    groupMessages: safeParseJSON("groupMessages", {}), // 群聊消息（临时缓存）
    imMessageBadge: {},    // 私聊未读数（从后端同步）
    groupMessageBadge: {}, // 群聊未读数（从后端同步）
    lastMessagePreviews: {}  // 最后一条消息预览 {chatId: {content, createTime}}
  },
  getters: {},
  mutations: {
    loadCurrentUser(state, user) {
      state.currentUser = user;
      safeSaveToStorage("currentUser", user);
    },
    loadSysConfig(state, sysConfig) {
      state.sysConfig = sysConfig;
      safeSaveToStorage("sysConfig", sysConfig);
    },
    updateOnlineUserCount(state, {groupId, count}) {
      state.onlineUserCount[groupId] = count;
    },
    
    // 聊天列表相关mutations（不再持久化到localStorage，从后端同步）
    updateImChats(state, chats) {
      state.imChats = [...chats];
    },
    updateGroupChats(state, chats) {
      state.groupChats = [...chats];
    },
    
    // 消息相关mutations
    updateImMessages(state, {friendId, messages}) {
      state.imMessages = {
        ...state.imMessages,
        [friendId]: [...messages]
      };
      safeSaveToStorage("imMessages", state.imMessages);
    },
    updateGroupMessages(state, {groupId, messages}) {
      state.groupMessages = {
        ...state.groupMessages,
        [groupId]: [...messages]
      };
      safeSaveToStorage("groupMessages", state.groupMessages);
    },
    
    // 添加单条消息
    addImMessage(state, {friendId, message}) {
      if (!state.imMessages[friendId]) {
        state.imMessages[friendId] = [];
      }
      state.imMessages[friendId].push(message);
      safeSaveToStorage("imMessages", state.imMessages);
    },
    addGroupMessage(state, {groupId, message}) {
      if (!state.groupMessages[groupId]) {
        state.groupMessages[groupId] = [];
      }
      state.groupMessages[groupId].push(message);
      safeSaveToStorage("groupMessages", state.groupMessages);
    },
    
    // 未读消息数相关mutations（不再持久化到localStorage，从后端同步）
    updateImMessageBadge(state, {friendId, count}) {
      state.imMessageBadge = {
        ...state.imMessageBadge,
        [friendId]: count
      };
    },
    updateGroupMessageBadge(state, {groupId, count}) {
      state.groupMessageBadge = {
        ...state.groupMessageBadge,
        [groupId]: count
      };
    },
    
    // 最后消息预览相关mutations
    updateLastMessagePreview(state, {chatId, preview}) {
      state.lastMessagePreviews = {
        ...state.lastMessagePreviews,
        [chatId]: preview
      };
    },
    
    // 清空所有聊天数据（聊天列表和未读数从后端获取，只清理消息缓存）
    clearAllChatData(state) {
      state.imChats = [];
      state.groupChats = [];
      state.imMessages = {};
      state.groupMessages = {};
      state.imMessageBadge = {};
      state.groupMessageBadge = {};
      state.lastMessagePreviews = {};
      
      // 只清除消息缓存的localStorage
      const keysToRemove = ['imMessages', 'groupMessages'];
      keysToRemove.forEach(key => {
        localStorage.removeItem(key);
      });
    }
  },
  actions: {
    // 批量更新聊天数据（聊天列表和未读数从WebSocket同步，不需要批量更新）
    updateChatData({commit}, {imMessages, groupMessages}) {
      if (imMessages !== undefined) {
        Object.keys(imMessages).forEach(friendId => {
          commit('updateImMessages', {friendId, messages: imMessages[friendId]});
        });
      }
      if (groupMessages !== undefined) {
        Object.keys(groupMessages).forEach(groupId => {
          commit('updateGroupMessages', {groupId, messages: groupMessages[groupId]});
        });
      }
    }
  },
  modules: {},
  plugins: []
})
