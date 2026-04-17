<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getLlmConfigs, createLlmConfig, updateLlmConfig, deleteLlmConfig,
  type LlmConfig,
} from '@/api/llmConfig'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Edit, Star, StarFilled, Lock } from '@element-plus/icons-vue'

const configs = ref<LlmConfig[]>([])
const loading = ref(true)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = ref<Partial<LlmConfig>>({
  name: '',
  provider: 'siliconflow',
  apiKey: '',
  modelName: '',
  baseUrl: '',
  isDefault: false,
  multimodal: false,
  secretKey: '',
})

const editingIsSystem = ref(false)

const providerOptions = [
  { value: 'ollama', label: 'Ollama（本地/远程）' },
  { value: 'tencent', label: '腾讯翻译' },
  { value: 'siliconflow', label: 'SiliconFlow' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'zhipu', label: '智谱 AI（GLM）' },
  { value: 'gemini', label: 'Google Gemini' },
  { value: 'openai', label: 'OpenAI' },
  { value: 'custom', label: '自定义 OpenAI 兼容' },
]

/** 需要用户手动填写 Base URL 的 provider */
const needsBaseUrl = new Set(['ollama', 'custom'])

const isTencentProvider = computed(() => form.value.provider === 'tencent')
const isOllamaProvider = computed(() => form.value.provider === 'ollama')
const showBaseUrl = computed(() => needsBaseUrl.has(form.value.provider ?? ''))

const systemConfigs = computed(() => configs.value.filter(c => c.isSystem))
const userConfigs = computed(() => configs.value.filter(c => !c.isSystem))

function onProviderChange(val: string) {
  if (!needsBaseUrl.has(val)) {
    form.value.baseUrl = ''
  }
  if (val === 'ollama') {
    form.value.apiKey = 'ollama'
  }
  if (val === 'tencent') {
    form.value.multimodal = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getLlmConfigs()
    configs.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  editingIsSystem.value = false
  form.value = {
    name: '',
    provider: 'siliconflow',
    apiKey: '',
    modelName: '',
    baseUrl: '',
    isDefault: false,
    multimodal: false,
    secretKey: '',
  }
  dialogVisible.value = true
}

function openEdit(config: LlmConfig) {
  if (config.isSystem) {
    ElMessage.info('系统预设配置不可编辑')
    return
  }
  editingId.value = config.id
  editingIsSystem.value = config.isSystem
  form.value = { ...config }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name?.trim()) {
    ElMessage.warning('请输入配置名称')
    return
  }
  if (!isTencentProvider.value && !isOllamaProvider.value && !form.value.apiKey?.trim()) {
    ElMessage.warning('请输入 API Key')
    return
  }
  if (!form.value.modelName?.trim() && !isTencentProvider.value) {
    ElMessage.warning('请输入模型名称')
    return
  }
  if (form.value.provider === 'custom' && !form.value.baseUrl?.trim()) {
    ElMessage.warning('自定义服务商必须填写 Base URL')
    return
  }

  try {
    if (editingId.value) {
      await updateLlmConfig(editingId.value, form.value)
      ElMessage.success('配置已更新')
    } else {
      await createLlmConfig(form.value)
      ElMessage.success('配置已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch {
    // handled by interceptor
  }
}

async function handleDelete(config: LlmConfig) {
  if (config.isSystem) {
    ElMessage.info('系统预设配置不可删除')
    return
  }
  await ElMessageBox.confirm(`确定删除配置"${config.name}"吗？`, '确认删除', { type: 'warning' })
  await deleteLlmConfig(config.id)
  ElMessage.success('已删除')
  await loadData()
}

async function handleSetDefault(config: LlmConfig) {
  await updateLlmConfig(config.id, { isDefault: true })
  ElMessage.success('已设为默认')
  await loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="llm-config-page">
    <div class="page-header">
      <h2>翻译模型管理</h2>
      <p class="page-desc">管理系统内置和自定义的翻译模型，支持 Ollama、OpenAI 兼容 API 和腾讯翻译</p>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建配置</el-button>
    </div>

    <div v-loading="loading" class="config-sections">
      <!-- 系统预设 -->
      <div v-if="systemConfigs.length > 0" class="section">
        <h3 class="section-title">系统预设</h3>
        <div class="config-list">
          <el-card v-for="c in systemConfigs" :key="c.id" class="config-card system-card" shadow="hover">
            <div class="card-header">
              <div class="card-title">
                <el-icon color="#909399" :size="16"><Lock /></el-icon>
                <span>{{ c.name }}</span>
                <el-tag size="small" type="info">{{ c.provider }}</el-tag>
                <el-tag v-if="c.multimodal" size="small" type="success">多模态</el-tag>
                <el-tag size="small" type="warning">系统</el-tag>
              </div>
            </div>
            <div class="card-body">
              <div class="card-field"><span class="field-label">模型：</span>{{ c.modelName }}</div>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 自定义配置 -->
      <div class="section">
        <h3 class="section-title">自定义配置</h3>
        <div class="config-list">
          <el-empty v-if="!loading && userConfigs.length === 0" description="暂无自定义配置，点击上方按钮新建" />

          <el-card v-for="c in userConfigs" :key="c.id" class="config-card" shadow="hover">
            <div class="card-header">
              <div class="card-title">
                <el-icon v-if="c.isDefault" color="#f59e0b" :size="18"><StarFilled /></el-icon>
                <span>{{ c.name }}</span>
                <el-tag size="small" type="info">{{ c.provider }}</el-tag>
                <el-tag v-if="c.multimodal" size="small" type="success">多模态</el-tag>
                <el-tag v-if="c.isDefault" size="small" type="warning">默认</el-tag>
              </div>
              <div class="card-actions">
                <el-button v-if="!c.isDefault" text size="small" @click="handleSetDefault(c)">
                  <el-icon><Star /></el-icon> 设为默认
                </el-button>
                <el-button text size="small" :icon="Edit" @click="openEdit(c)">编辑</el-button>
                <el-button text size="small" type="danger" :icon="Delete" @click="handleDelete(c)">删除</el-button>
              </div>
            </div>
            <div class="card-body">
              <div class="card-field"><span class="field-label">模型：</span>{{ c.modelName || '-' }}</div>
              <div class="card-field"><span class="field-label">API Key：</span>{{ c.apiKey }}</div>
              <div v-if="c.baseUrl" class="card-field"><span class="field-label">Base URL：</span>{{ c.baseUrl }}</div>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模型配置' : '新建模型配置'" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.name" placeholder="如：SiliconFlow Qwen-VL" />
        </el-form-item>
        <el-form-item label="服务商">
          <el-select v-model="form.provider" @change="onProviderChange">
            <el-option
              v-for="opt in providerOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="多模态">
          <el-switch v-model="form.multimodal" :disabled="isTencentProvider" />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">
            开启后将发送原始图片到模型以获取更精确翻译
          </span>
        </el-form-item>

        <template v-if="isTencentProvider">
          <el-form-item label="SecretId" required>
            <el-input v-model="form.apiKey" placeholder="腾讯云 SecretId" />
          </el-form-item>
          <el-form-item label="SecretKey" required>
            <el-input v-model="form.secretKey" placeholder="腾讯云 SecretKey" show-password />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item v-if="!isOllamaProvider" label="API Key" required>
            <el-input v-model="form.apiKey" placeholder="输入 API Key" show-password />
          </el-form-item>
          <el-form-item label="模型名称" required>
            <el-input v-model="form.modelName" placeholder="如：Qwen/Qwen2.5-VL-72B-Instruct" />
          </el-form-item>
          <el-form-item v-if="showBaseUrl" label="Base URL" :required="form.provider === 'custom'">
            <el-input
              v-model="form.baseUrl"
              :placeholder="isOllamaProvider ? 'http://localhost:11434' : 'https://your-api-endpoint.com/v1'"
            />
          </el-form-item>
        </template>

        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.llm-config-page { max-width: 900px; margin: 0 auto; }

.page-header {
  margin-bottom: 24px;
}

.page-header h2 { margin: 0 0 4px; }
.page-header h2 { color: var(--color-text); }
.page-desc { color: var(--color-text-secondary); font-size: 14px; margin: 0 0 16px; }

.config-sections { display: flex; flex-direction: column; gap: 24px; }

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
}

.config-list { display: flex; flex-direction: column; gap: 12px; }

.config-card { border-radius: var(--radius-md); }

.system-card {
  opacity: 0.85;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.card-actions { display: flex; gap: 4px; }

.card-body { display: flex; flex-wrap: wrap; gap: 8px 24px; }

.card-field { font-size: 13px; color: var(--color-text); }
.field-label { color: var(--color-text-secondary); }
</style>
