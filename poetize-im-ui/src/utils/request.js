import axios from "axios";
import constant from "./constant";
//处理url参数
import qs from "qs";

import store from "../store";


axios.defaults.baseURL = constant.baseURL;


// 添加请求拦截器
axios.interceptors.request.use(function (config) {
  // 在发送请求之前做些什么
  return config;
}, function (error) {
  // 对请求错误做些什么
  return Promise.reject(error);
});


// 添加响应拦截器
axios.interceptors.response.use(function (response) {
  if (response.data !== null && response.data.hasOwnProperty("code") && response.data.code !== 200) {
    // 详细错误日志记录
    console.group('🚨 HTTP响应错误');
    console.error('错误码:', response.data.code);
    console.error('错误信息:', response.data.message);
    console.error('请求URL:', response.config.url);
    console.error('请求方法:', response.config.method);
    console.error('请求参数:', response.config.data || response.config.params);
    console.error('响应数据:', response.data);
    console.error('发生时间:', new Date().toLocaleString());
    console.groupEnd();
    
    if (response.data.code === 300) {
      store.commit("loadCurrentUser", {});
      localStorage.removeItem("userToken");
      window.location.href = constant.webBaseURL + "/user";
    }
    return Promise.reject(new Error(response.data.message));
  } else {
    return response;
  }
}, function (error) {
  // 网络错误详细日志
  console.group('🚨 HTTP网络错误');
  console.error('错误类型:', error.name);
  console.error('错误信息:', error.message);
  if (error.response) {
    console.error('响应状态:', error.response.status);
    console.error('响应头:', error.response.headers);
    console.error('响应数据:', error.response.data);
    console.error('请求URL:', error.response.config.url);
    console.error('请求方法:', error.response.config.method);
  } else if (error.request) {
    console.error('请求对象:', error.request);
    console.error('网络连接失败或超时');
  }
  console.error('错误堆栈:', error.stack);
  console.error('发生时间:', new Date().toLocaleString());
  console.groupEnd();
  
  return Promise.reject(error);
});


// 当data为URLSearchParams对象时设置为application/x-www-form-urlencoded;charset=utf-8
// 当data为普通对象时，会被设置为application/json;charset=utf-8


export default {
  post(url, params = {}, json = true) {
    if (params === null || typeof params !== 'object') {
      params = {};
    }
    let config = {
      headers: {}
    };

    const token = localStorage.getItem("userToken");
    if (token) {
      config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }

    return new Promise((resolve, reject) => {
      axios
        .post(url, json ? params : qs.stringify(params), config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  get(url, params = {}) {
    if (params === null || typeof params !== 'object') {
      params = {};
    }
    let headers = {};
    const token = localStorage.getItem("userToken");
    if (token) {
      headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }

    return new Promise((resolve, reject) => {
      axios.get(url, {
        params: params,
        headers: headers
      }).then(res => {
        resolve(res.data);
      }).catch(err => {
        reject(err)
      })
    });
  },

  upload(url, param, option) {
    let config = {
      headers: {"Authorization": localStorage.getItem("userToken"), "Content-Type": "multipart/form-data"},
      timeout: 60000
    };
    if (typeof option !== "undefined") {
      config.onUploadProgress = progressEvent => {
        if (progressEvent.total > 0) {
          progressEvent.percent = progressEvent.loaded / progressEvent.total * 100;
        }
        option.onProgress(progressEvent);
      };
    }

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  uploadQiniu(url, param) {
    let config = {
      headers: {"Content-Type": "multipart/form-data"},
      timeout: 60000
    };

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
