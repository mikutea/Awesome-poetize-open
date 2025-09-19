import {useStore} from 'vuex';

import {useDialog} from 'naive-ui';

import {nextTick} from 'vue';

import {ElMessage} from "element-plus";

import {reactive, getCurrentInstance, onMounted, onBeforeUnmount, watchEffect, toRefs} from 'vue';

export default function () {
  const globalProperties = getCurrentInstance().appContext.config.globalProperties;
  const $common = globalProperties.$common;
  const $http = globalProperties.$http;
  const $constant = globalProperties.$constant;
  const store = useStore();
  const dialog = useDialog();

  let groupData = reactive({
    //群组列表
    groups: {},
    //当前群信息
    currentGroupId: null
  })

  function exitGroup(currentGroupId) {
    $http.get($constant.baseURL + "/imChatGroupUser/quitGroup", {id: currentGroupId})
      .then((res) => {
        // 删除群组信息
        delete groupData.groups[currentGroupId];
        groupData.currentGroupId = null;
        
        // 🔥 关键：同步清理localStorage中的群聊相关数据
        removeGroupFromLocalStorage(currentGroupId);
        
        ElMessage({
          message: "退群成功！",
          type: 'success'
        });
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  function dissolveGroup(currentGroupId) {
    $http.get($constant.baseURL + "/imChatGroup/deleteGroup", {id: currentGroupId})
      .then((res) => {
        // 删除群组信息
        delete groupData.groups[currentGroupId];
        groupData.currentGroupId = null;
        
        // 🔥 关键：同步清理localStorage中的群聊相关数据
        removeGroupFromLocalStorage(currentGroupId);
        
        ElMessage({
          message: "解散群成功！",
          type: 'success'
        });
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  // 🆕 新增：从localStorage中移除群聊相关数据的函数
  function removeGroupFromLocalStorage(groupId) {
    try {
      // 1. 从群聊列表中移除
      const currentGroupChats = JSON.parse(localStorage.getItem('groupChats') || '[]');
      const updatedGroupChats = currentGroupChats.filter(chatGroupId => chatGroupId !== groupId);
      localStorage.setItem('groupChats', JSON.stringify(updatedGroupChats));
      
      // 2. 删除群聊消息记录
      const groupMessages = JSON.parse(localStorage.getItem('groupMessages') || '{}');
      if (groupMessages[groupId]) {
        delete groupMessages[groupId];
        localStorage.setItem('groupMessages', JSON.stringify(groupMessages));
      }
      
      // 3. 删除群聊未读消息数
      const groupMessageBadge = JSON.parse(localStorage.getItem('groupMessageBadge') || '{}');
      if (groupMessageBadge[groupId]) {
        delete groupMessageBadge[groupId];
        localStorage.setItem('groupMessageBadge', JSON.stringify(groupMessageBadge));
      }
      
      // 4. 同步更新Vuex store
      store.commit('updateGroupChats', updatedGroupChats);
      
      console.log(`✅ 已清理群聊 ${groupId} 的所有本地数据`);
      
    } catch (error) {
      console.error('清理群聊本地数据失败:', error);
    }
  }

  async function getImGroup() {
    try {
      const res = await $http.get($constant.baseURL + "/imChatGroup/listGroup");
      if (res && res.code === 200 && res.data) {
        groupData.groups = {};
        if (Array.isArray(res.data)) {
          res.data.forEach(group => {
            if (group && group.id) {
              // 确保每个群组都有必要的属性
              groupData.groups[group.id] = {
                id: group.id,
                groupName: group.groupName || '未知群聊',
                avatar: group.avatar || '',
                groupType: group.groupType || 1,
                masterFlag: group.masterFlag || false,
                adminFlag: group.adminFlag || false,
                ...group
              };
            }
          });
        }
        console.log('群组数据加载成功:', Object.keys(groupData.groups).length, '个群组');
        return true;
      } else {
        console.error('获取群组数据失败:', res);
        return false;
      }
    } catch (error) {
      console.error('获取群组数据时发生错误:', error);
      ElMessage({
        message: error.message || '获取群组数据失败',
        type: 'error'
      });
      return false;
    }
  }

  function addGroupTopic() {
    $http.get($constant.baseURL + "/imChatGroup/addGroupTopic", {id: groupData.currentGroupId})
      .then((res) => {
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  // 🆕 新增：手动删除群聊列表项（不退群，只是从本地列表中移除）
  function removeGroupFromList(groupId) {
    dialog.warning({
      title: '确认删除',
      content: '确定要从聊天列表中删除这个群聊吗？（不会退出群聊，只是从列表中移除）',
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: () => {
        removeGroupFromLocalStorage(groupId);
        ElMessage({
          message: "已从聊天列表中删除！",
          type: 'success'
        });
      }
    });
  }

  return {
    groupData,
    getImGroup,
    addGroupTopic,
    exitGroup,
    dissolveGroup,
    removeGroupFromList,
    removeGroupFromLocalStorage
  }
}
