import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 60000
})

instance.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

instance.interceptors.response.use(
  (res) => {
    // 后端统一返回 { code, message, data }，业务错误 code 不为 200 时也按错误处理。
    // 打上 businessError 标记，与真实 HTTP 401（会话过期）区分，避免业务码 401 触发强制登出
    const data = res.data
    if (data && typeof data.code === 'number' && data.code !== 200) {
      return Promise.reject({ response: { data, status: data.code }, businessError: true })
    }
    return res
  },
  (error) => {
    const status = error.response && error.response.status
    const businessError = (error as { businessError?: boolean })?.businessError === true
    if (status === 401 && !businessError) {
      const auth = useAuthStore()
      // 仅在仍有 token 时登出，避免多个并行请求同时 401 触发多次跳转
      if (auth.token) {
        auth.logout().catch(() => undefined)
      }
    } else if (status === 403) {
      ElMessage.error(error.response?.data?.message || '无权访问该资源')
    }
    return Promise.reject(error)
  }
)

export default instance
