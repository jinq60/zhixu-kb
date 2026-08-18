<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)

const handleLogin = async () => {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入用户名与密码')
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    if (!auth.isAdmin) {
      ElMessage.error('该账号不是管理员，无法进入后台')
      auth.logout()
      return
    }
    router.push('/admin/ops')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">序</div>
      <h1>知序管理后台</h1>
      <p class="login-sub">Zhixu Platform Admin · 请使用管理员账号登录</p>

      <el-form class="login-form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" size="large" :prefix-icon="'User'" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="密码"
            size="large"
            :prefix-icon="'Lock'"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1f2733;
}

.login-card {
  width: 380px;
  padding: 40px 36px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.25);
  text-align: center;
}

.login-logo {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #409eff;
  color: #fff;
  font-size: 22px;
  font-weight: 800;
}

.login-card h1 {
  font-size: 22px;
  color: #1f2733;
}

.login-sub {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.login-form {
  margin-top: 28px;
}

.login-btn {
  width: 100%;
}
</style>
