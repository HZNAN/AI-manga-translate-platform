<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { getConfigs, getPresets, updateConfig } from '@/api/config'
import { setActiveConfig } from '@/api/manga'
import { getLlmConfigs, type LlmConfig } from '@/api/llmConfig'
import type { TranslateConfig, Manga } from '@/types/manga'
import { ElMessage } from 'element-plus'
import { Edit, Right } from '@element-plus/icons-vue'

const props = defineProps<{
  visible: boolean
  mangaId: number
  activeConfigId?: number
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  applied: [manga: Manga]
}>()

const drawerVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const configs = ref<TranslateConfig[]>([])
const presets = ref<TranslateConfig[]>([])
const llmConfigs = ref<LlmConfig[]>([])
const activeTab = ref('mine')
const applyingId = ref<number | null>(null)

const editDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

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

const systemLlmConfigs = computed(() => llmConfigs.value.filter(c => c.isSystem))
const userLlmConfigs = computed(() => llmConfigs.value.filter(c => !c.isSystem))

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
const inpaintingPrecisionOptions = [{ value: 'bf16', label: 'BF16（默认）' }]
const colorizerOptions = [{ value: 'none', label: '无（默认）' }]

function isActive(configId: number) {
  return props.activeConfigId === configId
}

function getTranslatorLabel(config: TranslateConfig) {
  if (config.llmConfigId) {
    const llm = llmConfigs.value.find(c => c.id === config.llmConfigId)
    if (llm) return `${llm.name}${llm.multimodal ? ' (多模态)' : ''}`
    return `LLM #${config.llmConfigId}`
  }
  return config.translator || '-'
}

async function applyConfig(configId: number) {
  if (!props.mangaId || applyingId.value !== null) return
  applyingId.value = configId
  try {
    const res = await setActiveConfig(props.mangaId, configId)
    emit('applied', res.data)
  } catch {
    ElMessage.error('应用配置失败')
  } finally {
    applyingId.value = null
  }
}

function openEditDialog(config: TranslateConfig) {
  editingId.value = config.id
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
  editDialogVisible.value = true
}

function onLlmConfigChange(llmId: number | undefined) {
  form.llmConfigId = llmId
  if (llmId) {
    form.translator = 'none'
  }
}

async function handleSaveEdit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入配置名称')
    return
  }
  if (!editingId.value) return
  saving.value = true
  try {
    await updateConfig(editingId.value, { ...form })
    ElMessage.success('配置已更新')
    editDialogVisible.value = false
    await loadData()
  } catch {
    // handled by interceptor
  } finally {
    saving.value = false
  }
}

async function loadData() {
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
  }
}

watch(() => props.visible, (val) => {
  if (val) loadData()
})

onMounted(() => {
  if (props.visible) loadData()
})
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    title="翻译配置"
    size="420px"
    direction="rtl"
  >
    <el-tabs v-model="activeTab">
      <!-- 我的配置 -->
      <el-tab-pane label="我的配置" name="mine">
        <el-empty v-if="configs.length === 0" description="暂无自定义配置">
          <el-button type="primary" @click="$router.push({ name: 'ConfigManage' })">
            前往创建
          </el-button>
        </el-empty>
        <div v-else class="config-list">
          <div
            v-for="c in configs"
            :key="c.id"
            class="config-card"
            :class="{ active: isActive(c.id) }"
          >
            <div class="config-main">
              <div class="config-header">
                <span class="config-name">{{ c.name }}</span>
                <el-tag v-if="isActive(c.id)" type="success" size="small" effect="dark" class="active-tag">
                  使用中
                </el-tag>
              </div>
              <div class="config-meta">
                <span>{{ c.targetLang }}</span>
                <span class="meta-sep">·</span>
                <span>{{ getTranslatorLabel(c) }}</span>
              </div>
              <div class="config-detail">
                <span>检测: {{ c.detector }}</span>
                <span class="meta-sep">·</span>
                <span>修复: {{ c.inpainter }}</span>
                <span v-if="c.fontSizeOffset" class="meta-sep">·</span>
                <span v-if="c.fontSizeOffset">字号偏移: {{ c.fontSizeOffset }}</span>
              </div>
            </div>
            <div class="config-actions">
              <el-button
                size="small"
                :icon="Edit"
                @click="openEditDialog(c)"
              >编辑</el-button>
              <el-button
                v-if="!isActive(c.id)"
                type="primary"
                size="small"
                :icon="Right"
                :loading="applyingId === c.id"
                :disabled="applyingId !== null"
                @click="applyConfig(c.id)"
              >应用</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 系统预设 -->
      <el-tab-pane label="系统预设" name="system">
        <el-empty v-if="presets.length === 0" description="暂无系统预设" />
        <div v-else class="config-list">
          <div
            v-for="p in presets"
            :key="p.id"
            class="config-card"
            :class="{ active: isActive(p.id) }"
          >
            <div class="config-main">
              <div class="config-header">
                <span class="config-name">{{ p.name }}</span>
                <el-tag v-if="isActive(p.id)" type="success" size="small" effect="dark" class="active-tag">
                  使用中
                </el-tag>
              </div>
              <div class="config-meta">
                <span>{{ p.targetLang }}</span>
                <span class="meta-sep">·</span>
                <span>{{ getTranslatorLabel(p) }}</span>
              </div>
              <div class="config-detail">
                <span>检测: {{ p.detector }}</span>
                <span class="meta-sep">·</span>
                <span>修复: {{ p.inpainter }}</span>
              </div>
            </div>
            <div class="config-actions">
              <el-button
                v-if="!isActive(p.id)"
                type="primary"
                size="small"
                :icon="Right"
                :loading="applyingId === p.id"
                :disabled="applyingId !== null"
                @click="applyConfig(p.id)"
              >应用</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>

  <!-- 编辑配置对话框 -->
  <el-dialog
    v-model="editDialogVisible"
    title="编辑配置"
    width="640px"
    top="5vh"
    append-to-body
    destroy-on-close
  >
    <el-form :model="form" label-width="100px" size="default">
      <el-form-item label="配置名称" required>
        <el-input v-model="form.name" placeholder="请输入配置名称" />
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
        <div v-if="!form.llmConfigId" class="form-hint">
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
        <el-input-number v-model="form.maskDilationOffset" :min="-10" :max="100" />
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
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSaveEdit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.config-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.config-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1.5px solid #e4e7ed;
  border-radius: 8px;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.config-card:hover {
  border-color: #409eff;
  background: #f5f9ff;
}

.config-card.active {
  border-color: #67c23a;
  background: #f0f9eb;
  box-shadow: 0 0 0 1px #67c23a20;
}

.config-card.active:hover {
  border-color: #67c23a;
  background: #f0f9eb;
}

.config-main {
  flex: 1;
  min-width: 0;
}

.config-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.config-name {
  font-weight: 600;
  font-size: 14px;
}

.active-tag {
  flex-shrink: 0;
}

.config-meta {
  font-size: 12px;
  color: #606266;
  margin-bottom: 2px;
}

.config-detail {
  font-size: 11px;
  color: #909399;
}

.meta-sep {
  margin: 0 4px;
}

.config-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  margin-left: 12px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
