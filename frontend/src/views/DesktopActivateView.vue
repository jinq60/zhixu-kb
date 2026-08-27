<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

/**
 * 桌面版激活页（仅桌面 exe 出现）：
 * 引导用户在浏览器完成官网验证，轮询本地激活状态，成功后进入工作台。
 */
const router = useRouter()
const auth = useAuthStore()
const activated = ref(false)
const starting = ref(false)
const verifyUrl = ref('')
let poll: ReturnType<typeof setInterval> | null = null

const checkStatus = async () => {
  try {
    const { data } = await http.get('/desktop/status')
    if (data.data?.activated) {
      activated.value = true
      stopPoll()
      // 激活完成：建立本地会话后进入工作台
      await auth.initDesktopSession()
      window.setTimeout(() => router.replace('/notes'), 1200)
    }
  } catch {
    // 忽略轮询失败
  }
}

const startVerify = async () => {
  starting.value = true
  try {
    const { data } = await http.post('/desktop/verify')
    verifyUrl.value = data.data?.verifyUrl || ''
    // 后端已尝试唤起系统浏览器；这里兜底再开一次（已打开时浏览器会聚焦同页）
    if (verifyUrl.value) window.open(verifyUrl.value, '_blank', 'noopener')
  } catch {
    // 忽略，用户可重试
  } finally {
    starting.value = false
  }
}

const stopPoll = () => {
  if (poll) {
    clearInterval(poll)
    poll = null
  }
}

onMounted(() => {
  checkStatus()
  poll = setInterval(checkStatus, 2000)
})

onBeforeUnmount(stopPoll)
</script>

<template>
  <div class="activate-page">
    <div class="activate-card">
      <div class="activate-logo">知序</div>

      <template v-if="!activated">
        <h1 class="activate-title">验证你的桌面版</h1>
        <p class="activate-desc">
          桌面版需要绑定你的官网账号后使用（数据始终保存在本机）。<br />
          点击下方按钮，将在浏览器中打开官网完成验证，全程约 20 秒。
        </p>
        <el-button type="primary" size="large" class="activate-btn" :loading="starting" @click="startVerify">
          打开浏览器验证
        </el-button>
        <p class="activate-tip">验证页面已打开？完成授权后本窗口会自动进入。</p>
        <p v-if="verifyUrl" class="activate-url">
          浏览器未自动打开？
          <a :href="verifyUrl" target="_blank" rel="noopener">点击手动打开验证链接</a>
        </p>
      </template>

      <template v-else>
        <h1 class="activate-title">✅ 验证成功</h1>
        <p class="activate-desc">正在进入你的知识库工作台…</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.activate-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #f5f7fa 0%, #eef2f7 100%);
  padding: 20px;
  box-sizing: border-box;
}

.activate-card {
  width: 440px;
  max-width: 100%;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  padding: 44px 44px 36px;
  text-align: center;
}

.activate-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 16px;
  border-radius: 14px;
  background: #2563eb;
  color: #fff;
  font-weight: 700;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.activate-title {
  margin: 0 0 14px;
  font-size: 21px;
  color: #111827;
}

.activate-desc {
  margin: 0 0 22px;
  color: #4b5563;
  font-size: 14px;
  line-height: 1.8;
}

.activate-btn {
  width: 100%;
}

.activate-tip {
  margin: 14px 0 0;
  color: #9ca3af;
  font-size: 12px;
}

.activate-url {
  margin: 8px 0 0;
  color: #9ca3af;
  font-size: 12px;
}

.activate-url a {
  color: #2563eb;
  text-decoration: none;
}
</style>
