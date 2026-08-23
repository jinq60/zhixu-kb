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

  if (error) {
    postResult('', error)
    ElMessage.error(error)
    router.replace('/home')
    return
  }

  if (!code) {
    postResult('', '未收到授权码')
    router.replace('/home')
    return
  }

  try {
    const token = await oauthExchange(code)
    postResult(token, '')
    const ok = await auth.oauthLogin(token)
    if (ok) {
      ElMessage.success('登录成功')
      router.replace('/notes')
    } else {
      ElMessage.error('登录失败')
      router.replace('/home')
    }
  } catch {
    postResult('', '授权码无效或已过期')
    ElMessage.error('授权码无效或已过期，请重新登录')
    router.replace('/home')
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
