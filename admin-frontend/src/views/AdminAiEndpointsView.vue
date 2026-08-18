<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminListAiEndpoints,
  adminCreateAiEndpoint,
  adminUpdateAiEndpoint,
  adminDeleteAiEndpoint,
  adminToggleAiEndpoint,
  adminTestAiEndpoint,
  type AiEndpoint,
  type AiEndpointSavePayload
} from '../api/admin'
import PagedTable from '../components/PagedTable.vue'

const endpoints = ref<AiEndpoint[]>([])
const loading = ref(false)
const testingId = ref<number | null>(null)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const dialog = ref(false)
const editing = ref(false)
const form = ref<AiEndpointSavePayload>({
  baseUrl: 'https://openrouter.ai/api/v1',
  model: 'deepseek/deepseek-chat',
  apiKey: '',
  enabled: true,
  remark: ''
})

const load = async () => {
  loading.value = true
  try {
    endpoints.value = await adminListAiEndpoints()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载端点失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editing.value = false
  form.value = { baseUrl: 'https://openrouter.ai/api/v1', model: 'deepseek/deepseek-chat', apiKey: '', enabled: true, remark: '' }
  testResult.value = null
  dialog.value = true
}

const openEdit = (endpoint: AiEndpoint) => {
  editing.value = true
  form.value = {
    id: endpoint.id,
    baseUrl: endpoint.baseUrl,
    model: endpoint.model,
    apiKey: '',
    enabled: endpoint.enabled,
    remark: endpoint.remark || ''
  }
  testResult.value = null
  dialog.value = true
}

const handleSave = async () => {
  if (!form.value.baseUrl.trim() || !form.value.model.trim()) {
    ElMessage.warning('请填写接口地址与模型名称')
    return
  }
  if (!editing.value && !form.value.apiKey?.trim()) {
    ElMessage.warning('请填写 API Key')
    return
  }
  try {
    if (editing.value && form.value.id) {
      await adminUpdateAiEndpoint(form.value.id, form.value)
      ElMessage.success('端点已更新（立即生效）')
    } else {
      await adminCreateAiEndpoint(form.value)
      ElMessage.success('端点已创建（立即生效）')
    }
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  }
}

const handleDelete = async (endpoint: AiEndpoint) => {
  try {
    await ElMessageBox.confirm(`确定删除端点「${endpoint.model}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminDeleteAiEndpoint(endpoint.id)
    ElMessage.success('端点已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

const handleToggle = async (endpoint: AiEndpoint) => {
  try {
    await adminToggleAiEndpoint(endpoint.id, !endpoint.enabled)
    ElMessage.success(endpoint.enabled ? '已停用' : '已启用')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const handleTest = async (endpoint: AiEndpoint) => {
  testingId.value = endpoint.id
  testResult.value = null
  try {
    testResult.value = await adminTestAiEndpoint(endpoint.id)
  } catch (e: any) {
    testResult.value = { success: false, message: e?.response?.data?.message || '测试失败' }
  } finally {
    testingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="ai-endpoints-page">
    <div class="page-header">
      <h2>AI 端点管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增端点</span>
        </el-button>
      </div>
    </div>
    <p class="page-desc">
      平台默认模型走端点池轮询：调用失败自动冷却 60 秒并切换到下一个端点，规避 429 限速。配置即时生效，无需重启。
    </p>

    <el-card shadow="never" class="panel-card">
      <PagedTable :data="endpoints" :loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="model" label="模型" min-width="180" />
        <el-table-column prop="baseUrl" label="接口地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="apiKeyMasked" label="API Key" width="150" />
        <el-table-column prop="remark" label="备注" min-width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'cooldown' ? 'danger' : row.enabled ? 'success' : 'info'" size="small">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsed" label="最近使用" width="180" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button size="small" link type="primary" :loading="testingId === row.id" @click="handleTest(row)">测试</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link :type="row.enabled ? 'warning' : 'success'" @click="handleToggle(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无端点，点击右上角新增（可配置多个厂商规避限速）" :image-size="80" />
        </template>
      </PagedTable>

      <el-alert
        v-if="testResult"
        :type="testResult.success ? 'success' : 'error'"
        :title="testResult.message"
        :closable="false"
        class="test-result"
      />
    </el-card>

    <el-dialog v-model="dialog" :title="editing ? '编辑端点' : '新增端点'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="接口地址" required>
          <el-input v-model="form.baseUrl" placeholder="https://openrouter.ai/api/v1" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.model" placeholder="deepseek/deepseek-chat" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editing ? '留空保持不变' : 'sk-...'"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="如：OpenRouter 主端点 / 备用厂商" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-endpoints-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header h2 {
  font-size: 20px;
  color: #111827;
}

.page-desc {
  color: #909399;
  font-size: 13px;
}

.panel-card {
  border-radius: 14px;
  border: 1px solid #ebeef5;
}

.test-result {
  margin-top: 14px;
}
</style>
