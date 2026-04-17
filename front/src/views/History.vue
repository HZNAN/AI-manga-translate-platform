<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getTranslateRecords, rollbackToRecord } from '@/api/translate'
import { getMangaList } from '@/api/manga'
import { getChapters } from '@/api/chapter'
import type { TranslationRecord, Manga, Chapter } from '@/types/manga'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheckFilled, WarningFilled, Clock, Loading as IconLoading,
  Search, Picture, ZoomIn, RefreshLeft, UserFilled,
} from '@element-plus/icons-vue'

const router = useRouter()

const records = ref<TranslationRecord[]>([])
const mangaMap = ref<Map<number, Manga>>(new Map())
const chapterMap = ref<Map<number, Chapter>>(new Map())
const loading = ref(false)

const filterMangaId = ref<number | undefined>(undefined)
const filterChapterId = ref<number | undefined>(undefined)
const filterStatus = ref('')
const mangaOptions = ref<Manga[]>([])
const chapterOptions = ref<Chapter[]>([])

const previewVisible = ref(false)
const previewOriginalUrl = ref('')
const previewTranslatedUrl = ref('')
const previewMode = ref<'original' | 'translated'>('translated')

const filteredRecords = computed(() => {
  let result = records.value
  if (filterStatus.value) {
    result = result.filter(r => r.status === filterStatus.value)
  }
  return result
})

async function loadData() {
  loading.value = true
  try {
    const [recordRes, mangaRes] = await Promise.all([
      getTranslateRecords({ mangaId: filterMangaId.value, chapterId: filterChapterId.value }),
      getMangaList({ page: 1, size: 1000 }),
    ])
    records.value = recordRes.data
    mangaOptions.value = mangaRes.data.records
    const map = new Map<number, Manga>()
    for (const m of mangaRes.data.records) {
      map.set(m.id, m)
    }
    mangaMap.value = map
    await loadChapterMapForRecords(recordRes.data)
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

watch(filterMangaId, async (newVal) => {
  filterChapterId.value = undefined
  chapterOptions.value = []
  chapterMap.value = new Map()

  if (newVal) {
    try {
      const res = await getChapters(newVal)
      chapterOptions.value = res.data
      const map = new Map<number, Chapter>()
      for (const c of res.data) {
        map.set(c.id, c)
      }
      chapterMap.value = map
    } catch {
      // ignore
    }
  }

  loadRecords()
})

watch(filterChapterId, () => {
  loadRecords()
})

async function loadRecords() {
  loading.value = true
  try {
    const res = await getTranslateRecords({
      mangaId: filterMangaId.value,
      chapterId: filterChapterId.value,
    })
    records.value = res.data
    await loadChapterMapForRecords(res.data)
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadChapterMapForRecords(data: TranslationRecord[]) {
  const mangaIds = new Set<number>()
  for (const r of data) {
    if (r.chapterId && !chapterMap.value.has(r.chapterId)) {
      mangaIds.add(r.mangaId)
    }
  }
  if (mangaIds.size === 0) return

  const results = await Promise.all(
    [...mangaIds].map(id => getChapters(id).catch(() => null)),
  )
  const map = new Map(chapterMap.value)
  for (const res of results) {
    if (!res) continue
    for (const c of res.data) {
      map.set(c.id, c)
    }
  }
  chapterMap.value = map
}

function getMangaTitle(mangaId: number) {
  return mangaMap.value.get(mangaId)?.title || `漫画 #${mangaId}`
}

function getChapterTitle(chapterId?: number) {
  if (!chapterId) return '-'
  const chapter = chapterMap.value.get(chapterId)
  return chapter ? chapter.title : `Ch.${chapterId}`
}

function getStatusIcon(status: TranslationRecord['status']) {
  const map = {
    queued: Clock,
    translating: IconLoading,
    machine_completed: CircleCheckFilled,
    manual_corrected: UserFilled,
    failed: WarningFilled,
  }
  return map[status]
}

function getStatusColor(status: TranslationRecord['status']) {
  const map: Record<string, string> = {
    queued: '#909399',
    translating: 'var(--color-accent)',
    machine_completed: 'var(--color-success)',
    manual_corrected: '#9b59b6',
    failed: 'var(--color-danger)',
  }
  return map[status] || '#909399'
}

function getStatusTagType(status: TranslationRecord['status']): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    queued: 'info',
    translating: '',
    machine_completed: 'success',
    manual_corrected: 'warning',
    failed: 'danger',
  }
  return map[status] || 'info'
}

const statusLabel: Record<string, string> = {
  queued: '等待中',
  translating: '翻译中',
  machine_completed: '机翻完成',
  manual_corrected: '人工完成',
  failed: '失败',
}

function isCompletedStatus(status: string) {
  return status === 'machine_completed' || status === 'manual_corrected'
}

function formatDuration(ms?: number) {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function openPreview(record: TranslationRecord) {
  previewOriginalUrl.value = `/api/mangas/${record.mangaId}/pages/${record.pageNumber}/image`
  previewTranslatedUrl.value = record.translatedImagePath
    ? `/api/translate/records/${record.id}/image`
    : ''
  previewMode.value = isCompletedStatus(record.status) ? 'translated' : 'original'
  previewVisible.value = true
}

async function handleRollback(record: TranslationRecord) {
  await ElMessageBox.confirm(
    `确定将页面 ${record.pageNumber} 的译图回退到此版本（${statusLabel[record.status]}，${formatTime(record.completedAt || record.createdAt)}）吗？`,
    '版本回退',
    { type: 'warning', confirmButtonText: '回退', cancelButtonText: '取消' },
  )
  try {
    await rollbackToRecord(record.id)
    ElMessage.success('已回退到此版本')
    await loadRecords()
  } catch {
    // handled by interceptor
  }
}

function goToManga(mangaId: number) {
  router.push(`/manga/${mangaId}`)
}

onMounted(loadData)
</script>

<template>
  <div class="history">
    <div class="history-header">
      <h2 class="page-title">翻译历史</h2>
    </div>

    <div class="filter-bar">
      <el-select
        v-model="filterMangaId"
        placeholder="全部漫画"
        clearable
        filterable
        style="width: 260px"
        :prefix-icon="Search"
      >
        <el-option
          v-for="m in mangaOptions"
          :key="m.id"
          :label="m.title"
          :value="m.id"
        />
      </el-select>
      <el-select
        v-model="filterChapterId"
        placeholder="全部章节"
        clearable
        :disabled="!filterMangaId"
        style="width: 200px"
      >
        <el-option
          v-for="c in chapterOptions"
          :key="c.id"
          :label="c.title"
          :value="c.id"
        />
      </el-select>
      <el-select
        v-model="filterStatus"
        placeholder="全部状态"
        clearable
        style="width: 180px"
      >
        <el-option label="机翻完成" value="machine_completed" />
        <el-option label="人工完成" value="manual_corrected" />
        <el-option label="翻译中" value="translating" />
        <el-option label="等待中" value="queued" />
        <el-option label="失败" value="failed" />
      </el-select>
    </div>

    <el-table
      v-loading="loading"
      :data="filteredRecords"
      stripe
      style="width: 100%"
      empty-text="暂无翻译记录"
      class="history-table"
    >
      <el-table-column label="漫画" min-width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="goToManga(row.mangaId)">
            {{ getMangaTitle(row.mangaId) }}
          </el-link>
        </template>
      </el-table-column>

      <el-table-column label="章节" width="120">
        <template #default="{ row }">
          {{ getChapterTitle(row.chapterId) }}
        </template>
      </el-table-column>

      <el-table-column label="页码" width="80" align="center" prop="pageNumber" />

      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <span class="status-cell" :style="{ color: getStatusColor(row.status) }">
            <el-icon
              :size="14"
              :class="row.status === 'translating' ? 'is-loading' : ''"
            >
              <component :is="getStatusIcon(row.status)" />
            </el-icon>
            <span class="status-text">{{ statusLabel[row.status] }}</span>
          </span>
        </template>
      </el-table-column>

      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">
          {{ formatDuration(row.durationMs) }}
        </template>
      </el-table-column>

      <el-table-column label="完成时间" width="170">
        <template #default="{ row }">
          {{ row.completedAt ? formatTime(row.completedAt) : '-' }}
        </template>
      </el-table-column>

      <el-table-column label="错误信息" min-width="140">
        <template #default="{ row }">
          <el-text v-if="row.errorMessage" type="danger" size="small" truncated>
            {{ row.errorMessage }}
          </el-text>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <div class="action-btns">
            <el-button
              v-if="isCompletedStatus(row.status)"
              text
              size="small"
              :icon="ZoomIn"
              @click="openPreview(row)"
            >
              预览
            </el-button>
            <el-button
              v-if="isCompletedStatus(row.status)"
              text
              type="warning"
              size="small"
              :icon="RefreshLeft"
              @click="handleRollback(row)"
            >
              回退
            </el-button>
            <span v-if="!isCompletedStatus(row.status)" class="text-muted">-</span>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="previewVisible"
      title="翻译结果预览"
      width="80%"
      top="3vh"
      destroy-on-close
    >
      <div class="preview-toolbar">
        <el-button-group>
          <el-button
            :type="previewMode === 'original' ? 'primary' : 'default'"
            size="small"
            :icon="Picture"
            @click="previewMode = 'original'"
          >
            原图
          </el-button>
          <el-button
            :type="previewMode === 'translated' ? 'success' : 'default'"
            size="small"
            :disabled="!previewTranslatedUrl"
            @click="previewMode = 'translated'"
          >
            译图
          </el-button>
        </el-button-group>
      </div>
      <div class="preview-image-wrap">
        <img
          v-if="previewMode === 'original'"
          :src="previewOriginalUrl"
          class="preview-image"
        />
        <img
          v-else-if="previewTranslatedUrl"
          :src="previewTranslatedUrl"
          class="preview-image"
        />
        <el-empty v-else description="暂无翻译结果图" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.history-table {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.status-text {
  line-height: 1;
}

.text-muted {
  color: var(--color-text-muted);
}

.action-btns {
  display: flex;
  gap: 2px;
  justify-content: center;
}

.preview-toolbar {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}

.preview-image-wrap {
  display: flex;
  justify-content: center;
  overflow: auto;
  max-height: calc(80vh - 100px);
}

.preview-image {
  max-width: 100%;
  height: auto;
  object-fit: contain;
}
</style>
