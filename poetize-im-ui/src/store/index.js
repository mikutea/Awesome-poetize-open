import {createStore} from 'vuex'

// 从localStorage安全地解析JSON数据
function safeParseJSON(key, defaultValue = {}) {
  try {
    const item = localStorage.getItem(key);
    return item ? JSON.parse(item) : defaultValue;
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

// 清理旧的聊天数据
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
}

export default createStore({
  state: {
    currentUser: safeParseJSON("currentUser", {}),
    sysConfig: safeParseJSON("sysConfig", {}),
    onlineUserCount: {},  // 存储各群组的在线用户数 {groupId: count}
    
    // 聊天相关数据持久化
    imChats: safeParseJSON("imChats", []),           // 私聊列表
    groupChats: safeParseJSON("groupChats", []),     // 群聊列表
    imMessages: safeParseJSON("imMessages", {}),     // 私聊消息
    groupMessages: safeParseJSON("groupMessages", {}), // 群聊消息
    imMessageBadge: safeParseJSON("imMessageBadge", {}), // 私聊未读数
    groupMessageBadge: safeParseJSON("groupMessageBadge", {}) // 群聊未读数
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
    
    // 聊天列表相关mutations
    updateImChats(state, chats) {
      state.imChats = [...chats];
      safeSaveToStorage("imChats", state.imChats);
    },
    updateGroupChats(state, chats) {
      state.groupChats = [...chats];
      safeSaveToStorage("groupChats", state.groupChats);
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
    
    // 未读消息数相关mutations
    updateImMessageBadge(state, {friendId, count}) {
      state.imMessageBadge = {
        ...state.imMessageBadge,
        [friendId]: count
      };
      safeSaveToStorage("imMessageBadge", state.imMessageBadge);
    },
    updateGroupMessageBadge(state, {groupId, count}) {
      state.groupMessageBadge = {
        ...state.groupMessageBadge,
        [groupId]: count
      };
      safeSaveToStorage("groupMessageBadge", state.groupMessageBadge);
    },
    
    // 清空所有聊天数据
    clearAllChatData(state) {
      state.imChats = [];
      state.groupChats = [];
      state.imMessages = {};
      state.groupMessages = {};
      state.imMessageBadge = {};
      state.groupMessageBadge = {};
      
      // 清除localStorage中的数据
      const keysToRemove = ['imChats', 'groupChats', 'imMessages', 'groupMessages', 'imMessageBadge', 'groupMessageBadge'];
      keysToRemove.forEach(key => {
        localStorage.removeItem(key);
      });
    }
  },
  actions: {
    // 批量更新聊天数据
    updateChatData({commit}, {imChats, groupChats, imMessages, groupMessages, imMessageBadge, groupMessageBadge}) {
      if (imChats !== undefined) commit('updateImChats', imChats);
      if (groupChats !== undefined) commit('updateGroupChats', groupChats);
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
      if (imMessageBadge !== undefined) {
        Object.keys(imMessageBadge).forEach(friendId => {
          commit('updateImMessageBadge', {friendId, count: imMessageBadge[friendId]});
        });
      }
      if (groupMessageBadge !== undefined) {
        Object.keys(groupMessageBadge).forEach(groupId => {
          commit('updateGroupMessageBadge', {groupId, count: groupMessageBadge[groupId]});
        });
      }
    }
  },
  modules: {},
  plugins: []
})
