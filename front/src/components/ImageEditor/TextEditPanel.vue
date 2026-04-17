<script setup lang="ts">
import { computed } from 'vue'
import type { TextRegion } from '@/types/imageEdit'
import { Delete, Download } from '@element-plus/icons-vue'

const props = defineProps<{
  region: TextRegion | null
  loading: boolean
}>()

const emit = defineEmits<{
  delete: [regionId: string]
  flatten: [regionId: string]
  'update:property': [regionId: string, key: keyof TextRegion, value: unknown]
}>()

const fontFamilyOptions = [
  { value: 'Noto Sans SC, sans-serif', label: 'Noto Sans SC' },
  { value: 'Microsoft YaHei, sans-serif', label: '微软雅黑' },
  { value: 'SimSun, serif', label: '宋体' },
  { value: 'SimHei, sans-serif', label: '黑体' },
  { value: 'KaiTi, serif', label: '楷体' },
  { value: 'FangSong, serif', label: '仿宋' },
  { value: 'Arial, sans-serif', label: 'Arial' },
  { value: 'Comic Sans MS, cursive', label: 'Comic Sans' },
  { value: 'Impact, sans-serif', label: 'Impact' },
]

const hasRegion = computed(() => !!props.region)

function updateProp(key: keyof TextRegion, value: unknown) {
  if (props.region) {
    emit('update:property', props.region.id, key, value)
  }
}

function handleDelete() {
  if (props.region) {
    emit('delete', props.region.id)
  }
}

function handleFlatten() {
  if (props.region) {
    emit('flatten', props.region.id)
  }
}
</script>

<template>
  <div class="text-edit-panel">
    <template v-if="hasRegion && region">
      <div class="panel-header">
        <span class="panel-title">文本编辑</span>
        <div class="panel-actions">
          <el-tooltip content="将文本保存到图片并移除此区域" placement="top">
            <el-button text type="primary" size="small" :icon="Download" :loading="loading" @click="handleFlatten">
              保存
            </el-button>
          </el-tooltip>
          <el-button text type="danger" size="small" :icon="Delete" @click="handleDelete">
            删除
          </el-button>
        </div>
      </div>

      <!-- 区域坐标信息 -->
      <div class="region-info">
        区域 {{ region.box.type === 'rect' ? '矩形' : '椭圆' }}
        ({{ region.box.x }}, {{ region.box.y }})
        {{ region.box.width }}×{{ region.box.height }}
      </div>

      <!-- 译文 -->
      <div class="field-group">
        <label class="field-label">翻译文本</label>
        <el-input
          :model-value="region.translatedText"
          type="textarea"
          :rows="3"
          placeholder="输入翻译内容，实时渲染到图片"
          @update:model-value="updateProp('translatedText', $event)"
        />
      </div>

      <!-- 字体设置 -->
      <div class="field-group">
        <label class="field-label">字体</label>
        <el-select
          :model-value="region.fontFamily"
          style="width: 100%"
          @update:model-value="updateProp('fontFamily', $event)"
        >
          <el-option
            v-for="o in fontFamilyOptions"
            :key="o.value"
            :label="o.label"
            :value="o.value"
          >
            <span :style="{ fontFamily: o.value }">{{ o.label }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="field-row">
        <div class="field-group" style="flex: 1">
          <label class="field-label">字号</label>
          <el-input-number
            :model-value="region.fontSize"
            :min="8"
            :max="120"
            size="small"
            @update:model-value="updateProp('fontSize', $event)"
          />
        </div>
        <div class="field-group" style="flex: 1">
          <label class="field-label">行高</label>
          <el-input-number
            :model-value="region.lineHeight"
            :min="0.5"
            :max="3"
            :step="0.1"
            :precision="1"
            size="small"
            @update:model-value="updateProp('lineHeight', $event)"
          />
        </div>
      </div>

      <div class="field-row">
        <div class="field-group" style="flex: 1">
          <label class="field-label">颜色</label>
          <el-color-picker
            :model-value="region.fontColor"
            size="small"
            @update:model-value="updateProp('fontColor', $event || '#000000')"
          />
        </div>
        <div class="field-group" style="flex: 1">
          <label class="field-label">方向</label>
          <el-select
            :model-value="region.textDirection"
            size="small"
            @update:model-value="updateProp('textDirection', $event)"
          >
            <el-option value="horizontal" label="横排" />
            <el-option value="vertical" label="竖排" />
          </el-select>
        </div>
      </div>

      <div class="field-row">
        <div class="field-group" style="flex: 1">
          <label class="field-label">粗细</label>
          <el-select
            :model-value="region.fontWeight"
            size="small"
            @update:model-value="updateProp('fontWeight', $event)"
          >
            <el-option value="normal" label="常规" />
            <el-option value="bold" label="粗体" />
          </el-select>
        </div>
        <div class="field-group" style="flex: 1">
          <label class="field-label">样式</label>
          <el-select
            :model-value="region.fontStyle"
            size="small"
            @update:model-value="updateProp('fontStyle', $event)"
          >
            <el-option value="normal" label="常规" />
            <el-option value="italic" label="斜体" />
          </el-select>
        </div>
      </div>

    </template>

    <template v-else>
      <div class="empty-hint">
        <el-icon :size="36" color="#c0c4cc">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 000-1.41l-2.34-2.34a.996.996 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" />
          </svg>
        </el-icon>
        <p>使用矩形或椭圆工具<br />框选图片中的文本区域</p>
        <p class="sub-hint">框选后可进行抠字、<br />输入翻译文本并渲染回图片</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.text-edit-panel {
  width: 280px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  padding: 16px;
  overflow-y: auto;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.panel-title {
  font-weight: 600;
  font-size: 15px;
}

.region-info {
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 6px 10px;
  border-radius: 4px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 12px;
  color: #606266;
  font-weight: 500;
}

.field-row {
  display: flex;
  gap: 12px;
}

.empty-hint {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #909399;
  gap: 8px;
}

.empty-hint p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
}

.sub-hint {
  font-size: 12px !important;
  color: #c0c4cc;
}
</style>
