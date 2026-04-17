<script setup lang="ts">
import type { EditorTool } from '@/types/imageEdit'
import {
  Pointer, Delete, Refresh, RefreshRight,
  Download, MagicStick, Position, RefreshLeft,
} from '@element-plus/icons-vue'

const props = defineProps<{
  activeTool: EditorTool
  canUndo: boolean
  canRedo: boolean
  loading: boolean
  hasRegions: boolean
  hasSelection: boolean
  eraserSize: number
  eraserColor: string
}>()

const emit = defineEmits<{
  'update:activeTool': [tool: EditorTool]
  'update:eraserSize': [size: number]
  'update:eraserColor': [color: string]
  undo: []
  redo: []
  delete: []
  inpaint: []
  inpaintAll: []
  restore: []
  save: []
}>()

function setTool(tool: EditorTool) {
  emit('update:activeTool', tool)
}
</script>

<template>
  <div class="editor-toolbar">
    <!-- 绘图工具 -->
    <div class="tool-group">
      <el-tooltip content="选择 (V)" placement="bottom">
        <el-button
          :type="activeTool === 'pointer' ? 'primary' : 'default'"
          circle
          size="small"
          :icon="Pointer"
          @click="setTool('pointer')"
        />
      </el-tooltip>
      <el-tooltip content="矩形框选 (R)" placement="bottom">
        <el-button
          :type="activeTool === 'rect' ? 'primary' : 'default'"
          circle
          size="small"
          @click="setTool('rect')"
        >
          <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
            <rect x="1" y="3" width="14" height="10" rx="1" fill="none" stroke="currentColor" stroke-width="1.5" />
          </svg>
        </el-button>
      </el-tooltip>
      <el-tooltip content="椭圆框选 (E)" placement="bottom">
        <el-button
          :type="activeTool === 'ellipse' ? 'primary' : 'default'"
          circle
          size="small"
          @click="setTool('ellipse')"
        >
          <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
            <ellipse cx="8" cy="8" rx="7" ry="5" fill="none" stroke="currentColor" stroke-width="1.5" />
          </svg>
        </el-button>
      </el-tooltip>
      <el-tooltip content="橡皮擦 (B)" placement="bottom">
        <el-button
          :type="activeTool === 'eraser' ? 'primary' : 'default'"
          circle
          size="small"
          @click="setTool('eraser')"
        >
          <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
            <path d="M14.4 4.6L11.4 1.6a1.5 1.5 0 00-2.1 0L2.3 8.6a1.5 1.5 0 000 2.1l2.1 2.1a1.5 1.5 0 001.05.44H14v-1.5H8.56l5.84-5.84a1.5 1.5 0 000-2.1zM5.1 11.7L3.4 10l4-4 1.8 1.8z" />
          </svg>
        </el-button>
      </el-tooltip>
    </div>

    <!-- 橡皮擦参数（仅在橡皮擦激活时显示） -->
    <template v-if="activeTool === 'eraser'">
      <el-divider direction="vertical" />
      <div class="tool-group eraser-controls">
        <span class="eraser-label">大小</span>
        <el-slider
          :model-value="eraserSize"
          :min="2"
          :max="100"
          :step="1"
          :show-tooltip="true"
          style="width: 100px"
          @update:model-value="(v: number) => emit('update:eraserSize', v)"
        />
        <span class="eraser-label">颜色</span>
        <el-color-picker
          :model-value="eraserColor"
          size="small"
          :predefine="['#FFFFFF', '#000000', '#F5F5F5', '#EEEEEE']"
          @update:model-value="(v: string | null) => v && emit('update:eraserColor', v)"
        />
      </div>
    </template>

    <el-divider direction="vertical" />

    <!-- 区域操作 -->
    <div class="tool-group">
      <el-tooltip content="清除框选区域内文字" placement="bottom">
        <el-button
          size="small"
          :icon="MagicStick"
          :loading="loading"
          :disabled="!hasRegions"
          @click="emit('inpaint')"
        >
          抠字
        </el-button>
      </el-tooltip>
      <el-tooltip content="清除整张图片所有文字" placement="bottom">
        <el-button
          size="small"
          :loading="loading"
          @click="emit('inpaintAll')"
        >
          全部抠字
        </el-button>
      </el-tooltip>
      <el-tooltip content="用原图复原框选区域" placement="bottom">
        <el-button
          size="small"
          :icon="RefreshLeft"
          :loading="loading"
          :disabled="!hasRegions"
          @click="emit('restore')"
        >
          复原
        </el-button>
      </el-tooltip>
      <el-tooltip content="删除选中区域" placement="bottom">
        <el-button
          size="small"
          type="danger"
          :icon="Delete"
          :disabled="!hasSelection"
          @click="emit('delete')"
        />
      </el-tooltip>
    </div>

    <el-divider direction="vertical" />

    <!-- 撤销/重做/保存 -->
    <div class="tool-group">
      <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
        <el-button
          size="small"
          circle
          :icon="Refresh"
          :disabled="!canUndo"
          @click="emit('undo')"
        />
      </el-tooltip>
      <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
        <el-button
          size="small"
          circle
          :icon="RefreshRight"
          :disabled="!canRedo"
          @click="emit('redo')"
        />
      </el-tooltip>
      <el-tooltip content="一键保存 (S)" placement="bottom">
        <el-button
          size="small"
          type="primary"
          :icon="Download"
          :loading="loading"
          @click="emit('save')"
        >
          一键保存
        </el-button>
      </el-tooltip>
    </div>

  </div>
</template>

<style scoped>
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.tool-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.el-divider--vertical {
  height: 24px;
  margin: 0 4px;
}

.eraser-controls {
  gap: 8px !important;
}

.eraser-label {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}
</style>
