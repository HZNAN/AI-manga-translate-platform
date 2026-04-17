<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMangaDetail, getMangaPages } from '@/api/manga'
import { batchTranslate, getTranslateTasks, cancelTask } from '@/api/translate'
import { getConfigs, getPresets } from '@/api/config'
import type { Manga, MangaPage, TranslateConfig, TranslationTask } from '@/types/manga'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheckFilled, WarningFilled, Loading as IconLoading,
  VideoPlay, Close, Refresh, Check, Promotion, Link, Setting,
} from '@element-plus/icons-vue'
import IconTranslate from '@/components/icons/IconTranslate.vue'
import TranslateConfigPanel from '@/components/TranslateConfigPanel.vue'
import { useWebSocket, type WsMessage } from '@/utils/websocket'

const route = useRoute()
const router = useRouter()
const mangaId = computed(() => Number(route.params.id))

const manga = ref<Manga | null>(null)
const pages = ref<MangaPage[]>([])
const allConfigs = ref<TranslateConfig[]>([])
const allPresets = ref<TranslateConfig[]>([])
const tasks = ref<TranslationTask[]>([])
const loading = ref(true)
const submitting = ref(false)
const showConfigPanel = ref(false)

const forceRetranslate = ref(false)
const selectMode = ref<'all' | 'untranslated' | 'custom'>('untranslated')
const selectedPageIds = ref<number[]>([])

const activeConfigName = computed(() => {
  if (!manga.value?.activeConfigId) return null
  const id = manga.value.activeConfigId
  const found = allConfigs.value.find(c => c.id === id) ?? allPresets.value.find(c => c.id === id)
  return found?.name ?? `配置 #${id}`
})

const canTranslate = computed(() => !!manga.value?.activeConfigId)

let pollTimer: ReturnType<typeof setInterval> | null = null

function handleWsMessage(msg: WsMessage) {
  if (msg.type === 'TASK_PROGRESS') {
    const task = tasks.value.find(t => t.id === msg.taskId)
    if (task) {
      task.completedPages = msg.completedPages
      task.failedPages = msg.failedPages
      task.status = msg.status as TranslationTask['status']

      if (msg.status === 'completed') {
        getMangaPages(mangaId.value).then(res => {
          pages.value = res.data
        })
      }
    }
  }
}

const { connected: wsConnected } = useWebSocket(handleWsMessage)

const translatedCount = computed(() =>
  pages.value.filter(p => p.isTranslated).length,
)

const untranslatedPages = computed(() =>
  pages.value.filter(p => !p.isTranslated),
)

const pageIdsToTranslate = computed(() => {
  if (selectMode.value === 'all') return undefined
  if (selectMode.value === 'untranslated') return untranslatedPages.value.map(p => p.id)
  return selectedPageIds.value
})

const pageCountToTranslate = computed(() => {
  if (selectMode.value === 'all') return pages.value.length
  if (selectMode.value === 'untranslated') return untranslatedPages.value.length
  return selectedPageIds.value.length
})

const activeTasks = computed(() =>
  tasks.value.filter(t => t.status === 'pending' || t.status === 'processing'),
)

const completedTasks = computed(() =>
  tasks.value.filter(t => t.status === 'completed' || t.status === 'cancelled'),
)

async function loadData() {
  loading.value = true
  try {
    const [mangaRes, pagesRes, configRes, presetRes, taskRes] = await Promise.all([
      getMangaDetail(mangaId.value),
      getMangaPages(mangaId.value),
      getConfigs(),
      getPresets(),
      getTranslateTasks({ mangaId: mangaId.value }),
    ])
    manga.value = mangaRes.data
    pages.value = pagesRes.data
    allConfigs.value = configRes.data
    allPresets.value = presetRes.data
    tasks.value = taskRes.data
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function refreshTasks() {
  try {
    const taskRes = await getTranslateTasks({ mangaId: mangaId.value })
    tasks.value = taskRes.data
  } catch {
    // ignore
  }
}

function togglePageSelection(page: MangaPage) {
  const idx = selectedPageIds.value.indexOf(page.id)
  if (idx >= 0) {
    selectedPageIds.value.splice(idx, 1)
  } else {
    selectedPageIds.value.push(page.id)
  }
}

function isPageSelected(page: MangaPage) {
  if (selectMode.value === 'all') return true
  if (selectMode.value === 'untranslated') return !page.isTranslated
  return selectedPageIds.value.includes(page.id)
}

async function handleBatchTranslate() {
  if (!canTranslate.value) {
    ElMessage.warning('请先为漫画设置翻译配置')
    showConfigPanel.value = true
    return
  }
  if (pageCountToTranslate.value === 0) {
    ElMessage.warning('没有需要翻译的页面')
    return
  }

  await ElMessageBox.confirm(
    `即将翻译 ${pageCountToTranslate.value} 页，确认开始？`,
    '批量翻译',
    { type: 'info', confirmButtonText: '开始翻译', cancelButtonText: '取消' },
  )

  submitting.value = true
  try {
    await batchTranslate({
      mangaId: mangaId.value,
      pageIds: pageIdsToTranslate.value,
      forceRetranslate: forceRetranslate.value,
    })
    ElMessage.success('翻译任务已提交')
    await refreshTasks()
    startPolling()
  } catch {
    // handled by interceptor
  } finally {
    submitting.value = false
  }
}

function handleConfigApplied(updatedManga: typeof manga.value) {
  if (updatedManga) {
    manga.value = updatedManga
  }
  ElMessage.success('配置已应用')
}

async function handleCancelTask(task: TranslationTask) {
  await ElMessageBox.confirm('确定取消该翻译任务？', '取消任务', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
  try {
    await cancelTask(task.id)
    ElMessage.success('任务已取消')
    await refreshTasks()
  } catch {
    // handled by interceptor
  }
}

function getTaskProgress(task: TranslationTask) {
  if (task.totalPages === 0) return 0
  return Math.round(((task.completedPages + task.failedPages) / task.totalPages) * 100)
}

function getTaskStatusType(status: TranslationTask['status']) {
  const map: Record<string, string> = {
    pending: 'info',
    processing: '',
    completed: 'success',
    cancelled: 'warning',
  }
  return map[status] || 'info'
}

const statusLabel: Record<string, string> = {
  pending: '等待中',
  processing: '翻译中',
  completed: '已完成',
  cancelled: '已取消',
}

function getPageImageUrl(page: MangaPage) {
  return page.thumbnailPath
    ? `/api/mangas/${mangaId.value}/pages/${page.pageNumber}/thumbnail`
    : `/api/mangas/${mangaId.value}/pages/${page.pageNumber}/image`
}

function startPolling() {
  stopPolling()
  const interval = wsConnected.value ? 15000 : 3000
  pollTimer = setInterval(async () => {
    await refreshTasks()
    if (activeTasks.value.length === 0) {
      stopPolling()
      const pagesRes = await getMangaPages(mangaId.value)
      pages.value = pagesRes.data
    }
  }, interval)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(async () => {
  await loadData()
  if (activeTasks.value.length > 0) {
    startPolling()
  }
})

onUnmounted(stopPolling)
</script>

<template>
  <div v-loading="loading" class="translate-manage">
    <el-page-header @back="router.push(`/manga/${mangaId}`)">
      <template #content>
        <span class="page-header-title">翻译管理 — {{ manga?.title || '' }}</span>
      </template>
    </el-page-header>

    <template v-if="manga">
      <!-- 翻译概览 -->
      <div class="overview-row">
        <el-card shadow="never" class="overview-card">
          <el-statistic title="总页数" :value="pages.length" />
        </el-card>
        <el-card shadow="never" class="overview-card">
          <el-statistic title="已翻译" :value="translatedCount">
            <template #suffix>
              <span class="stat-suffix">/ {{ pages.length }}</span>
            </template>
          </el-statistic>
        </el-card>
        <el-card shadow="never" class="overview-card">
          <el-statistic title="未翻译" :value="untranslatedPages.length" />
        </el-card>
        <el-card shadow="never" class="overview-card">
          <el-statistic title="进行中任务" :value="activeTasks.length" />
        </el-card>
      </div>

      <!-- 批量翻译操作 -->
      <el-card class="action-card">
        <template #header>
          <div class="card-header-row">
            <span class="card-title">批量翻译</span>
          </div>
        </template>

        <el-form label-width="100px" size="default">
          <el-form-item label="翻译范围">
            <el-radio-group v-model="selectMode">
              <el-radio value="untranslated">
                仅未翻译 ({{ untranslatedPages.length }} 页)
              </el-radio>
              <el-radio value="all">全部页面 ({{ pages.length }} 页)</el-radio>
              <el-radio value="custom">自定义选择</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="当前配置">
            <div class="active-config-display">
              <template v-if="activeConfigName">
                <el-tag type="success" size="default" effect="dark">{{ activeConfigName }}</el-tag>
              </template>
              <template v-else>
                <el-tag type="danger" size="default">未设置</el-tag>
              </template>
              <el-button
                :icon="Setting"
                size="small"
                @click="showConfigPanel = true"
              >
                {{ activeConfigName ? '更换配置' : '设置配置' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="强制重翻">
            <el-switch v-model="forceRetranslate" />
            <span class="form-hint">开启后将重新翻译已翻译的页面</span>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :icon="IconTranslate"
              :loading="submitting"
              :disabled="pageCountToTranslate === 0 || !canTranslate"
              @click="handleBatchTranslate"
            >
              {{ canTranslate ? `开始翻译 (${pageCountToTranslate} 页)` : '请先设置翻译配置' }}
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 自定义选择页面 -->
        <template v-if="selectMode === 'custom'">
          <el-divider />
          <div class="custom-select-header">
            <span>点击选择要翻译的页面（已选 {{ selectedPageIds.length }} 页）</span>
            <div>
              <el-button size="small" @click="selectedPageIds = pages.map(p => p.id)">全选</el-button>
              <el-button size="small" @click="selectedPageIds = untranslatedPages.map(p => p.id)">选择未翻译</el-button>
              <el-button size="small" @click="selectedPageIds = []">清空</el-button>
            </div>
          </div>
          <div class="pages-grid">
            <div
              v-for="p in pages"
              :key="p.id"
              class="page-thumb"
              :class="{ selected: isPageSelected(p), translated: p.isTranslated }"
              @click="togglePageSelection(p)"
            >
              <div class="thumb-img">
                <img :src="getPageImageUrl(p)" :alt="`第${p.pageNumber}页`" loading="lazy" />
                <el-icon v-if="p.isTranslated" class="status-badge translated-badge" color="#67c23a" :size="18">
                  <CircleCheckFilled />
                </el-icon>
                <el-icon v-if="isPageSelected(p)" class="status-badge select-badge" color="#409eff" :size="18">
                  <Check />
                </el-icon>
              </div>
              <div class="thumb-label">{{ p.pageNumber }}</div>
            </div>
          </div>
        </template>
      </el-card>

      <!-- 进行中的任务 -->
      <el-card v-if="activeTasks.length > 0" class="task-card">
        <template #header>
          <div class="card-header-row">
            <span class="card-title">
              <el-icon class="is-loading" :size="16"><IconLoading /></el-icon>
              进行中的任务
            </span>
            <el-button text :icon="Refresh" size="small" @click="refreshTasks">刷新</el-button>
          </div>
        </template>

        <div v-for="task in activeTasks" :key="task.id" class="task-item">
          <div class="task-info">
            <div class="task-top-row">
              <el-tag :type="getTaskStatusType(task.status)" size="small">
                {{ statusLabel[task.status] }}
              </el-tag>
              <span class="task-time">{{ formatTime(task.createdAt) }}</span>
            </div>
            <div class="task-progress-row">
              <el-progress
                :percentage="getTaskProgress(task)"
                :status="task.failedPages > 0 ? 'warning' : undefined"
                :stroke-width="16"
                text-inside
              />
            </div>
            <div class="task-stats">
              <span>
                <el-icon color="#67c23a"><CircleCheckFilled /></el-icon>
                成功 {{ task.completedPages }}
              </span>
              <span v-if="task.failedPages > 0">
                <el-icon color="#f56c6c"><WarningFilled /></el-icon>
                失败 {{ task.failedPages }}
              </span>
              <span>总计 {{ task.totalPages }} 页</span>
            </div>
          </div>
          <el-button
            type="danger"
            text
            :icon="Close"
            @click="handleCancelTask(task)"
          >
            取消
          </el-button>
        </div>
      </el-card>

      <!-- 历史任务 -->
      <el-card v-if="completedTasks.length > 0" class="task-card">
        <template #header>
          <span class="card-title">历史任务</span>
        </template>

        <el-table :data="completedTasks" stripe size="default">
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getTaskStatusType(row.status)" size="small">
                {{ statusLabel[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="getTaskProgress(row)"
                :status="row.status === 'completed' ? 'success' : row.failedPages > 0 ? 'warning' : undefined"
              />
            </template>
          </el-table-column>
          <el-table-column label="成功/失败/总计" width="160">
            <template #default="{ row }">
              <span style="color: #67c23a">{{ row.completedPages }}</span>
              /
              <span style="color: #f56c6c">{{ row.failedPages }}</span>
              /
              {{ row.totalPages }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="140">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <TranslateConfigPanel
      v-model:visible="showConfigPanel"
      :manga-id="mangaId"
      :active-config-id="manga?.activeConfigId"
      @applied="handleConfigApplied"
    />
  </div>
</template>

<style scoped>
.page-header-title {
  font-weight: 600;
  color: var(--color-text);
}

.overview-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 24px;
}

.overview-card {
  text-align: center;
}

.stat-suffix {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.action-card,
.task-card {
  margin-top: 20px;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.active-config-display {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.custom-select-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--color-text);
}

.pages-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 10px;
}

.page-thumb {
  cursor: pointer;
  text-align: center;
  transition: transform 0.15s;
  border: 2px solid transparent;
  border-radius: 6px;
  padding: 4px;
}

.page-thumb:hover {
  transform: scale(1.04);
}

.page-thumb.selected {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.thumb-img {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 4px;
  overflow: hidden;
  background: var(--color-bg);
}

.thumb-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.status-badge {
  position: absolute;
  border-radius: 50%;
  background: var(--color-bg-card);
}

.translated-badge {
  top: 4px;
  right: 4px;
}

.select-badge {
  top: 4px;
  left: 4px;
}

.thumb-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.task-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}

.task-item:last-child {
  border-bottom: none;
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-top-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.task-time {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.task-progress-row {
  margin-bottom: 6px;
}

.task-stats {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--color-text);
}

.task-stats span {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
