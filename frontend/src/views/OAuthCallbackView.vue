<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { oauthExchange } from '../api/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  const code = route.query.code as string
  const error = route.query.error as string

  // P0-7 修复：仅接受本次会话发起的 OAuth 回调，拒绝攻击者诱导访问的 code（Login CSRF）；
  // 标记一次性消费，且兑换后立即清除 URL 中的 code 防重放/日志残留
  let initiated = false
  try {
    initiated = sessionStorage.getItem('oauth_initiated') === '1'
    sessionStorage.removeItem('oauth_initiated')
  } catch { /* ignore */ }
  // 弹窗模式由 opener 发起：允许无标记（父页面持有 source 校验）；直跳模式必须有标记
  if (!window.opener && !initiated) {
    ElMessage.error('请从登录页发起第三方登录')
    router.replace({ path: '/home', query: {} })
    return
  }

  if (error) {
    postResult('', error)
    ElMessage.error(error)
    router.replace({ path: '/home', query: {} })
    return
  }

  if (!code) {
    postResult('', '未收到授权码')
    router.replace({ path: '/home', query: {} })
    return
  }

  try {
    const token = await oauthExchange(code)
    postResult(token, '')
    const ok = await auth.oauthLogin(token)
    if (ok) {
      ElMessage.success('登录成功')
      router.replace({ path: '/notes', query: {} })
    } else {
      ElMessage.error('登录失败')
      router.replace({ path: '/home', query: {} })
    }
  } catch {
    postResult('', '授权码无效或已过期')
    ElMessage.error('授权码无效或已过期，请重新登录')
    router.replace({ path: '/home', query: {} })
  }
})

function postResult(token: string, error: string) {
  if (window.opener) {
    // 弹窗模式：把结果 postMessage 给父页面
    window.opener.postMessage({ token, error }, window.location.origin)
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
