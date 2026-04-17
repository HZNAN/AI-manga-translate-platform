<script setup lang="ts">
import { ref, watch, onUnmounted, nextTick } from 'vue'
import { useImageEditor } from '@/composables/useImageEditor'
import ToolBar from './ToolBar.vue'
import TextEditPanel from './TextEditPanel.vue'
import type { TextRegion } from '@/types/imageEdit'
import { Close } from '@element-plus/icons-vue'

const props = defineProps<{
  visible: boolean
  imageUrl: string
  pageId: number
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
  closed: []
}>()

const canvasEl = ref<HTMLCanvasElement | null>(null)
const canvasContainer = ref<HTMLElement | null>(null)

const {
  activeTool,
  regions,
  selectedRegion,
  loading,
  canUndo,
  canRedo,
  eraserSize,
  eraserColor,

  initCanvas,
  dispose,
  setTool,

  deleteSelectedRegion,
  inpaintDrawnRegions,
  inpaintAllRegions,
  restoreDrawnRegions,
  flattenSelectedRegion,

  updateRegionProperty,
  setEraserSize,
  setEraserColor,

  saveImage,
  undo,
  redo,
} = useImageEditor(canvasEl)

watch(
  () => props.visible,
  async (val) => {
    if (val && props.imageUrl && props.pageId) {
      await nextTick()
      await nextTick()
      if (canvasContainer.value) {
        await initCanvas(props.imageUrl, props.pageId, canvasContainer.value)
      }
    } else if (!val) {
      dispose()
    }
  },
)

function handleClose() {
  emit('update:visible', false)
  emit('closed')
}

async function handleSave() {
  await saveImage()
  emit('saved')
}

function handleQuickSave() {
  if (loading.value) return
  handleSave()
}

function handlePropertyUpdate(regionId: string, key: keyof TextRegion, value: unknown) {
  updateRegionProperty(regionId, key, value as never)
}

function handleKeydown(e: KeyboardEvent) {
  if (!props.visible) return

  if (e.key === 'Escape') {
    handleClose()
    return
  }

  if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
    e.preventDefault()
    undo()
    return
  }
  if ((e.ctrlKey || e.metaKey) && e.key === 'y') {
    e.preventDefault()
    redo()
    return
  }
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    handleQuickSave()
    return
  }

  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return

  switch (e.key) {
    case 'v':
      setTool('pointer')
      break
    case 'r':
      setTool('rect')
      break
    case 'e':
      setTool('ellipse')
      break
    case 'b':
      setTool('eraser')
      break
    case 's':
      e.preventDefault()
      handleQuickSave()
      break
    case 'Delete':
    case 'Backspace':
      deleteSelectedRegion()
      break
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      window.addEventListener('keydown', handleKeydown)
    } else {
      window.removeEventListener('keydown', handleKeydown)
    }
  },
)

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  dispose()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="editor-fade">
      <div v-if="visible" class="image-editor-overlay">
        <!-- 顶部关闭栏 -->
        <div class="editor-top-bar">
          <span class="editor-top-title">图片编辑器</span>
          <el-button text circle @click="handleClose">
            <el-icon :size="20"><Close /></el-icon>
          </el-button>
        </div>

        <!-- 工具栏 -->
        <ToolBar
          :active-tool="activeTool"
          :can-undo="canUndo"
          :can-redo="canRedo"
          :loading="loading"
          :has-regions="regions.length > 0"
          :has-selection="!!selectedRegion"
          :eraser-size="eraserSize"
          :eraser-color="eraserColor"
          @update:active-tool="setTool"
          @update:eraser-size="setEraserSize"
          @update:eraser-color="setEraserColor"
          @undo="undo"
          @redo="redo"
          @delete="deleteSelectedRegion"
          @inpaint="inpaintDrawnRegions"
          @inpaint-all="inpaintAllRegions"
          @restore="restoreDrawnRegions"
          @save="handleSave"
        />

        <!-- 主体区域 -->
        <div class="editor-body">
          <!-- Canvas 区域 -->
          <div ref="canvasContainer" class="canvas-container">
            <canvas ref="canvasEl" />
          </div>

          <!-- 右侧文本编辑面板 -->
          <TextEditPanel
            :region="selectedRegion"
            :loading="loading"
            @delete="deleteSelectedRegion"
            @flatten="flattenSelectedRegion"
            @update:property="handlePropertyUpdate"
          />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.image-editor-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.editor-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.editor-top-title {
  font-weight: 600;
  font-size: 15px;
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  background:
    repeating-conic-gradient(#e0e0e0 0% 25%, transparent 0% 50%)
    50% / 20px 20px;
  padding: 20px;
}

.canvas-container canvas {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}

/* 过渡动画 */
.editor-fade-enter-active,
.editor-fade-leave-active {
  transition: opacity 0.25s ease;
}

.editor-fade-enter-from,
.editor-fade-leave-to {
  opacity: 0;
}
</style>
