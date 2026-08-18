<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  const token = route.query.token as string
  const error = route.query.error as string

  if (window.opener) {
    // 弹窗模式：把结果 postMessage 给父页面
    window.opener.postMessage(
      { token, error },
      window.location.origin
    )
    window.close()
    return
  }

  if (error) {
    ElMessage.error(error)
    router.replace('/home')
    return
  }

  if (token) {
    const ok = await auth.oauthLogin(token)
    if (ok) {
      ElMessage.success('登录成功')
      router.replace('/notes')
    } else {
      ElMessage.error('登录失败')
      router.replace('/home')
    }
    return
  }

  router.replace('/home')
})
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
