import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import pinia from './stores'
import { useAuthStore } from './stores/auth'
import './styles/console.css'

const app = createApp(App)

// 注册Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 使用插件
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 应用形态探测：桌面版（exe）自动建立本地单用户会话，免登录直达工作台
const authStore = useAuthStore()
authStore
  .initDesktopSession()
  .catch(() => undefined)
  .finally(() => {
    // 桌面版默认落在工作台（网页版的落地页/marketing 页对桌面无意义）
    const current = router.currentRoute.value.path
    if (authStore.appMode === 'desktop' && (current === '/' || current === '/home')) {
      router.replace('/notes')
    }
    app.mount('#app')
  })
