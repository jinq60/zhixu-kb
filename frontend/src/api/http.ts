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
    // 后端统一返回 { code, message, data }，业务错误 code 不为 200 时也按错误处理
    const data = res.data
    if (data && typeof data.code === 'number' && data.code !== 200) {
      return Promise.reject({ response: { data, status: data.code } })
    }
    return res
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      const auth = useAuthStore()
      auth.logout()
    } else if (error.response && error.response.status === 403) {
      ElMessage.error(error.response.data?.message || '无权访问该资源')
    }
    return Promise.reject(error)
  }
)

export default instance
