<script setup lang="ts">
import { reactive, ref, watch, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { useRoute, useRouter } from 'vue-router'
import {
  User,
  Lock,
  Message,
  Iphone,
  CircleCheck,
  Close,
  View,
  Hide,
  Loading,
  InfoFilled
} from '@element-plus/icons-vue'
import { oauthAuthorizeUrl } from '../api/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const visible = ref(auth.showLoginModal)
const loading = ref(false)
const loginMode = ref<'password' | 'email' | 'sms'>('password')
const countdown = ref(0)
const showPassword = ref(false)
let countdownTimer: number | null = null
let oauthListener: ((event: MessageEvent) => void) | null = null

watch(() => auth.showLoginModal, (val) => {
  visible.value = val
})
watch(visible, (val) => {
  if (!val) auth.closeLoginModal()
})

watch(loginMode, () => {
  nextTick(() => {
    formRef.value?.clearValidate()
  })
})

watch(() => route.query.login, (val) => {
  if (val === '1' && !auth.isLoggedIn) {
    auth.openLoginModal()
    router.replace({ query: { ...route.query, login: undefined } })
  }
}, { immediate: true })

const passwordForm = reactive({ username: '', password: '' })
const emailForm = reactive({ email: '', code: '' })
const smsForm = reactive({ phone: '', code: '' })

const formRef = ref<any>(null)

const rules: Record<string, any> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 24, message: '长度在 3 到 24 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度在 6 到 32 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

const canSendCode = computed(() => {
  if (loginMode.value === 'email') {
    return /^\S+@\S+\.\S+$/.test(emailForm.email.trim())
  }
  if (loginMode.value === 'sms') {
    return /^1[3-9]\d{9}$/.test(smsForm.phone.trim())
  }
  return false
})

const startCountdown = () => {
  countdown.value = 60
  countdownTimer && clearInterval(countdownTimer)
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

const currentCodeValue = () => {
  return loginMode.value === 'email' ? emailForm.email : smsForm.phone
}

const handleSendCode = async () => {
  const field = loginMode.value === 'email' ? 'email' : 'phone'
  const valid = await formRef.value?.validateField(field).catch(() => false)
  if (!valid) return
  loading.value = true
  const value = currentCodeValue()
  const ok = loginMode.value === 'email'
    ? await auth.sendEmailCode(value.trim())
    : await auth.sendSmsCode(value.trim())
  loading.value = false
  if (ok) {
    ElMessage.success('验证码已发送')
    startCountdown()
  } else {
    ElMessage.error('验证码发送失败，请检查输入')
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请填写正确的登录信息')
    return
  }
  loading.value = true
  let ok = false
  if (loginMode.value === 'password') {
    ok = await auth.login({
      username: passwordForm.username.trim(),
      password: passwordForm.password
    })
  } else if (loginMode.value === 'email') {
    ok = await auth.emailCodeLogin({
      email: emailForm.email.trim(),
      code: emailForm.code.trim()
    })
  } else {
    ok = await auth.smsCodeLogin({
      phone: smsForm.phone.trim(),
      code: smsForm.code.trim()
    })
  }
  loading.value = false
  if (ok) {
    ElMessage.success('登录成功')
    auth.closeLoginModal()
    const redirect = route.query.redirect as string
    if (redirect && redirect.startsWith('/')) {
      router.replace(redirect)
    } else {
      router.replace('/notes')
    }
  } else {
    ElMessage.error('登录失败，请检查输入')
  }
}

const openOAuth = (provider: 'github' | 'google' | 'qq') => {
  if (provider !== 'github') {
    ElMessage.warning('该登录方式暂不可用')
    return
  }
  const url = oauthAuthorizeUrl(provider)
  const width = 560
  const height = 640
  const left = window.screenX + (window.outerWidth - width) / 2
  const top = window.screenY + (window.outerHeight - height) / 2
  const popup = window.open(
    url,
    `oauth-${provider}`,
    `width=${width},height=${height},left=${left},top=${top},toolbar=no,menubar=no`
  )
  if (!popup) {
    ElMessage.warning('请允许浏览器打开弹窗')
    return
  }

  oauthListener = (event: MessageEvent) => {
    if (event.origin !== window.location.origin) return
    const { token, error } = event.data || {}
    if (error) {
      ElMessage.error(error)
    } else if (token) {
      auth.oauthLogin(token).then((ok) => {
        if (ok) {
          ElMessage.success('登录成功')
          auth.closeLoginModal()
          const redirect = route.query.redirect as string
          router.replace(redirect && redirect.startsWith('/') ? redirect : '/notes')
        } else {
          ElMessage.error('登录失败')
        }
      })
    }
    window.removeEventListener('message', oauthListener!)
    oauthListener = null
  }
  window.addEventListener('message', oauthListener)
}

onUnmounted(() => {
  if (oauthListener) {
    window.removeEventListener('message', oauthListener)
  }
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="420px"
    :show-close="false"
    :close-on-click-modal="true"
    align-center
    class="login-dialog"
  >
    <section class="login-form-section">
      <button type="button" class="close-btn" @click="auth.closeLoginModal()">
        <el-icon><Close /></el-icon>
      </button>

      <div class="form-wrapper">
        <header class="form-header">
          <h2>欢迎回来</h2>
          <p>登录即可体验，新用户将自动注册</p>
        </header>

        <div class="mode-switch">
          <button
            type="button"
            :class="['mode-btn', { active: loginMode === 'password' }]"
            @click="loginMode = 'password'"
          >
            账号密码
          </button>
          <button
            type="button"
            :class="['mode-btn', { active: loginMode === 'email' }]"
            @click="loginMode = 'email'"
          >
            邮箱验证码
          </button>
          <button
            type="button"
            :class="['mode-btn', { active: loginMode === 'sms' }]"
            @click="loginMode = 'sms'"
          >
            短信验证码
          </button>
        </div>

        <el-form
          ref="formRef"
          :model="loginMode === 'password' ? passwordForm : loginMode === 'email' ? emailForm : smsForm"
          :rules="rules"
          size="large"
          label-position="left"
          label-width="90px"
          :hide-required-asterisk="true"
          class="login-form"
          @keyup.enter="handleSubmit"
        >
          <template v-if="loginMode === 'password'">
            <el-form-item prop="username" label="用户名" :show-message="false" class="form-item-clean">
              <div class="custom-input-wrap">
                <el-icon class="input-icon"><User /></el-icon>
                <el-input
                  v-model="passwordForm.username"
                  placeholder="请输入用户名"
                  class="custom-input"
                />
              </div>
            </el-form-item>

            <el-form-item prop="password" label="密码" :show-message="false" class="form-item-clean">
              <div class="custom-input-wrap">
                <el-icon class="input-icon"><Lock /></el-icon>
                <el-input
                  v-model="passwordForm.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入 6-32 位密码"
                  class="custom-input"
                />
                <button type="button" class="eye-btn" @click="showPassword = !showPassword">
                  <el-icon><View v-if="showPassword" /><Hide v-else /></el-icon>
                </button>
              </div>
            </el-form-item>
          </template>

          <template v-if="loginMode === 'email'">
            <el-form-item prop="email" label="邮箱" :show-message="false" class="form-item-clean">
              <div class="custom-input-wrap">
                <el-icon class="input-icon"><Message /></el-icon>
                <el-input
                  v-model="emailForm.email"
                  placeholder="请输入邮箱"
                  class="custom-input"
                />
              </div>
            </el-form-item>

            <el-form-item prop="code" label="验证码" :show-message="false" class="form-item-clean">
              <div class="code-row">
                <div class="custom-input-wrap code-input">
                  <el-icon class="input-icon"><CircleCheck /></el-icon>
                  <el-input
                    v-model="emailForm.code"
                    placeholder="6 位验证码"
                    maxlength="6"
                    class="custom-input"
                  />
                </div>
                <button
                  type="button"
                  class="send-code-btn"
                  :disabled="countdown > 0 || loading || !canSendCode"
                  @click="handleSendCode"
                >
                  {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                </button>
              </div>
            </el-form-item>
          </template>

          <template v-if="loginMode === 'sms'">
            <div class="sms-disabled-tip">
              <el-icon class="sms-disabled-icon"><InfoFilled /></el-icon>
              <p>短信验证码登录暂不可用</p>
              <span>请使用账号密码、邮箱验证码或 GitHub 登录</span>
            </div>
          </template>

          <button
            type="button"
            class="submit-btn"
            :disabled="loading"
            @click="handleSubmit"
          >
            <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
            <span>{{ loading ? '登录中...' : '登录 / 自动注册' }}</span>
          </button>
        </el-form>

        <div class="oauth-divider">
          <span>或</span>
        </div>

        <div class="oauth-buttons">
          <button type="button" class="oauth-btn" title="GitHub" @click="openOAuth('github')">
            <svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.87 8.17 6.84 9.5.5.08.66-.23.66-.5v-1.69c-2.77.6-3.36-1.34-3.36-1.34-.46-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.6.07-.6 1 .07 1.53 1.03 1.53 1.03.87 1.52 2.34 1.08 2.91.83.09-.65.35-1.09.63-1.34-2.22-.25-4.55-1.11-4.55-4.92 0-1.11.38-2 1.03-2.71-.1-.25-.45-1.29.1-2.64 0 0 .84-.27 2.75 1.02.79-.22 1.65-.33 2.5-.33.85 0 1.71.11 2.5.33 1.91-1.29 2.75-1.02 2.75-1.02.55 1.35.2 2.39.1 2.64.65.71 1.03 1.6 1.03 2.71 0 3.82-2.34 4.66-4.57 4.91.36.31.69.92.69 1.85V21c0 .27.16.59.67.5C19.14 20.16 22 16.42 22 12A10 10 0 0012 2z"/></svg>
          </button>
          <button type="button" class="oauth-btn google" title="Google" @click="openOAuth('google')">
            <svg viewBox="0 0 24 24" width="22" height="22"><path fill="#EA4335" d="M12 5.04c1.75 0 3.32.6 4.56 1.79l3.42-3.42C17.95 1.19 15.24 0 12 0 7.28 0 3.18 2.69 1.21 6.59l3.94 3.05C6.22 7.14 8.87 5.04 12 5.04z"/><path fill="#4285F4" d="M23.49 12.27c0-.86-.08-1.69-.22-2.49H12v4.71h6.45c-.28 1.48-1.11 2.73-2.36 3.57l3.83 2.97c2.24-2.07 3.57-5.12 3.57-8.76z"/><path fill="#FBBC05" d="M5.16 14.36l-3.94 3.05C3.18 21.31 7.28 24 12 24c3.24 0 5.95-1.19 7.92-3.23l-3.83-2.97c-1.07.72-2.45 1.14-4.09 1.14-3.13 0-5.78-2.1-6.73-4.95l-.01.37z"/><path fill="#34A853" d="M12 4.96V9.67h6.75c-.3 1.48-1.11 2.74-2.36 3.58l3.83 2.97C21.09 14.35 23.49 10.18 23.49 12c0-.43-.04-.86-.08-1.28H12V4.96z"/></svg>
          </button>
          <button type="button" class="oauth-btn qq" title="QQ" @click="openOAuth('qq')">
            <svg viewBox="0 0 24 24" width="22" height="22"><path fill="#12B7F5" d="M12 2C7.5 2 4 5.5 4 9c0 2.5 1.2 4.7 3 6.2-.1.8-.5 2.3-1.3 3.3 1.7.4 3.4-.2 4.3-.8.4.1.7.1 1 .1s.6 0 1-.1c.9.6 2.6 1.2 4.3.8-.8-1-1.2-2.5-1.3-3.3 1.8-1.5 3-3.7 3-6.2 0-3.5-3.5-7-8-7z"/></svg>
          </button>
        </div>
      </div>
    </section>
  </el-dialog>
</template>

<style scoped>
.login-form-section {
  position: relative;
  padding: 32px 28px 28px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.12);
}

.close-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f4f5f7;
  color: #1a1d21;
}

.form-header {
  margin-bottom: 16px;
}

.form-header h2 {
  font-size: 22px;
  font-weight: 800;
  color: #1a1d21;
  margin: 0;
}

.form-header p {
  font-size: 13px;
  color: #4b5563;
  margin-top: 6px;
}

.form-item-clean {
  margin-bottom: 10px;
}

:deep(.form-item-clean .el-form-item__label) {
  white-space: nowrap;
  color: #4b5563;
  font-weight: 600;
  font-size: 13px;
  padding-right: 8px;
}

:deep(.form-item-clean .el-form-item__error) {
  position: relative;
  padding-top: 2px;
  color: #f56c6c;
}

.mode-switch {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.mode-btn {
  flex: 1;
  padding: 8px 0;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #4b5563;
  background: transparent;
  cursor: pointer;
  transition: all 0.18s;
}

.mode-btn:hover {
  background: #f4f5f7;
}

.mode-btn.active {
  background: #e8f0fe;
  color: #0057c2;
}

.input-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
  margin-bottom: 6px;
  margin-top: 12px;
}

.custom-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 12px;
  color: #6b7280;
  font-size: 16px;
  z-index: 1;
}

.eye-btn {
  position: absolute;
  right: 10px;
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
}

.eye-btn:hover {
  color: #1a1d21;
}

.custom-input {
  width: 100%;
}

.custom-input :deep(.el-input__wrapper) {
  background: #f4f5f7;
  border: none;
  border-radius: 10px;
  box-shadow: none !important;
  padding-left: 34px;
  padding-right: 12px;
}

.custom-input :deep(.el-input__inner) {
  height: 34px;
  color: #1a1d21;
  font-size: 14px;
}

.custom-input :deep(.el-input__inner::placeholder) {
  color: #6b7280;
}

.code-row {
  display: flex;
  gap: 10px;
}

.code-input {
  flex: 1;
}

.send-code-btn {
  padding: 0 14px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #ffffff;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s;
  white-space: nowrap;
}

.send-code-btn:hover:not(:disabled) {
  background: #f4f5f7;
  color: #1a1d21;
}

.send-code-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.submit-btn {
  width: 100%;
  margin-top: 14px;
  padding: 10px 0;
  border: none;
  border-radius: 10px;
  background: #0057c2;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: transform 0.15s;
}

.submit-btn:hover:not(:disabled) {
  transform: scale(0.985);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.sms-disabled-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  color: #6b7280;
  text-align: center;
}

.sms-disabled-tip p {
  font-size: 15px;
  font-weight: 600;
  color: #1a1d21;
  margin: 0;
}

.sms-disabled-tip span {
  font-size: 13px;
  color: #9ca3af;
}

.sms-disabled-icon {
  font-size: 36px;
  color: #d1d5db;
}

.oauth-divider {
  display: flex;
  align-items: center;
  margin: 14px 0;
  color: #6b7280;
  font-size: 12px;
}

.oauth-divider::before,
.oauth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #d1d5db;
}

.oauth-divider span {
  padding: 0 12px;
}

.oauth-buttons {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.oauth-btn {
  width: 44px;
  height: 44px;
  border: 1px solid #d1d5db;
  border-radius: 50%;
  background: #ffffff;
  color: #1a1d21;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.oauth-btn:hover {
  background: #f4f5f7;
  transform: scale(1.05);
}

.oauth-btn.google:hover {
  background: #fff1f0;
}

.oauth-btn.qq:hover {
  background: #e6f7ff;
}
</style>

<style>
/* 覆盖 Element Plus Dialog 默认样式，去掉白边 */
.login-dialog .el-dialog__header {
  display: none !important;
  padding: 0 !important;
  height: 0 !important;
}

.login-dialog .el-dialog__body {
  padding: 0 !important;
  background: transparent !important;
}

.login-dialog.el-dialog,
.login-dialog .el-dialog {
  background: transparent !important;
  box-shadow: none !important;
  border-radius: 0 !important;
  padding: 0 !important;
}

@media (max-width: 520px) {
  .login-dialog .el-dialog {
    width: 92% !important;
  }
}
</style>
