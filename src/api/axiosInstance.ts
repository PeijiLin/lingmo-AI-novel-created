// src/api/axiosInstance.ts
import axios from 'axios';
import {getAccessToken, getRefreshToken, clearTokens, setTokens} from '@/utils/token';
import { UserControllerService } from "@/api/services/UserControllerService.ts";

// 移除 useRouter 的错误使用

// 创建一个独立的 axios 实例
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
});

// 请求拦截器：自动添加 Authorization
apiClient.interceptors.request.use(
  (config) => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ✅ 响应拦截器：关键修改！在成功响应中检查业务码
apiClient.interceptors.response.use(
  // ✅ 成功响应（HTTP 200）时，检查业务码
  (response) => {
    const data = response.data;
    if (data.code === 40101 && data.msg === '无权限') {
      // 将业务错误包装成一个可识别的 Error 对象，便于拦截器后续处理
      const error: any = new Error(data.message || '未知错误');
      error.code = data.code;
      error.response = response; // 保留原始响应，方便调试
      error.data = data;         // 保留完整数据
      return handleRefreshToken();
    }
    // 如果是业务错误，主动抛出错误，触发 .catch()
    if (data.code !== 200) {
      // 将业务错误包装成一个可识别的 Error 对象，便于拦截器后续处理
      const error: any = new Error(data.message || '未知错误');
      error.code = data.code;
      error.response = response; // 保留原始响应，方便调试
      error.data = data;         // 保留完整数据
      return Promise.reject(error); // 👈 主动 reject，进入 error 分支
    }

    // ✅ 正常业务响应，返回 data（可选：只返回 data）
    return response; // 👈 通常前端只关心 data 字段，可直接返回
  },

  // ✅ 真正的网络错误（如超时、断网、502）才进这里
  async (error) => {
    // 网络错误：断网、DNS、超时、服务器宕机等
    if (!error.response) {
      // 比如：ERR_NETWORK、ECONNABORTED
      console.error('网络连接失败，请检查网络', error.message);
      return Promise.reject(error);
    }

    // 如果是 HTTP 状态码错误（如 502、404），也统一处理
    // 但你后端一般不会返回这些，所以可以忽略或提示“服务异常”
    if (error.response.status >= 500) {
      alert('服务器内部错误，请稍后再试');
      return Promise.reject(error);
    }

    // 如果是 401（极少见，但可能有），也尝试刷新
    if (error.response.status === 401 && !error.config._retry) {
      return handleRefreshToken(error.config);
    }

    return Promise.reject(error);
  }
);

// ✅ 抽离刷新逻辑，便于复用
export async function handleRefreshToken() {

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    clearTokens();
    window.location.href = '/login';
    return Promise.reject(new Error('无刷新令牌，强制登出'));
  }

  try {
    const res = await UserControllerService.refreshToken({ refreshToken });

    if (res.code === 200 && res.data?.access_token) {
      const { access_token, refresh_token, userId } = res.data;
      setTokens({ access_token, refresh_token, userId });
      return
    } else {
      throw new Error('刷新 Token 失败');
    }
  } catch (refreshError) {
    console.error('Token 刷新失败:', refreshError);
    clearTokens();
    window.location.href = '/login';
    return Promise.reject(refreshError);
  }
}
export default apiClient;

