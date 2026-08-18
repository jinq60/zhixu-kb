<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PagedTable from '../components/PagedTable.vue'
import {
  getSystemOverview,
  getAiEngineState,
  resetAiEngine,
  getGraphOverview,
  type SystemOverview,
  type AiEngineState,
  type GraphOverview
} from '../api/admin'

const overview = ref<SystemOverview | null>(null)
const engineState = ref<AiEngineState | null>(null)
const graphOverview = ref<GraphOverview | null>(null)
const switching = ref(false)
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    overview.value = await getSystemOverview(10)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载系统状态失败')
  } finally {
    loading.value = false
  }
}

const loadAiEngine = async () => {
  try {
    engineState.value = await getAiEngineState()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载 AI 引擎状态失败')
  }
}

const handleReset = async () => {
  switching.value = true
  try {
    engineState.value = await resetAiEngine()
    ElMessage.success('已恢复为配置文件模式')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '重置失败')
  } finally {
    switching.value = false
  }
}

onMounted(async () => {
  await load()
  await loadAiEngine()
  graphOverview.value = await getGraphOverview().catch(() => null)
})
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>系统总览</h2>
      <p>AI 引擎、知识图谱与系统监控状态</p>
    </div>

    <div v-loading="loading" class="overview-grid">
      <!-- AI 引擎 -->
      <el-card shadow="never" class="panel-card">
        <template #header><span class="card-title">AI 引擎</span></template>
        <div class="engine-row">
          <span class="label">当前生效</span>
          <el-tag type="primary" effect="dark">云端 API</el-tag>
        </div>
        <div class="engine-row">
          <span class="label">配置类型</span>
          <span class="value">{{ engineState?.configuredEngineType }}</span>
        </div>
        <div class="engine-row">
          <span class="label">健康状态</span>
          <el-tag :type="engineState?.healthy ? 'success' : 'danger'" size="small">
            {{ engineState?.healthy ? '健康' : '不可用' }}
          </el-tag>
        </div>
        <div class="engine-row">
          <span class="label">运行覆盖</span>
          <span class="value">{{ engineState?.overrideEnabled ? engineState.overrideEngineType : '未覆盖（跟随配置）' }}</span>
        </div>
        <el-divider />
        <div class="engine-actions">
          <el-button size="small" plain :loading="switching" @click="handleReset">恢复配置模式</el-button>
        </div>
      </el-card>

      <!-- 系统监控 -->
      <el-card shadow="never" class="panel-card">
        <template #header><span class="card-title">系统监控</span></template>
        <template v-if="overview">
          <div class="engine-row">
            <span class="label">接口限流</span>
            <span class="value">{{ overview.rateLimitPerMinute }} 次/分钟</span>
          </div>
          <div class="engine-row">
            <span class="label">资源监控</span>
            <span class="value">{{ overview.monitorEnabled ? '已开启' : '已关闭' }}</span>
          </div>
        </template>
      </el-card>

      <!-- 知识图谱 -->
      <el-card shadow="never" class="panel-card">
        <template #header><span class="card-title">知识图谱（Neo4j）</span></template>
        <template v-if="graphOverview">
          <div class="engine-row">
            <span class="label">服务状态</span>
            <el-tag :type="graphOverview.available ? 'success' : 'danger'" size="small">
              {{ graphOverview.available ? '可用' : '不可用' }}
            </el-tag>
          </div>
          <div class="engine-row">
            <span class="label">笔记数</span>
            <span class="value">{{ graphOverview.noteCount }}</span>
          </div>
          <div class="engine-row">
            <span class="label">实体数</span>
            <span class="value">{{ graphOverview.entityCount }}</span>
          </div>
          <div class="engine-row">
            <span class="label">关系数</span>
            <span class="value">{{ graphOverview.relationCount }}</span>
          </div>
          <p v-if="graphOverview.message" class="warn-text">{{ graphOverview.message }}</p>
        </template>
      </el-card>
    </div>

    <!-- 最近告警 -->
    <el-card shadow="never" class="panel-card" v-if="overview && overview.recentAlerts.length">
      <template #header><span class="card-title">最近告警（{{ overview.recentAlertCount }}）</span></template>
      <PagedTable :data="overview.recentAlerts" :page-size="10">
        <el-table-column prop="severity" label="级别" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.severity === 'WARN' ? 'warning' : 'info'">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="160" />
        <el-table-column prop="message" label="内容" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </PagedTable>
    </el-card>
  </div>
</template>

<style scoped>
.admin-page {
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

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.panel-card {
  border-radius: 14px;
  border: 1px solid #ebeef5;
}

.card-title {
  font-weight: 600;
  color: #111827;
}

.engine-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.label {
  color: #909399;
  font-size: 13px;
}

.value {
  color: #303133;
  font-size: 14px;
}

.engine-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.warn-text {
  margin-top: 10px;
  color: #f56c6c;
  font-size: 12px;
}
</style>
