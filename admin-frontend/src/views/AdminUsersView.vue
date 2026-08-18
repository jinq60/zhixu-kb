<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminListUsers, adminChangeRole, type AdminUser } from '../api/admin'
import PagedTable from '../components/PagedTable.vue'

const users = ref<AdminUser[]>([])
const loading = ref(false)
const changing = ref(false)

const load = async () => {
  loading.value = true
  try {
    users.value = await adminListUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载用户失败')
  } finally {
    loading.value = false
  }
}

const changeRole = async (user: AdminUser, role: string) => {
  changing.value = true
  try {
    await adminChangeRole(user.id, role)
    ElMessage.success(`已将 ${user.username} 的角色变更为 ${role}`)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '角色变更失败')
  } finally {
    changing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-users-page">
    <div class="page-header">
      <h2>用户治理</h2>
      <p>管理用户角色（USER / ADMIN），admin 角色可访问管理端全部功能</p>
    </div>

    <el-card shadow="never" class="panel-card">
      <PagedTable :data="users" :loading="loading" :page-size="10">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" size="small" :type="role === 'admin' ? 'warning' : 'info'">
              {{ role.toUpperCase() }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="180" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="changing || row.roles.includes('admin')"
              @click="changeRole(row, 'admin')"
            >设为管理员</el-button>
            <el-button
              size="small"
              plain
              :disabled="changing || !row.roles.includes('admin')"
              @click="changeRole(row, 'user')"
            >降为普通用户</el-button>
          </template>
        </el-table-column>
      </PagedTable>
    </el-card>
  </div>
</template>

<style scoped>
.admin-users-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header h2 {
  font-size: 20px;
  color: #111827;
}

.page-header p {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.panel-card {
  border-radius: 14px;
  border: 1px solid #ebeef5;
}
</style>
