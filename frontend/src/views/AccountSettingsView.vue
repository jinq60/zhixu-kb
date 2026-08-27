<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchUserProfile,
  updatePassword,
  sendBindEmailCode,
  bindEmail,
  type UserProfile
} from '../api/auth'
import { resetTour, runWorkspaceTour } from '../utils/onboarding'
import { useRouter } from 'vue-router'

const router = useRouter()

/** 重新播放新手引导：跳回工作台首页后启动 */
const replayOnboarding = () => {
  resetTour()
  router.push('/notes')
  window.setTimeout(() => runWorkspaceTour(), 600)
}

const profile = ref<UserProfile | null>(null)
const loading = ref(false)
const saving = ref(false)
const bindLoading = ref(false)
const sendingCode = ref(false)
const bindDialogVisible = ref(false)
const countdown = ref(0)
let countdownTimer: number | null = null

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const bindEmailInput = ref('')
const bindCode = ref('')

const providerLabels: Record<string, string> = {
  password: '账号密码',
  email_code: '邮箱验证码',
  sms_code: '短信验证码',
  github: 'GitHub',
  google: 'Google',
  qq: 'QQ'
}

const providerTagType = (provider: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined => {
  switch (provider) {
    case 'github': return 'info'
    case 'google': return 'danger'
    case 'qq': return 'primary'
    case 'email_code': return 'success'
    case 'sms_code': return 'warning'
    default: return undefined
  }
}

const emailText = computed(() => {
  const e = profile.value?.email
  return e && e !== 'null' ? e : '未绑定邮箱'
})

const hasEmail = computed(() => !!profile.value?.email && profile.value.email !== 'null')
const bindBtnText = computed(() => hasEmail.value ? '更换邮箱' : '绑定邮箱')
const title = computed(() => profile.value?.hasPassword ? '修改密码' : '设置密码')
const submitText = computed(() => profile.value?.hasPassword ? '修改密码' : '设置密码')
const passwordLabel = computed(() => profile.value?.hasPassword ? '旧密码' : '新密码')

const loadProfile = async () => {
  loading.value = true
  try {
    profile.value = await fetchUserProfile()
  } catch {
    ElMessage.error('获取账号信息失败')
  } finally {
    loading.value = false
  }
}

const handlePasswordSubmit = async () => {
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('新密码长度不能少于 6 位')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await updatePassword(oldPassword.value, newPassword.value)
    ElMessage.success(title.value + '成功')
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    await loadProfile()
  } catch (error: any) {
    const msg = error?.response?.data?.message || title.value + '失败'
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

const openBindDialog = () => {
  bindEmailInput.value = profile.value?.email && profile.value.email !== 'null' ? profile.value.email : ''
  bindCode.value = ''
  bindDialogVisible.value = true
}

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

const handleSendBindCode = async () => {
  if (!bindEmailInput.value || !bindEmailInput.value.includes('@')) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  sendingCode.value = true
  try {
    await sendBindEmailCode(bindEmailInput.value.trim())
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch (error: any) {
    const msg = error?.response?.data?.message || '验证码发送失败'
    ElMessage.error(msg)
  } finally {
    sendingCode.value = false
  }
}

const handleBindEmail = async () => {
  if (!bindEmailInput.value || !bindCode.value) {
    ElMessage.warning('请填写邮箱和验证码')
    return
  }
  bindLoading.value = true
  try {
    await bindEmail(bindEmailInput.value.trim(), bindCode.value.trim())
    ElMessage.success('邮箱绑定成功')
    bindDialogVisible.value = false
    await loadProfile()
  } catch (error: any) {
    const msg = error?.response?.data?.message || '邮箱绑定失败'
    ElMessage.error(msg)
  } finally {
    bindLoading.value = false
  }
}

onMounted(loadProfile)

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
</script>

<template>
  <div class="account-settings-page">
    <h1 class="page-title">账号设置</h1>

    <div v-loading="loading" class="settings-card">
      <div class="section">
        <h2 class="section-title">新手引导</h2>
        <div class="info-list">
          <div class="info-item">
            <span class="info-label">产品功能导览</span>
            <span class="info-value empty">首次使用？花 30 秒了解核心功能</span>
            <el-button type="primary" link size="small" @click="replayOnboarding">重新查看引导</el-button>
          </div>
        </div>
      </div>

      <div class="section">
        <h2 class="section-title">基本信息</h2>
        <div class="info-list">
          <div class="info-item">
            <span class="info-label">用户名</span>
            <span class="info-value">{{ profile?.username || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">邮箱</span>
            <span class="info-value" :class="{ empty: !hasEmail }">{{ emailText }}</span>
            <el-button type="primary" link size="small" @click="openBindDialog">
              {{ bindBtnText }}
            </el-button>
          </div>
        </div>
      </div>

      <div class="section">
        <h2 class="section-title">已绑定登录方式</h2>
        <div v-if="profile?.bindings?.length" class="binding-tags">
          <el-tag
            v-for="provider in profile.bindings"
            :key="provider"
            :type="providerTagType(provider)"
            size="large"
            effect="light"
          >
            {{ providerLabels[provider] || provider }}
          </el-tag>
        </div>
        <div v-else class="empty-text">暂无绑定记录</div>
      </div>

      <div class="section">
        <h2 class="section-title">{{ title }}</h2>
        <div class="password-form">
          <div v-if="profile?.hasPassword" class="form-row">
            <label class="form-label">旧密码</label>
            <el-input
              v-model="oldPassword"
              type="password"
              placeholder="请输入旧密码"
              show-password
              class="form-input"
            />
          </div>
          <div class="form-row">
            <label class="form-label">{{ passwordLabel }}</label>
            <el-input
              v-model="newPassword"
              type="password"
              placeholder="请输入 6-32 位新密码"
              show-password
              class="form-input"
            />
          </div>
          <div class="form-row">
            <label class="form-label">确认密码</label>
            <el-input
              v-model="confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              show-password
              class="form-input"
            />
          </div>
          <el-button type="primary" :loading="saving" @click="handlePasswordSubmit">
            {{ submitText }}
          </el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="bindDialogVisible" title="绑定邮箱" width="400px">
      <div class="bind-form">
        <div class="bind-row">
          <label class="bind-label">邮箱</label>
          <el-input v-model="bindEmailInput" placeholder="请输入邮箱" />
        </div>
        <div class="bind-row">
          <label class="bind-label">验证码</label>
          <div class="code-row">
            <el-input v-model="bindCode" placeholder="6 位验证码" maxlength="6" />
            <el-button
              :disabled="countdown > 0 || sendingCode"
              :loading="sendingCode"
              @click="handleSendBindCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindLoading" @click="handleBindEmail">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.account-settings-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 0;
}

.page-title {
  font-size: 24px;
  font-weight: 800;
  color: #1a1d21;
  margin-bottom: 20px;
}

.settings-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.section {
  margin-bottom: 28px;
}

.section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a1d21;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f2f6;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.info-label {
  width: 80px;
  font-size: 14px;
  color: #6b7280;
  flex-shrink: 0;
}

.info-value {
  font-size: 14px;
  font-weight: 600;
  color: #1a1d21;
  min-width: 120px;
}

.info-value.empty {
  color: #9ca3af;
  font-weight: 400;
}

.binding-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.empty-text {
  font-size: 13px;
  color: #9ca3af;
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.form-label {
  width: 80px;
  font-size: 14px;
  color: #6b7280;
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  max-width: 320px;
}

.bind-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.bind-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bind-label {
  font-size: 13px;
  font-weight: 600;
  color: #4b5563;
}

.code-row {
  display: flex;
  gap: 10px;
}

.code-row .el-input {
  flex: 1;
}

@media (max-width: 768px) {
  .account-settings-page {
    padding: 16px 0;
  }

  .page-title {
    font-size: 20px;
    margin-bottom: 16px;
  }

  .settings-card {
    padding: 20px;
  }

  .section {
    margin-bottom: 22px;
  }

  .info-item,
  .form-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .info-label,
  .form-label {
    width: auto;
  }

  .form-input {
    max-width: none;
    width: 100%;
  }

  .code-row {
    flex-direction: column;
  }
}
</style>
