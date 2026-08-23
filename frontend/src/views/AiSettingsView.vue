<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiConfig, saveAiConfig, clearAiConfig, testAiConnection, type AiUserConfig } from '../api/ai'

/** 多厂商预设（全部 OpenAI 兼容协议） */
const PROVIDERS = [
  { key: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', models: ['deepseek-chat', 'deepseek-reasoner'] },
  { key: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', models: ['gpt-4o-mini', 'gpt-4o'] },
  { key: 'moonshot', name: 'Kimi（月之暗面）', baseUrl: 'https://api.moonshot.cn/v1', models: ['moonshot-v1-8k', 'moonshot-v1-32k'] },
  { key: 'zhipu', name: '智谱 GLM', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', models: ['glm-4-flash', 'glm-4-air'] },
  { key: 'qwen', name: '通义千问（阿里云）', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', models: ['qwen-plus', 'qwen-turbo'] },
  { key: 'siliconflow', name: '硅基流动 SiliconFlow', baseUrl: 'https://api.siliconflow.cn/v1', models: ['deepseek-ai/DeepSeek-V3', 'deepseek-ai/DeepSeek-R1'] },
  { key: 'custom', name: '自定义（OpenAI 兼容）', baseUrl: '', models: [] }
]

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const clearing = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const form = ref({
  provider: 'deepseek',
  baseUrl: 'https://api.deepseek.com',
  apiKey: '',
  model: 'deepseek-chat',
  embeddingBaseUrl: '',
  embeddingApiKey: '',
  embeddingModel: '',
  enabled: true
})
const configured = ref(false)
const apiKeyMasked = ref('')
const embeddingApiKeyMasked = ref('')

const selectProvider = (key: string) => {
  const provider = PROVIDERS.find((p) => p.key === key)
  if (!provider) return
  form.value.provider = key
  form.value.baseUrl = provider.baseUrl
  if (provider.models.length) {
    form.value.model = provider.models[0]
  }
}

const load = async () => {
  loading.value = true
  try {
    const config = await getAiConfig()
    configured.value = config.configured
    apiKeyMasked.value = config.apiKeyMasked || ''
    if (config.configured) {
      form.value.provider = config.provider || 'deepseek'
      form.value.baseUrl = config.baseUrl || ''
      form.value.model = config.model || ''
      form.value.embeddingBaseUrl = config.embeddingBaseUrl || ''
      form.value.embeddingApiKey = ''
      form.value.embeddingModel = config.embeddingModel || ''
      embeddingApiKeyMasked.value = config.embeddingApiKeyMasked || ''
      form.value.enabled = config.enabled
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载配置失败')
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (!form.value.baseUrl.trim()) {
    ElMessage.warning('请填写接口地址')
    return
  }
  if (!form.value.model.trim()) {
    ElMessage.warning('请填写模型名称')
    return
  }
  if (!apiKeyMasked.value && !form.value.apiKey.trim()) {
    ElMessage.warning('请填写 API Key（未配置过 Key 时必须填写）')
    return
  }
  saving.value = true
  try {
    const saved = await saveAiConfig({
      provider: form.value.provider,
      baseUrl: form.value.baseUrl.trim(),
      apiKey: form.value.apiKey.trim() || undefined,
      model: form.value.model.trim(),
      embeddingBaseUrl: form.value.embeddingBaseUrl.trim() || undefined,
      embeddingApiKey: form.value.embeddingApiKey.trim() || undefined,
      embeddingModel: form.value.embeddingModel.trim() || undefined,
      enabled: form.value.enabled
    })
    configured.value = saved.configured
    apiKeyMasked.value = saved.apiKeyMasked || ''
    embeddingApiKeyMasked.value = saved.embeddingApiKeyMasked || ''
    form.value.apiKey = ''
    form.value.embeddingApiKey = ''
    form.value.embeddingBaseUrl = saved.embeddingBaseUrl || ''
    form.value.embeddingModel = saved.embeddingModel || ''
    ElMessage.success(
      saved.embeddingBaseUrl
        ? 'AI 配置已保存，并已自动启用向量化（同一服务支持 embedding），所有功能均可使用'
        : 'AI 配置已保存。当前服务不支持向量化，知识问答将使用关键词检索'
    )
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleTest = async () => {
  if (!form.value.baseUrl.trim()) {
    ElMessage.warning('请先填写接口地址')
    return
  }
  if (!form.value.apiKey.trim()) {
    ElMessage.warning('测试连接需要重新输入 API Key（脱敏展示的 Key 不能用于测试）')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testAiConnection({
      baseUrl: form.value.baseUrl.trim(),
      apiKey: form.value.apiKey.trim(),
      model: form.value.model.trim()
    })
  } catch (e: any) {
    testResult.value = { success: false, message: e?.response?.data?.message || '连接失败' }
  } finally {
    testing.value = false
  }
}

const handleClear = async () => {
  clearing.value = true
  try {
    await clearAiConfig()
    configured.value = false
    apiKeyMasked.value = ''
    embeddingApiKeyMasked.value = ''
    form.value.apiKey = ''
    form.value.embeddingApiKey = ''
    form.value.embeddingBaseUrl = ''
    form.value.embeddingModel = ''
    ElMessage.success('已清除配置，将使用平台默认云端模型')
  } catch (e: any) {
    // 清除失败时保持界面状态不变，避免 UI 与后端不一致
    ElMessage.error(e?.response?.data?.message || '清除配置失败，请稍后重试')
  } finally {
    clearing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="ai-settings-page">
    <div class="page-header">
      <h2>AI 模型设置</h2>
      <p>配置你自己的 API Key 使用指定模型（推荐，稳定不受平台额度影响）；不配置时自动使用平台默认云端模型</p>
    </div>

    <el-card v-loading="loading" shadow="never" class="panel-card">
      <el-alert type="info" :closable="false" class="tip-alert">
        <template #title>
          使用优先级：你的 API Key（多厂商） → 平台默认云端模型（平台额度有限，推荐自配 Key）
        </template>
      </el-alert>

      <el-form label-width="110px" label-position="left" class="config-form">
        <el-form-item label="模型厂商">
          <el-select v-model="form.provider" style="width: 320px" @change="selectProvider">
            <el-option v-for="p in PROVIDERS" :key="p.key" :label="p.name" :value="p.key" />
          </el-select>
          <span class="form-tip">所有厂商均为 OpenAI 兼容接口</span>
        </el-form-item>

        <el-form-item label="接口地址">
          <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" style="width: 420px" />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="apiKeyMasked ? `已保存（${apiKeyMasked}），留空保持不变` : 'sk-...'"
            style="width: 420px"
          />
          <span class="form-tip">Key 加密存储，仅用于调用你选择的模型</span>
        </el-form-item>

        <el-form-item label="模型名称">
          <el-input v-model="form.model" placeholder="deepseek-chat" style="width: 420px" />
        </el-form-item>

        <el-divider content-position="left">向量化配置（可选）</el-divider>
        <el-form-item label="向量化地址">
          <el-input v-model="form.embeddingBaseUrl" placeholder="如 https://api.siliconflow.cn/v1，留空使用平台端点" style="width: 420px" />
          <span class="form-tip">向量化需要 embedding 模型，与对话模型分开配置</span>
        </el-form-item>
        <el-form-item label="向量化 Key">
          <el-input
            v-model="form.embeddingApiKey"
            type="password"
            show-password
            :placeholder="embeddingApiKeyMasked ? `已保存（${embeddingApiKeyMasked}），留空保持不变` : 'sk-...'"
            style="width: 420px"
          />
        </el-form-item>
        <el-form-item label="向量化模型">
          <el-input v-model="form.embeddingModel" placeholder="text-embedding-3-small（如 BAAI/bge-m3）" style="width: 420px" />
          <span class="form-tip">推荐：硅基流动 BAAI/bge-m3，或 OpenAI text-embedding-3-small</span>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-form-item label=" ">
          <div class="action-row">
            <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
            <el-button :loading="testing" @click="handleTest">测试连接</el-button>
            <el-button v-if="configured" type="danger" plain :loading="clearing" @click="handleClear">清除配置（改用平台默认模型）</el-button>
          </div>
          <el-alert
            v-if="testResult"
            :type="testResult.success ? 'success' : 'error'"
            :title="testResult.message"
            :closable="false"
            class="test-result"
          />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.ai-settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
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

.tip-alert {
  margin-bottom: 18px;
}

.config-form {
  margin-top: 8px;
}

.form-tip {
  margin-left: 10px;
  color: #b0b8c4;
  font-size: 12px;
}

.action-row {
  display: flex;
  gap: 10px;
}

.test-result {
  margin-top: 12px;
}

.free-model-box {
  padding: 14px 16px;
  background: #f8fbff;
  border-radius: 10px;
}

.free-model-box h4 {
  color: #303133;
  font-size: 14px;
}

.free-model-box p {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 768px) {
  .ai-settings-page {
    max-width: none;
  }

  .config-form :deep(.el-form-item__content) {
    flex-wrap: wrap;
  }

  .config-form .el-input,
  .config-form .el-select {
    width: 100% !important;
  }

  .form-tip {
    display: block;
    margin-left: 0;
    margin-top: 6px;
  }

  .action-row {
    flex-wrap: wrap;
  }
}
</style>
