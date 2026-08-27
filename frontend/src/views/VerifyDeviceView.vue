<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { createDeviceBindCode } from '../api/auth'

/**
 * 桌面版设备验证页（官网侧，公开路由）：
 * 桌面 exe 弹浏览器带 state+callback 进入此页 →
 * 用户完成官网登录 → 确认授权 → 生成一次性绑定码 → 302 跳回本地回调。
 */
const route = useRoute()
const auth = useAuthStore()

const state = computed(() => String(route.query.state || ''))
const callback = computed(() => String(route.query.callback || ''))
const callbackValid = computed(() => /^http:\/\/127\.0\.0\.1:\d+\/desktop\/callback$/.test(callback.value))
const paramsMissing = computed(() => !state.value || !callback.value || !callbackValid.value)

const submitting = ref(false)
const localUsername = ref('')
const localPassword = ref('')
const loginError = ref('')

const doLogin = async () => {
  loginError.value = ''
  const ok = await auth.login({ username: localUsername.value.trim(), password: localPassword.value })
  if (!ok) loginError.value = '用户名或密码错误'
}

const confirmAuth = async () => {
  if (!auth.isLoggedIn) return
  submitting.value = true
  try {
    const { bindCode } = await createDeviceBindCode()
    const sep = callback.value.includes('?') ? '&' : '?'
    window.location.href = `${callback.value}${sep}bindCode=${encodeURIComponent(bindCode)}&state=${encodeURIComponent(state.value)}`
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成绑定码失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (paramsMissing.value) {
    ElMessage.error('验证链接无效：缺少参数或回调地址不合法')
  }
})
</script>

<template>
  <div class="verify-page">
    <div class="verify-card">
      <div class="verify-logo">知序</div>
      <h1 class="verify-title">桌面版设备验证</h1>

      <div v-if="paramsMissing" class="verify-block">
        <el-alert type="error" :closable="false" title="验证链接无效" description="请从桌面应用重新发起验证" />
      </div>

      <!-- 已登录：确认授权 -->
      <div v-else-if="auth.isLoggedIn" class="verify-block">
        <p class="verify-desc">
          当前账号 <b>{{ auth.displayName }}</b>，即将授权桌面应用访问你的知识库数据。
        </p>
        <el-button type="primary" size="large" class="verify-btn" :loading="submitting" @click="confirmAuth">
          确认授权桌面版
        </el-button>
        <p class="verify-tip">授权后可在「账号设置 → 已绑定设备」中随时吊销</p>
      </div>

      <!-- 未登录：内联登录（复用现有账号体系） -->
      <div v-else class="verify-block">
        <p class="verify-desc">请先登录官网账号，以确认你是这台电脑的主人。</p>
        <el-form @submit.prevent>
          <el-form-item>
            <el-input v-model="localUsername" placeholder="用户名" @keyup.enter="confirmAuth" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="localPassword" type="password" show-password placeholder="密码" @keyup.enter="doLogin" />
          </el-form-item>
          <p v-if="loginError" class="login-error">{{ loginError }}</p>
          <el-button type="primary" size="large" class="verify-btn" @click="doLogin">登录并继续验证</el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.verify-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #f5f7fa 0%, #eef2f7 100%);
  padding: 20px;
  box-sizing: border-box;
}

.verify-card {
  width: 420px;
  max-width: 100%;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  padding: 36px 40px;
  text-align: center;
}

.verify-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  border-radius: 14px;
  background: #2563eb;
  color: #fff;
  font-weight: 700;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.verify-title {
  margin: 0 0 18px;
  font-size: 20px;
  color: #111827;
}

.verify-desc {
  margin: 0 0 18px;
  color: #4b5563;
  font-size: 14px;
  line-height: 1.7;
  text-align: left;
}

.verify-btn {
  width: 100%;
}

.verify-tip {
  margin: 14px 0 0;
  color: #9ca3af;
  font-size: 12px;
}

.login-error {
  margin: 0 0 12px;
  color: #f56c6c;
  font-size: 13px;
  text-align: left;
}
</style>
