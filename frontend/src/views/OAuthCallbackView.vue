<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  // Cookie 会话模式：登录态已由后端 302 响应的 Set-Cookie 种下，回调地址不再带 code，
  // 这里只向服务端确认会话有效（刷新用户信息），不存在凭证经 URL 传递。
  const error = route.query.error as string
  if (error) {
    postResult(false, error)
    ElMessage.error(error)
    router.replace({ path: '/home', query: {} })
    return
  }

  const ok = await auth.oauthRefresh()
  postResult(ok, ok ? '' : '登录失败')
  if (ok) {
    ElMessage.success('登录成功')
    router.replace({ path: '/notes', query: {} })
  } else {
    ElMessage.error('登录失败，请重新登录')
    router.replace({ path: '/home', query: {} })
  }
})

function postResult(success: boolean, error: string) {
  if (window.opener) {
    // 弹窗模式：只通知父页面成功与否，不再传递任何 token
    window.opener.postMessage({ success, error }, window.location.origin)
    window.close()
  }
}
</script>

<template>
  <div class="oauth-callback">
    <p>正在处理登录结果，请稍候…</p>
  </div>
</template>

<style scoped>
.oauth-callback {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
}
</style>
