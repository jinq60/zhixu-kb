<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiConfig, saveAiConfig, clearAiConfig, testAiConnection, type AiUserConfig } from '../api/ai'

/** 多厂商预设（全部 OpenAI 兼容协议）：含接口地址/常用模型 ID/取 Key 入口，
 *  模型均可下拉选择或手动输入自定义 ID */
const PROVIDERS = [
  {
    key: 'deepseek',
    name: 'DeepSeek（深度求索）',
    baseUrl: 'https://api.deepseek.com',
    models: ['deepseek-chat', 'deepseek-reasoner'],
    embeddingModels: [],
    keyUrl: 'https://platform.deepseek.com/api_keys',
    note: '国内直连；deepseek-chat 为主力对话模型，价格低'
  },
  {
    key: 'openai',
    name: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    models: ['gpt-4o-mini', 'gpt-4o', 'gpt-4.1-mini', 'o4-mini'],
    embeddingModels: ['text-embedding-3-small', 'text-embedding-3-large'],
    keyUrl: 'https://platform.openai.com/api-keys',
    note: '国内需自备网络；text-embedding-3-small 可直接用作向量化'
  },
  {
    key: 'moonshot',
    name: 'Kimi（月之暗面）',
    baseUrl: 'https://api.moonshot.cn/v1',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k', 'kimi-k2-0711-preview'],
    embeddingModels: [],
    keyUrl: 'https://platform.moonshot.cn/console/api-keys',
    note: '国内直连；长文本能力强，按上下文长度选模型（8k/32k/128k）'
  },
  {
    key: 'zhipu',
    name: '智谱 GLM',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    models: ['glm-4-flash', 'glm-4-air', 'glm-4-plus', 'glm-4-long'],
    embeddingModels: ['embedding-3'],
    keyUrl: 'https://open.bigmodel.cn/usercenter/apikeys',
    note: '国内直连；glm-4-flash 免费，适合先体验'
  },
  {
    key: 'qwen',
    name: '通义千问（阿里云百炼）',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: ['qwen-plus', 'qwen-turbo', 'qwen-max', 'qwen-long'],
    embeddingModels: ['text-embedding-v3', 'text-embedding-v2'],
    keyUrl: 'https://bailian.console.aliyun.com/?apiKey=1',
    note: '国内直连；text-embedding-v3 可用作向量化'
  },
  {
    key: 'siliconflow',
    name: '硅基流动 SiliconFlow',
    baseUrl: 'https://api.siliconflow.cn/v1',
    models: [
      'deepseek-ai/DeepSeek-V3',
      'Qwen/Qwen2.5-72B-Instruct',
      'THUDM/glm-4-9b-chat',
      'deepseek-ai/DeepSeek-R1'
    ],
    embeddingModels: ['BAAI/bge-m3', 'netease-youdao/bce-embedding-base_v1'],
    keyUrl: 'https://cloud.siliconflow.cn/account/ak',
    note: '国内直连；聚合多家开源模型，BAAI/bge-m3 是常用向量化模型，注册送额度'
  },
  {
    key: 'openrouter',
    name: 'OpenRouter（聚合）',
    baseUrl: 'https://openrouter.ai/api/v1',
    models: [
      'deepseek/deepseek-chat',
      'google/gemini-2.0-flash-exp:free',
      'meta-llama/llama-3.3-70b-instruct'
    ],
    embeddingModels: [],
    keyUrl: 'https://openrouter.ai/keys',
    note: '国际聚合网关，部分模型带 :free 后缀可零成本使用'
  },
  {
    key: 'custom',
    name: '自定义（OpenAI 兼容）',
    baseUrl: '',
    models: [],
    embeddingModels: [],
    keyUrl: '',
    note: '任何兼容 OpenAI /chat/completions 协议的服务均可接入'
  }
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

/** 当前厂商的预设信息（模板区展示模型下拉/取 Key 链接/说明用） */
const activeProvider = () => PROVIDERS.find((p) => p.key === form.value.provider)

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
        <div class="config-grid">
          <!-- 左栏：对话模型配置 -->
          <section class="config-col">
            <h4 class="col-title">对话模型</h4>
            <el-form-item label="模型厂商">
              <el-select v-model="form.provider" @change="selectProvider">
                <el-option v-for="p in PROVIDERS" :key="p.key" :label="p.name" :value="p.key" />
              </el-select>
              <span class="form-tip">所有厂商均为 OpenAI 兼容接口</span>
            </el-form-item>

            <el-form-item label="接口地址">
              <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
            </el-form-item>

            <el-form-item label="API Key">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="apiKeyMasked ? `已保存（${apiKeyMasked}），留空保持不变` : 'sk-...'"
              />
              <span class="form-tip">
                Key 加密存储，仅用于调用你选择的模型
                <a
                  v-if="activeProvider()?.keyUrl"
                  :href="activeProvider()?.keyUrl"
                  target="_blank"
                  rel="noopener"
                  class="key-link"
                >获取 {{ activeProvider()?.name.split('（')[0] }} Key →</a>
              </span>
            </el-form-item>

            <el-form-item label="模型名称">
              <el-select
                v-model="form.model"
                filterable
                allow-create
                default-first-option
                placeholder="选择预设或输入模型 ID"
              >
                <el-option v-for="m in activeProvider()?.models || []" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>

            <div v-if="activeProvider()?.note" class="provider-note">{{ activeProvider()?.note }}</div>

            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>

            <el-form-item label=" ">
              <div class="action-row">
                <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
                <el-button :loading="testing" @click="handleTest">测试连接</el-button>
                <el-button v-if="configured" type="danger" plain :loading="clearing" @click="handleClear">清除配置</el-button>
              </div>
              <el-alert
                v-if="testResult"
                :type="testResult.success ? 'success' : 'error'"
                :title="testResult.message"
                :closable="false"
                class="test-result"
              />
            </el-form-item>
          </section>

          <!-- 右栏：向量化配置（可选） -->
          <section class="config-col embedding-col">
            <h4 class="col-title">
              向量化配置
              <el-tag size="small" type="info" class="optional-tag">可选</el-tag>
            </h4>
            <p class="col-desc">向量化需要 embedding 模型，与对话模型分开配置；留空时使用平台端点。</p>

            <el-form-item label="向量化地址">
              <el-input v-model="form.embeddingBaseUrl" placeholder="如 https://api.siliconflow.cn/v1" />
              <span class="form-tip">留空使用平台端点（若平台端点支持 embedding 会自动启用）</span>
            </el-form-item>

            <el-form-item label="向量化 Key">
              <el-input
                v-model="form.embeddingApiKey"
                type="password"
                show-password
                :placeholder="embeddingApiKeyMasked ? `已保存（${embeddingApiKeyMasked}），留空保持不变` : 'sk-...'"
              />
            </el-form-item>

            <el-form-item label="向量化模型">
              <el-select
                v-model="form.embeddingModel"
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="选择预设或输入模型 ID"
              >
                <el-option
                  v-for="m in activeProvider()?.embeddingModels?.length
                    ? activeProvider()?.embeddingModels
                    : ['text-embedding-3-small', 'BAAI/bge-m3']"
                  :key="m"
                  :label="m"
                  :value="m"
                />
              </el-select>
              <span class="form-tip">推荐：硅基流动 BAAI/bge-m3，或 OpenAI text-embedding-3-small</span>
            </el-form-item>

            <div class="embedding-hint">
              配置后知识问答将结合向量语义检索，召回更准确；未配置时自动降级为关键词检索。
            </div>
          </section>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.ai-settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1080px;
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

/* 双栏布局：左=对话模型，右=向量化配置 */
.config-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 28px;
  align-items: start;
}

.config-col {
  min-width: 0;
  padding: 18px 20px 6px;
  border: 1px solid #f0f2f5;
  border-radius: 12px;
  background: #fafbfc;
}

.embedding-col {
  background: #f8fbff;
  border-color: #e6f0fb;
}

.col-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.optional-tag {
  font-weight: 400;
}

.col-desc {
  margin: 0 0 14px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.embedding-hint {
  margin: 4px 0 16px 110px;
  padding: 10px 12px;
  background: #fff;
  border: 1px dashed #d9e6f5;
  border-radius: 8px;
  color: #6b7a8d;
  font-size: 12px;
  line-height: 1.6;
}

.config-col :deep(.el-input),
.config-col :deep(.el-select) {
  width: 100%;
}

.form-tip {
  margin-left: 10px;
  color: #b0b8c4;
  font-size: 12px;
}

.key-link {
  margin-left: 8px;
  color: #409eff;
  text-decoration: none;
}

.provider-note {
  margin: 0 0 14px 110px;
  padding: 8px 12px;
  background: #fff;
  border: 1px dashed #e6e8eb;
  border-radius: 8px;
  color: #8b96a5;
  font-size: 12px;
  line-height: 1.6;
}

.action-row {
  display: flex;
  gap: 10px;
}

.test-result {
  margin-top: 12px;
}

@media (max-width: 900px) {
  .ai-settings-page {
    max-width: none;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .config-form :deep(.el-form-item__content) {
    flex-wrap: wrap;
  }

  .config-col .el-input,
  .config-col .el-select {
    width: 100% !important;
  }

  .form-tip {
    display: block;
    margin-left: 0;
    margin-top: 6px;
  }

  .embedding-hint {
    margin-left: 0;
  }

  .action-row {
    flex-wrap: wrap;
  }
}
</style>
