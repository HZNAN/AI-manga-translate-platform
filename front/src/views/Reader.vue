<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useReader } from '@/composables/useReader'
import { useKeyboard } from '@/composables/useKeyboard'
import TranslateConfigPanel from '@/components/TranslateConfigPanel.vue'
import ImageEditor from '@/components/ImageEditor/ImageEditor.vue'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowRight, Back,
  View, Hide, Setting, EditPen,
  FullScreen, ScaleToOriginal,
  DCaret,
} from '@element-plus/icons-vue'
import IconTranslate from '@/components/icons/IconTranslate.vue'

const {
  manga, chapters, pages, currentChapter, loading, translating, translateStatus, store,
  currentPageData, currentImageUrl, canShowTranslated,
  hasNextChapter, hasPrevChapter,
  loadData, goNext, goPrev, goNextChapter, goPrevChapter,
  toggleLayer, doTranslate, goBack, showConfig,
  currentChapterId,
} = useReader()

const showToolbar = ref(true)
const showEditor = ref(false)

const editorImageUrl = computed(() => currentImageUrl.value)

const editorPageId = computed(() => currentPageData.value?.id ?? 0)

function openEditor() {
  showEditor.value = true
}

function handleConfigApplied(updatedManga: typeof manga.value) {
  if (updatedManga) {
    manga.value = updatedManga
  }
  ElMessage.success('翻译配置已应用')
}

async function refreshPages() {
  if (currentChapterId.value && manga.value) {
    const { getChapterPages } = await import('@/api/chapter')
    const res = await getChapterPages(manga.value.id, currentChapterId.value)
    pages.value = res.data
  }
}

async function handleEditorSaved() {
  await refreshPages()
}

async function handleEditorClosed() {
  await refreshPages()
}
const hideTimer = ref<ReturnType<typeof setTimeout>>()
const scrollContainer = ref<HTMLElement>()

const isProgramScroll = ref(false)
let scrollRafId = 0

function resetHideTimer() {
  showToolbar.value = true
  clearTimeout(hideTimer.value)
  hideTimer.value = setTimeout(() => {
    showToolbar.value = false
  }, 3000)
}

function handleMouseMove() {
  resetHideTimer()
}

function handlePageAreaClick(e: MouseEvent) {
  if (store.readingMode !== 'page') return
  const target = e.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const x = e.clientX - rect.left
  const third = rect.width / 3

  if (x < third) {
    goPrev()
  } else if (x > third * 2) {
    goNext()
  } else {
    showToolbar.value = !showToolbar.value
  }
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    store.isFullscreen = true
  } else {
    document.exitFullscreen()
    store.isFullscreen = false
  }
}

function handleFitMode() {
  const modes: Array<typeof store.fitMode> = ['width', 'height', 'original', 'auto']
  const idx = modes.indexOf(store.fitMode)
  store.fitMode = modes[(idx + 1) % modes.length] ?? 'auto'
}

const fitModeLabel: Record<string, string> = {
  width: '适应宽度',
  height: '适应高度',
  original: '原始大小',
  auto: '自适应',
}

function getPageImageUrl(p: { id: number; isTranslated?: boolean; translatedImagePath?: string }) {
  if (store.imageLayer === 'translated' && p.isTranslated && p.translatedImagePath) {
    return `/api/mangas/page-by-id/${p.id}/translated-image`
  }
  return `/api/mangas/page-by-id/${p.id}/image`
}

// --- 滚动模式：scroll 事件检测当前可见页 ---
function detectCurrentPage() {
  if (isProgramScroll.value) return
  const container = scrollContainer.value
  if (!container) return

  const containerRect = container.getBoundingClientRect()
  const centerY = containerRect.top + containerRect.height / 2
  const images = container.querySelectorAll<HTMLElement>('.scroll-page-wrap')

  let closestPage = 1
  let closestDist = Infinity

  for (const img of images) {
    const rect = img.getBoundingClientRect()
    if (rect.height === 0) continue
    const imgCenter = rect.top + rect.height / 2
    const dist = Math.abs(imgCenter - centerY)
    if (dist < closestDist) {
      closestDist = dist
      closestPage = Number(img.dataset.page) || 1
    }
  }

  if (closestPage !== store.currentPage) {
    store.setPage(closestPage)
  }
}

function handleContainerScroll() {
  cancelAnimationFrame(scrollRafId)
  scrollRafId = requestAnimationFrame(detectCurrentPage)
}

function bindScrollListener() {
  scrollContainer.value?.addEventListener('scroll', handleContainerScroll, { passive: true })
}

function unbindScrollListener() {
  scrollContainer.value?.removeEventListener('scroll', handleContainerScroll)
  cancelAnimationFrame(scrollRafId)
}

// 滚动模式：跳转到指定页
function scrollToPage(pageNum: number, smooth = true) {
  const container = scrollContainer.value
  if (!container) return
  const target = container.querySelector<HTMLElement>(`.scroll-page-wrap[data-page="${pageNum}"]`)
  if (!target) return

  isProgramScroll.value = true
  const behavior: ScrollBehavior = smooth ? 'smooth' : 'instant'
  target.scrollIntoView({ behavior, block: 'start' })

  if (!smooth) {
    const retryScroll = () => {
      const el = container.querySelector<HTMLElement>(`.scroll-page-wrap[data-page="${pageNum}"]`)
      if (el) el.scrollIntoView({ behavior: 'instant', block: 'start' })
    }
    setTimeout(retryScroll, 50)
    setTimeout(retryScroll, 200)
    setTimeout(retryScroll, 500)
    setTimeout(() => { isProgramScroll.value = false }, 600)
  } else {
    setTimeout(() => { isProgramScroll.value = false }, 600)
  }
}

// slider 值变化统一处理
function handleSliderChange(val: number) {
  console.log('handleSliderChange', val)
  store.setPage(val)
  if (store.readingMode === 'scroll') {
    scrollToPage(val)
  }
}

// 切换到滚动模式时：先渲染 DOM → 等图片撑起高度 → 再跳转
watch(
  () => store.readingMode,
  async (mode) => {
    if (mode === 'scroll') {
      await nextTick()
      bindScrollListener()
      scrollToPage(store.currentPage, false)
    } else {
      unbindScrollListener()
    }
  },
)

// 页面加载完成后，如果是滚动模式就启动监听
watch(loading, async (val) => {
  if (!val && store.readingMode === 'scroll') {
    await nextTick()
    bindScrollListener()
    scrollToPage(store.currentPage, false)
  }
})

useKeyboard({
  ArrowLeft: () => goPrev(),
  ArrowRight: () => goNext(),
  ' ': () => goNext(),
  Escape: () => {
    if (showEditor.value || showConfig.value) return
    goBack()
  },
  t: () => toggleLayer(),
  f: () => toggleFullscreen(),
})

function handleFullscreenChange() {
  store.isFullscreen = !!document.fullscreenElement
}

onMounted(() => {
  loadData()
  resetHideTimer()
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  clearTimeout(hideTimer.value)
  unbindScrollListener()
  store.reset()
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
})
</script>

<template>
  <div
    class="reader"
    @mousemove="handleMouseMove"
  >
    <div v-if="loading" class="reader-loading">
      <el-icon class="is-loading" :size="40" color="#fff"><DCaret /></el-icon>
      <p style="color: #aaa; margin-top: 12px">加载中...</p>
    </div>

    <template v-else>
      <!-- 顶部工具栏 -->
      <transition name="toolbar-fade">
        <div v-show="showToolbar" class="toolbar toolbar-top">
          <div class="toolbar-left">
            <el-button text circle @click="goBack">
              <el-icon :size="20" color="#fff"><Back /></el-icon>
            </el-button>
            <span class="toolbar-title">{{ manga?.title }}</span>
          </div>
          <div class="toolbar-center">
            <div class="chapter-nav">
              <el-button
                v-if="chapters.length > 1"
                text circle size="small"
                :disabled="!hasPrevChapter"
                @click="goPrevChapter"
              >
                <el-icon :size="14" color="#fff"><ArrowLeft /></el-icon>
              </el-button>
              <span v-if="currentChapter" class="chapter-title-label">{{ currentChapter.title }}</span>
              <el-button
                v-if="chapters.length > 1"
                text circle size="small"
                :disabled="!hasNextChapter"
                @click="goNextChapter"
              >
                <el-icon :size="14" color="#fff"><ArrowRight /></el-icon>
              </el-button>
            </div>
            <span class="page-info">{{ store.currentPage }} / {{ store.totalPages }}</span>
          </div>
          <div class="toolbar-right">
            <el-button text size="small" @click="handleFitMode">
              <span style="color: #ccc; font-size: 12px">{{ fitModeLabel[store.fitMode] }}</span>
            </el-button>
            <el-tooltip content="切换翻页/滚动模式">
              <el-button text circle @click="store.readingMode = store.readingMode === 'page' ? 'scroll' : 'page'">
                <el-icon :size="18" color="#fff"><ScaleToOriginal /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="全屏">
              <el-button text circle @click="toggleFullscreen">
                <el-icon :size="18" color="#fff"><FullScreen /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </transition>

      <!-- 主阅读区 - 翻页模式 -->
      <div
        v-if="store.readingMode === 'page'"
        class="reader-page-area"
        @click="handlePageAreaClick"
      >
        <div class="page-image-wrap" :class="`fit-${store.fitMode}`">
          <img
            v-if="currentImageUrl"
            :src="currentImageUrl"
            :key="currentImageUrl"
            class="page-image"
            draggable="false"
          />
        </div>

        <div class="page-nav page-nav-left" @click.stop="goPrev">
          <el-icon :size="36" color="rgba(255,255,255,0.4)"><ArrowLeft /></el-icon>
        </div>
        <div class="page-nav page-nav-right" @click.stop="goNext">
          <el-icon :size="36" color="rgba(255,255,255,0.4)"><ArrowRight /></el-icon>
        </div>
      </div>

      <!-- 主阅读区 - 滚动模式 -->
      <div
        v-else
        ref="scrollContainer"
        class="reader-scroll-area"
      >
        <div class="scroll-content" :class="`fit-${store.fitMode}`">
          <div
            v-for="p in pages"
            :key="p.id"
            :data-page="p.pageNumber"
            class="scroll-page-wrap"
          >
            <img
              :src="getPageImageUrl(p)"
              class="scroll-page-image"
              draggable="false"
              @load="($event.target as HTMLElement).parentElement!.classList.add('loaded')"
            />
          </div>
        </div>
      </div>

      <!-- 底部工具栏 -->
      <transition name="toolbar-fade">
        <div v-show="showToolbar" class="toolbar toolbar-bottom">
          <div class="bottom-bar">
            <el-slider
              :model-value="store.currentPage"
              :min="1"
              :max="store.totalPages || 1"
              :step="1"
              :show-tooltip="true"
              :format-tooltip="(val: number) => `第 ${val} 页`"
              class="page-slider"
              @input="handleSliderChange"
            />
          </div>
          <div class="bottom-actions">
            <el-button
              :loading="translating"
              type="primary"
              size="small"
              :icon="IconTranslate"
              @click.stop="doTranslate()"
            >
              {{ translating
                ? (translateStatus === 'queued' ? '排队中...' : translateStatus === 'translating' ? '翻译中...' : '翻译中...')
                : '翻译当前页' }}
            </el-button>

            <el-button-group size="small">
              <el-button
                :type="store.imageLayer === 'original' ? 'primary' : 'default'"
                @click.stop="store.imageLayer = 'original'"
              >
                <el-icon><View /></el-icon> 原图
              </el-button>
              <el-button
                :type="store.imageLayer === 'translated' ? 'success' : 'default'"
                :disabled="!canShowTranslated"
                @click.stop="store.imageLayer = 'translated'"
              >
                <el-icon><Hide /></el-icon> 译图
              </el-button>
            </el-button-group>

            <el-tooltip content="编辑图片">
              <el-button size="small" circle :icon="EditPen" @click.stop="openEditor" />
            </el-tooltip>

            <el-tooltip content="翻译配置">
              <el-button size="small" circle :icon="Setting" @click.stop="showConfig = true" />
            </el-tooltip>
          </div>
        </div>
      </transition>
    </template>

    <TranslateConfigPanel
      v-model:visible="showConfig"
      :manga-id="manga?.id ?? 0"
      :active-config-id="manga?.activeConfigId"
      @applied="handleConfigApplied"
    />
    <ImageEditor
      v-model:visible="showEditor"
      :image-url="editorImageUrl"
      :page-id="editorPageId"
      @saved="handleEditorSaved"
      @closed="handleEditorClosed"
    />
  </div>
</template>

<style scoped>
.reader {
  position: fixed;
  inset: 0;
  background: #1a1a1a;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  user-select: none;
}

.reader-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

/* 工具栏 */
.toolbar {
  position: absolute;
  left: 0;
  right: 0;
  z-index: 10;
  padding: 8px 16px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.7), transparent);
  display: flex;
  align-items: center;
}

.toolbar-top {
  top: 0;
  justify-content: space-between;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.75), transparent);
  padding: 10px 16px 24px;
}

.toolbar-bottom {
  bottom: 0;
  flex-direction: column;
  gap: 8px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.75), transparent);
  padding: 24px 16px 12px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.toolbar-title {
  color: #eee;
  font-size: 14px;
  font-weight: 500;
  max-width: 300px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chapter-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
}

.chapter-title-label {
  color: #ccc;
  font-size: 12px;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.page-info {
  color: #ddd;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.bottom-bar {
  width: 100%;
  padding: 0 8px;
}

.page-slider {
  --el-slider-main-bg-color: #409eff;
}

.bottom-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

/* 翻页模式 */
.reader-page-area {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.page-image-wrap {
  max-width: 100%;
  max-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-image-wrap.fit-width {
  width: 100%;
}

.page-image-wrap.fit-height {
  height: 100%;
}

.page-image {
  max-width: 100%;
  max-height: 100vh;
  object-fit: contain;
}

.fit-width .page-image {
  width: 100%;
  max-width: 100%;
  max-height: none;
}

.fit-height .page-image {
  height: 100vh;
  max-width: none;
}

.fit-original .page-image {
  max-width: none;
  max-height: none;
}

.page-nav {
  position: absolute;
  top: 60px;
  bottom: 80px;
  width: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}

.page-nav:hover {
  opacity: 1;
}

.page-nav-left {
  left: 0;
}

.page-nav-right {
  right: 0;
}

/* 滚动模式 */
.reader-scroll-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.scroll-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 0 100px;
}

.scroll-page-wrap {
  width: 100%;
  min-height: 60vh;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.scroll-page-wrap.loaded {
  min-height: 0;
}

.scroll-page-image {
  max-width: 100%;
  display: block;
}

.scroll-content.fit-width .scroll-page-wrap.loaded {
  justify-content: center;
}

.scroll-content.fit-width .scroll-page-image {
  width: 100%;
}

.scroll-content.fit-height .scroll-page-image {
  max-height: 100vh;
  width: auto;
}

/* 过渡 */
.toolbar-fade-enter-active,
.toolbar-fade-leave-active {
  transition: opacity 0.3s;
}

.toolbar-fade-enter-from,
.toolbar-fade-leave-to {
  opacity: 0;
}
</style>
