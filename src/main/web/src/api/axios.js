import axios from "axios";
import {ElMessage} from "element-plus";

axios.defaults.baseURL = window.location.protocol + "//" + window.location.host + "/checkIn/api/";

//post请求头
axios.defaults.headers.post["Content-Type"] = "application/json;charset=UTF-8";
//允许跨域携带cookie信息
axios.defaults.withCredentials = true;
//设置超时
axios.defaults.timeout = 30000;

// 最大重试次数
const MAX_RETRIES = 3;

axios.interceptors.request.use(
    config => {
        return config;
    },
    error => {
        return Promise.reject(error);
    }
);

axios.interceptors.response.use(
    response => {
        if (response.status === 200) {
            return Promise.resolve(response);
        } else {
            return Promise.reject(response);
        }
    },
    error => {
        const config = error.config;
        if (!config) {
            return Promise.reject(error);
        }
        
        config._retryCount = config._retryCount || 0;
        
        if (config._retryCount < MAX_RETRIES) {
            config._retryCount += 1;
            console.log(`请求失败，正在重试 (${config._retryCount}/${MAX_RETRIES})...`);
            return axios(config);
        }
        
        return Promise.reject(error);
    }
);
export default {
    /**
     * @param {String} url
     * @param {Object} data
     * @returns Promise
     */
    post(url, data) {
        return new Promise((resolve, reject) => {
            axios.post(url, data).then(
                response => {
                    try {
                        if (response.data) {
                            resolve(response.data)
                        } else {
                            reject(response)
                        }
                    } catch (e) {
                        reject(response)
                    }
                },
                error => {
                    reject(error)
                });
        })
    },
    get(url, data) {
        return new Promise((resolve, reject) => {
            axios.get(url, data).then(
                response => {
                    if (response && response.data) {
                        try {
                            resolve(response.data)
                        } catch (e) {
                            reject(response)
                        }
                    } else {
                        reject(response)
                    }
                },
                error => {
                    reject(error)
                });
        })
    }
};