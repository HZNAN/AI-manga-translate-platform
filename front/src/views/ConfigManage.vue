<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { getConfigs, createConfig, updateConfig, deleteConfig, getPresets } from '@/api/config'
import { getLlmConfigs, type LlmConfig } from '@/api/llmConfig'
import type { TranslateConfig } from '@/types/manga'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, CopyDocument } from '@element-plus/icons-vue'

const configs = ref<TranslateConfig[]>([])
const presets = ref<TranslateConfig[]>([])
const llmConfigs = ref<LlmConfig[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新建配置')
const saving = ref(false)
const editingId = ref<number | null>(null)

const defaultFormValues = {
  name: '',
  targetLang: 'CHS',
  translator: 'none',
  llmConfigId: undefined as number | undefined,
  detector: 'ctd',
  detectionSize: 1536,
  textThreshold: 0.5,
  boxThreshold: 0.7,
  unclipRatio: 2.3,
  ocr: '48px',
  sourceLang: 'japanese',
  useMocrMerge: false,
  inpainter: 'lama_mpe',
  inpaintingSize: 2560,
  inpaintingPrecision: 'bf16',
  renderer: 'default',
  alignment: 'auto',
  direction: 'auto',
  fontSizeOffset: 0,
  maskDilationOffset: 0,
  kernelSize: 3,
  colorizer: 'none',
  isDefault: false,
}

const form = reactive({ ...defaultFormValues })

const targetLangOptions = [
  { value: 'CHS', label: '简体中文' },
  { value: 'CHT', label: '繁体中文' },
  { value: 'ENG', label: '英语' },
  { value: 'JPN', label: '日语' },
  { value: 'KOR', label: '韩语' },
  { value: 'FRA', label: '法语' },
  { value: 'DEU', label: '德语' },
  { value: 'ESP', label: '西班牙语' },
  { value: 'RUS', label: '俄语' },
  { value: 'VIN', label: '越南语' },
  { value: 'PTB', label: '葡萄牙语(巴西)' },
  { value: 'NLD', label: '荷兰语' },
  { value: 'POL', label: '波兰语' },
  { value: 'UKR', label: '乌克兰语' },
  { value: 'THA', label: '泰语' },
  { value: 'ARA', label: '阿拉伯语' },
  { value: 'IND', label: '印尼语' },
  { value: 'TRK', label: '土耳其语' },
]

const systemLlmConfigs = computed(() =>
  llmConfigs.value.filter(c => c.isSystem),
)
const userLlmConfigs = computed(() =>
  llmConfigs.value.filter(c => !c.isSystem),
)

function getTranslatorLabel(config: TranslateConfig) {
  if (config.llmConfigId) {
    const llm = llmConfigs.value.find(c => c.id === config.llmConfigId)
    if (llm) return `${llm.name}${llm.multimodal ? ' (多模态)' : ''}`
    return `LLM #${config.llmConfigId}`
  }
  return config.translator || '-'
}

function onLlmConfigChange(llmId: number | undefined) {
  form.llmConfigId = llmId
  if (llmId) {
    form.translator = 'none'
  }
}

const detectorOptions = [
  { value: 'ctd', label: 'CTD 漫画文本检测（默认）' },
  { value: 'default', label: 'DBNet ResNet34（通用）' },
]

const ocrOptions = [
  { value: '48px', label: '48px OCR（默认）' },
  { value: 'paddle_vl', label: 'PaddleOCR-VL（漫画增强）' },
]

const sourceLangOptions = [
  { value: 'japanese', label: '日语' },
  { value: 'en', label: '英语' },
  { value: 'korean', label: '韩语' },
  { value: 'chinese', label: '中文' },
  { value: 'french', label: '法语' },
  { value: 'german', label: '德语' },
  { value: 'spanish', label: '西班牙语' },
  { value: 'russian', label: '俄语' },
  { value: 'italian', label: '意大利语' },
  { value: 'portuguese', label: '葡萄牙语' },
  { value: 'latin', label: '拉丁语系（通用）' },
]

const inpainterOptions = [
  { value: 'lama_mpe', label: 'LaMa MPE（默认）' },
  { value: 'lama_large', label: 'LaMa Large' },
]

const rendererOptions = [
  { value: 'default', label: '默认' },
  { value: 'manga2eng', label: 'Manga2Eng' },
  { value: 'manga2eng_pillow', label: 'Manga2Eng Pillow' },
]

const alignmentOptions = [
  { value: 'auto', label: '自动' },
  { value: 'left', label: '左对齐' },
  { value: 'center', label: '居中' },
  { value: 'right', label: '右对齐' },
]

const directionOptions = [
  { value: 'auto', label: '自动' },
  { value: 'horizontal', label: '水平' },
  { value: 'vertical', label: '垂直' },
]

const inpaintingPrecisionOptions = [
  { value: 'bf16', label: 'BF16（默认）' },
]

const colorizerOptions = [
  { value: 'none', label: '无（默认）' },
]

async function loadData() {
  loading.value = true
  try {
    const [configRes, presetRes, llmRes] = await Promise.all([
      getConfigs(),
      getPresets(),
      getLlmConfigs(),
    ])
    configs.value = configRes.data
    presets.value = presetRes.data
    llmConfigs.value = llmRes.data
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingId.value = null
  dialogTitle.value = '新建配置'
  Object.assign(form, defaultFormValues)
  dialogVisible.value = true
}

function openEditDialog(config: TranslateConfig) {
  editingId.value = config.id
  dialogTitle.value = '编辑配置'
  Object.assign(form, {
    name: config.name,
    targetLang: config.targetLang,
    translator: config.translator,
    llmConfigId: config.llmConfigId,
    detector: config.detector,
    detectionSize: config.detectionSize,
    textThreshold: config.textThreshold,
    boxThreshold: config.boxThreshold,
    unclipRatio: config.unclipRatio,
    ocr: config.ocr,
    sourceLang: config.sourceLang || 'japanese',
    useMocrMerge: config.useMocrMerge ?? false,
    inpainter: config.inpainter,
    inpaintingSize: config.inpaintingSize,
    inpaintingPrecision: config.inpaintingPrecision,
    renderer: config.renderer,
    alignment: config.alignment,
    direction: config.direction,
    fontSizeOffset: config.fontSizeOffset,
    maskDilationOffset: config.maskDilationOffset,
    kernelSize: config.kernelSize,
    colorizer: config.colorizer,
    isDefault: config.isDefault,
  })
  dialogVisible.value = true
}

function duplicateConfig(config: TranslateConfig) {
  editingId.value = null
  dialogTitle.value = '复制配置'
  Object.assign(form, {
    name: `${config.name} (副本)`,
    targetLang: config.targetLang,
    translator: config.translator,
    llmConfigId: config.llmConfigId,
    detector: config.detector,
    detectionSize: config.detectionSize,
    textThreshold: config.textThreshold,
    boxThreshold: config.boxThreshold,
    unclipRatio: config.unclipRatio,
    ocr: config.ocr,
    sourceLang: config.sourceLang || 'japanese',
    useMocrMerge: config.useMocrMerge ?? false,
    inpainter: config.inpainter,
    inpaintingSize: config.inpaintingSize,
    inpaintingPrecision: config.inpaintingPrecision,
    renderer: config.renderer,
    alignment: config.alignment,
    direction: config.direction,
    fontSizeOffset: config.fontSizeOffset,
    maskDilationOffset: config.maskDilationOffset,
    kernelSize: config.kernelSize,
    colorizer: config.colorizer,
    isDefault: false,
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入配置名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateConfig(editingId.value, { ...form })
      ElMessage.success('配置已更新')
    } else {
      await createConfig({ ...form })
      ElMessage.success('配置已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch {
    // handled by interceptor
  } finally {
    saving.value = false
  }
}

async function handleDelete(config: TranslateConfig) {
  await ElMessageBox.confirm(
    `确定删除配置「${config.name}」吗？`,
    '确认删除',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  try {
    await deleteConfig(config.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch {
    // handled by interceptor
  }
}

function importPreset(preset: TranslateConfig) {
  editingId.value = null
  dialogTitle.value = '从预设创建'
  Object.assign(form, {
    name: preset.name,
    targetLang: preset.targetLang,
    translator: preset.translator,
    llmConfigId: preset.llmConfigId,
    detector: preset.detector,
    detectionSize: preset.detectionSize,
    textThreshold: preset.textThreshold,
    boxThreshold: preset.boxThreshold,
    unclipRatio: preset.unclipRatio,
    ocr: preset.ocr,
    useMocrMerge: preset.useMocrMerge ?? false,
    inpainter: preset.inpainter,
    inpaintingSize: preset.inpaintingSize,
    inpaintingPrecision: preset.inpaintingPrecision,
    renderer: preset.renderer,
    alignment: preset.alignment,
    direction: preset.direction,
    fontSizeOffset: preset.fontSizeOffset,
    maskDilationOffset: preset.maskDilationOffset,
    kernelSize: preset.kernelSize,
    colorizer: preset.colorizer,
    isDefault: false,
  })
  dialogVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <div class="config-manage">
    <div class="config-header">
      <h2 class="page-title">翻译配置管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建配置</el-button>
    </div>

    <div v-loading="loading">
      <!-- 我的配置 -->
      <div class="section">
        <h3 class="section-title">我的配置</h3>
        <div v-if="configs.length > 0" class="config-grid">
          <el-card
            v-for="config in configs"
            :key="config.id"
            shadow="hover"
            class="config-card"
          >
            <template #header>
              <div class="config-card-header">
                <div class="config-name-row">
                  <span class="config-name">{{ config.name }}</span>
                  <el-tag v-if="config.isDefault" type="success" size="small">默认</el-tag>
                </div>
                <div class="config-card-actions">
                  <el-button text size="small" :icon="Edit" @click="openEditDialog(config)">
                    编辑
                  </el-button>
                  <el-button text size="small" :icon="CopyDocument" @click="duplicateConfig(config)">
                    复制
                  </el-button>
                  <el-button text size="small" type="danger" :icon="Delete" @click="handleDelete(config)">
                    删除
                  </el-button>
                </div>
              </div>
            </template>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="目标语言">
                {{ targetLangOptions.find(o => o.value === config.targetLang)?.label || config.targetLang }}
              </el-descriptions-item>
              <el-descriptions-item label="翻译模型">
                {{ getTranslatorLabel(config) }}
              </el-descriptions-item>
              <el-descriptions-item label="检测器">
                {{ config.detector }}
              </el-descriptions-item>
              <el-descriptions-item label="OCR">
                {{ config.ocr }}
              </el-descriptions-item>
              <el-descriptions-item label="修复器">
                {{ config.inpainter }}
              </el-descriptions-item>
              <el-descriptions-item label="渲染器">
                {{ config.renderer }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </div>
        <el-empty v-else description="暂无自定义配置，点击上方按钮新建" />
      </div>

      <!-- 系统预设 -->
      <div class="section">
        <h3 class="section-title">系统预设</h3>
        <p class="section-hint">点击预设可将其导入为自定义配置</p>
        <div v-if="presets.length > 0" class="config-grid">
          <el-card
            v-for="preset in presets"
            :key="preset.id"
            shadow="hover"
            class="config-card preset-card"
            @click="importPreset(preset)"
          >
            <template #header>
              <div class="config-card-header">
                <span class="config-name">{{ preset.name }}</span>
                <el-tag type="info" size="small">预设</el-tag>
              </div>
            </template>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="目标语言">
                {{ targetLangOptions.find(o => o.value === preset.targetLang)?.label || preset.targetLang }}
              </el-descriptions-item>
              <el-descriptions-item label="翻译模型">
                {{ getTranslatorLabel(preset) }}
              </el-descriptions-item>
              <el-descriptions-item label="检测器">
                {{ preset.detector }}
              </el-descriptions-item>
              <el-descriptions-item label="修复器">
                {{ preset.inpainter }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </div>
        <el-empty v-else description="暂无系统预设" />
      </div>
    </div>

    <!-- 新建/编辑配置对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" top="5vh" destroy-on-close>
      <el-form :model="form" label-width="110px" size="default">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.name" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>

        <el-divider content-position="left">翻译模型</el-divider>
        <el-form-item label="目标语言">
          <el-select v-model="form.targetLang" style="width: 100%">
            <el-option v-for="o in targetLangOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="翻译模型">
          <el-select
            :model-value="form.llmConfigId"
            placeholder="选择翻译模型"
            style="width: 100%"
            clearable
            @update:model-value="onLlmConfigChange"
            @clear="form.llmConfigId = undefined; form.translator = 'none'"
          >
            <el-option-group label="系统模型">
              <el-option
                v-for="llm in systemLlmConfigs"
                :key="llm.id"
                :label="`${llm.name}${llm.multimodal ? ' (多模态)' : ''}`"
                :value="llm.id"
              />
            </el-option-group>
            <el-option-group v-if="userLlmConfigs.length > 0" label="自定义模型">
              <el-option
                v-for="llm in userLlmConfigs"
                :key="llm.id"
                :label="`${llm.name}${llm.multimodal ? ' (多模态)' : ''}`"
                :value="llm.id"
              />
            </el-option-group>
          </el-select>
          <div v-if="!form.llmConfigId" class="translator-hint">
            未选择模型时使用 Python 全流程翻译（兜底）
          </div>
        </el-form-item>

        <el-divider content-position="left">检测 & OCR</el-divider>
        <el-form-item label="检测器">
          <el-select v-model="form.detector" style="width: 100%">
            <el-option v-for="o in detectorOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="检测尺寸">
          <el-input-number v-model="form.detectionSize" :min="256" :max="4096" :step="256" />
        </el-form-item>
        <el-form-item label="文本阈值">
          <el-slider v-model="form.textThreshold" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
        <el-form-item label="边框阈值">
          <el-slider v-model="form.boxThreshold" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
        <el-form-item label="Unclip 比率">
          <el-input-number v-model="form.unclipRatio" :min="1" :max="5" :step="0.1" :precision="1" />
        </el-form-item>
        <el-form-item label="OCR 模型">
          <el-select v-model="form.ocr" style="width: 100%">
            <el-option v-for="o in ocrOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.ocr === 'paddle_vl'" label="源语言">
          <el-select v-model="form.sourceLang" style="width: 100%">
            <el-option v-for="o in sourceLangOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div class="form-hint">PaddleOCR-VL 根据源语言构造识别提示词，推荐使用 GPU</div>
        </el-form-item>
        <el-form-item label="文本区域合并">
          <el-switch v-model="form.useMocrMerge" />
          <div class="form-hint">合并相邻文本区域后再进行 OCR，可避免同一气泡被拆分识别</div>
        </el-form-item>

        <el-divider content-position="left">修复 & 渲染</el-divider>
        <el-form-item label="修复器">
          <el-select v-model="form.inpainter" style="width: 100%">
            <el-option v-for="o in inpainterOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="修复尺寸">
          <el-input-number v-model="form.inpaintingSize" :min="256" :max="4096" :step="256" />
        </el-form-item>
        <el-form-item label="修复精度">
          <el-select v-model="form.inpaintingPrecision" style="width: 100%">
            <el-option v-for="o in inpaintingPrecisionOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="渲染器">
          <el-select v-model="form.renderer" style="width: 100%">
            <el-option v-for="o in rendererOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对齐方式">
          <el-select v-model="form.alignment" style="width: 100%">
            <el-option v-for="o in alignmentOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="文字方向">
          <el-select v-model="form.direction" style="width: 100%">
            <el-option v-for="o in directionOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">高级参数</el-divider>
        <el-form-item label="字号偏移">
          <el-input-number v-model="form.fontSizeOffset" :min="-20" :max="20" />
        </el-form-item>
        <el-form-item label="掩膜扩展">
          <el-input-number v-model="form.maskDilationOffset" :min="-10" :max="30" />
        </el-form-item>
        <el-form-item label="卷积核大小">
          <el-input-number v-model="form.kernelSize" :min="1" :max="7" :step="2" />
        </el-form-item>
        <el-form-item label="上色器">
          <el-select v-model="form.colorizer" style="width: 100%">
            <el-option v-for="o in colorizerOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
}

.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}

.section-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 16px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.config-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.config-card :deep(.el-card__body) {
  padding: 16px;
}

.config-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-name {
  font-weight: 600;
  font-size: 15px;
}

.config-card-actions {
  display: flex;
  gap: 2px;
}

.preset-card {
  cursor: pointer;
  transition: border-color 0.2s;
}

.preset-card:hover {
  border-color: var(--color-primary);
}

.translator-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
