<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMangaDetail, deleteManga, updateManga } from '@/api/manga'
import { getChapters, createChapter, deleteChapter, getAllChapterPages, getChapterPages, updateChapter } from '@/api/chapter'
import type { Manga, MangaPage, Chapter } from '@/types/manga'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Reading, Download, Edit, Delete,
  Picture, CircleCheckFilled, Plus,
  ArrowDown, ArrowRight, FolderOpened,
} from '@element-plus/icons-vue'
import IconTranslate from '@/components/icons/IconTranslate.vue'
import UploadDialog from '@/components/UploadDialog.vue'

const route = useRoute()
const router = useRouter()
const mangaId = computed(() => Number(route.params.id))

const manga = ref<Manga | null>(null)
const chapters = ref<Chapter[]>([])
const loading = ref(true)
const showUpload = ref(false)
const uploadChapterId = ref<number | undefined>(undefined)
const editDialogVisible = ref(false)
const newChapterDialogVisible = ref(false)
const newChapterTitle = ref('')

const expandedChapterId = ref<number | null>(null)
const chapterPages = ref<Map<number, MangaPage[]>>(new Map())

const editForm = ref({
  title: '',
  author: '',
  description: '',
  tags: '',
})

const translatedCount = computed(() => {
  let count = 0
  for (const pages of chapterPages.value.values()) {
    count += pages.filter(p => p.isTranslated).length
  }
  return count
})

const totalPageCount = computed(() => manga.value?.pageCount ?? 0)

const translatedPercent = computed(() => {
  if (!totalPageCount.value) return 0
  return Math.round((translatedCount.value / totalPageCount.value) * 100)
})

async function loadData() {
  loading.value = true
  try {
    const [mangaRes, chaptersRes, allPagesRes] = await Promise.all([
      getMangaDetail(mangaId.value),
      getChapters(mangaId.value),
      getAllChapterPages(mangaId.value),
    ])
    manga.value = mangaRes.data
    chapters.value = chaptersRes.data
    editForm.value = {
      title: mangaRes.data.title,
      author: mangaRes.data.author || '',
      description: mangaRes.data.description || '',
      tags: mangaRes.data.tags || '',
    }
    chapterPages.value.clear()
    for (const [chIdStr, pages] of Object.entries(allPagesRes.data)) {
      chapterPages.value.set(Number(chIdStr), pages)
    }
  } finally {
    loading.value = false
  }
}

async function loadChapterPages(chapterId: number) {
  const res = await getChapterPages(mangaId.value, chapterId)
  chapterPages.value.set(chapterId, res.data)
}

function toggleChapter(chapterId: number) {
  expandedChapterId.value = expandedChapterId.value === chapterId ? null : chapterId
}

function goRead(chapterId: number, pageNum?: number) {
  const query: Record<string, string> = { chapterId: String(chapterId) }
  if (pageNum) query.page = String(pageNum)
  router.push({ path: `/manga/${mangaId.value}/read`, query })
}

function goTranslate() {
  router.push(`/manga/${mangaId.value}/translate`)
}

async function handleDelete() {
  await ElMessageBox.confirm('删除后不可恢复，确定删除该漫画及所有翻译结果吗？', '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteManga(mangaId.value)
  ElMessage.success('删除成功')
  router.push('/')
}

async function handleSaveEdit() {
  if (!manga.value) return
  await updateManga(manga.value.id, editForm.value)
  ElMessage.success('保存成功')
  editDialogVisible.value = false
  await loadData()
}

async function handleCreateChapter() {
  if (!newChapterTitle.value.trim()) return
  await createChapter(mangaId.value, { title: newChapterTitle.value.trim() })
  newChapterTitle.value = ''
  newChapterDialogVisible.value = false
  ElMessage.success('章节创建成功')
  await loadData()
}

async function handleDeleteChapter(ch: Chapter) {
  await ElMessageBox.confirm(`确定删除章节"${ch.title}"及其所有页面吗？`, '确认删除', {
    type: 'warning',
  })
  await deleteChapter(mangaId.value, ch.id)
  ElMessage.success('章节已删除')
  await loadData()
}

async function handleRenameChapter(ch: Chapter) {
  const { value } = await ElMessageBox.prompt('请输入新标题', '重命名章节', {
    inputValue: ch.title,
    confirmButtonText: '保存',
  })
  if (value && value.trim()) {
    await updateChapter(mangaId.value, ch.id, { title: value.trim() })
    ElMessage.success('已重命名')
    await loadData()
  }
}

function getPageImageUrl(page: MangaPage) {
  return page.thumbnailPath
    ? `/api/mangas/page-by-id/${page.id}/thumbnail`
    : `/api/mangas/page-by-id/${page.id}/image`
}

function getChapterTranslatedCount(chapterId: number) {
  const pages = chapterPages.value.get(chapterId) ?? []
  return pages.filter(p => p.isTranslated).length
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="manga-detail">
    <template v-if="manga">
      <el-page-header @back="router.push('/')">
        <template #content>
          <span class="detail-title">{{ manga.title }}</span>
        </template>
      </el-page-header>

      <div class="detail-info">
        <div class="info-cover">
          <div class="cover-img">
            <img v-if="manga.coverUrl" :src="manga.coverUrl" :alt="manga.title" />
            <el-icon v-else :size="64" color="#c0c4cc"><Picture /></el-icon>
          </div>
        </div>
        <div class="info-body">
          <h1 class="info-title">{{ manga.title }}</h1>
          <div class="info-meta">
            <el-tag v-if="manga.author" type="info" size="small">{{ manga.author }}</el-tag>
            <el-tag type="info" size="small">{{ chapters.length }} 章节</el-tag>
            <el-tag type="info" size="small">{{ manga.pageCount }} 页</el-tag>
            <el-tag :type="translatedPercent === 100 ? 'success' : 'warning'" size="small">
              翻译 {{ translatedPercent }}%
            </el-tag>
            <el-tag v-if="manga.readingDirection === 'rtl'" size="small">日漫 RTL</el-tag>
            <el-tag v-else size="small">LTR</el-tag>
          </div>
          <p v-if="manga.description" class="info-desc">{{ manga.description }}</p>
          <div v-if="manga.tags" class="info-tags">
            <el-tag
              v-for="tag in manga.tags.split(',')"
              :key="tag"
              size="small"
              style="margin-right: 6px"
            >{{ tag.trim() }}</el-tag>
          </div>
          <div class="info-actions">
            <el-button type="primary" :icon="Reading" @click="goRead(chapters[0]?.id)">
              开始阅读
            </el-button>
            <el-button type="success" :icon="IconTranslate" @click="goTranslate">
              翻译管理
            </el-button>
            <el-button :icon="Download">导出</el-button>
            <el-button :icon="Edit" @click="editDialogVisible = true">编辑</el-button>
            <el-button type="danger" :icon="Delete" @click="handleDelete">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 章节列表 -->
      <div class="chapters-section">
        <div class="section-header">
          <h3 class="section-title">章节列表 ({{ chapters.length }})</h3>
          <el-button size="small" :icon="Plus" @click="newChapterDialogVisible = true">
            新建章节
          </el-button>
        </div>

        <div class="chapter-list">
          <div v-for="ch in chapters" :key="ch.id" class="chapter-item">
            <div class="chapter-header" @click="toggleChapter(ch.id)">
              <el-icon class="expand-icon" :class="{ expanded: expandedChapterId === ch.id }">
                <ArrowRight />
              </el-icon>
              <el-icon color="#909399"><FolderOpened /></el-icon>
              <span class="chapter-title">{{ ch.title }}</span>
              <span class="chapter-info">
                {{ ch.pageCount }} 页 ·
                {{ getChapterTranslatedCount(ch.id) }}/{{ ch.pageCount }} 已翻译
              </span>
              <div class="chapter-actions" @click.stop>
                <el-button text size="small" type="primary" @click="goRead(ch.id)">
                  阅读
                </el-button>
                <el-button text size="small" type="success" @click="uploadChapterId = ch.id; showUpload = true">
                  上传图片
                </el-button>
                <el-button text size="small" @click="handleRenameChapter(ch)">
                  重命名
                </el-button>
                <el-button text size="small" type="danger" @click="handleDeleteChapter(ch)">
                  删除
                </el-button>
              </div>
            </div>

            <transition name="expand">
              <div v-if="expandedChapterId === ch.id" class="chapter-pages">
                <div class="pages-grid">
                  <div
                    v-for="p in (chapterPages.get(ch.id) ?? [])"
                    :key="p.id"
                    class="page-thumb"
                    :class="{ translated: p.isTranslated }"
                    @click="goRead(ch.id, p.pageNumber)"
                  >
                    <div class="thumb-img">
                      <img :src="getPageImageUrl(p)" :alt="`第${p.pageNumber}页`" loading="lazy" />
                      <el-icon v-if="p.isTranslated" class="translated-badge" color="#67c23a" :size="18">
                        <CircleCheckFilled />
                      </el-icon>
                    </div>
                    <div class="thumb-label">{{ p.pageNumber }}</div>
                  </div>
                </div>
                <el-empty v-if="(chapterPages.get(ch.id) ?? []).length === 0" description="暂无页面" :image-size="60" />
              </div>
            </transition>
          </div>
        </div>
        <el-empty v-if="!loading && chapters.length === 0" description="暂无章节" />
      </div>
    </template>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑漫画信息" width="480px">
      <el-form :model="editForm" label-width="60px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="editForm.author" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" placeholder="逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建章节对话框 -->
    <el-dialog v-model="newChapterDialogVisible" title="新建章节" width="400px">
      <el-input v-model="newChapterTitle" placeholder="请输入章节标题" @keyup.enter="handleCreateChapter" />
      <template #footer>
        <el-button @click="newChapterDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateChapter">创建</el-button>
      </template>
    </el-dialog>

    <UploadDialog
      v-model:visible="showUpload"
      :manga-id="mangaId"
      :chapter-id="uploadChapterId"
      @success="loadData"
    />
  </div>
</template>

<style scoped>
.detail-title { font-weight: 600; color: var(--color-text); }

.detail-info {
  display: flex;
  gap: 32px;
  margin-top: 24px;
  padding: 24px;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
}

.info-cover { flex-shrink: 0; }

.cover-img {
  width: 200px;
  aspect-ratio: 3 / 4;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-bg);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-img img { width: 100%; height: 100%; object-fit: cover; }

.info-body { flex: 1; min-width: 0; }
.info-title { font-size: 24px; font-weight: 700; margin: 0 0 12px; color: var(--color-text); }
.info-meta { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.info-desc { color: var(--color-text-secondary); line-height: 1.6; margin-bottom: 12px; }
.info-tags { margin-bottom: 16px; }
.info-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.chapters-section { margin-top: 24px; }

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title { font-size: 16px; font-weight: 600; margin: 0; color: var(--color-text); }

.chapter-list { display: flex; flex-direction: column; gap: 4px; }

.chapter-item {
  background: var(--color-bg-card);
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--color-border-light);
}

.chapter-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
}

.chapter-header:hover { background: var(--color-bg-hover); }

.expand-icon {
  transition: transform 0.2s;
  font-size: 14px;
  color: var(--color-text-muted);
}

.expand-icon.expanded { transform: rotate(90deg); }

.chapter-title { font-weight: 500; flex: 1; min-width: 0; color: var(--color-text); }

.chapter-info { color: var(--color-text-secondary); font-size: 13px; white-space: nowrap; }

.chapter-actions { margin-left: auto; display: flex; gap: 4px; }

.chapter-pages { padding: 0 16px 16px; }

.pages-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 10px;
}

.page-thumb { cursor: pointer; text-align: center; transition: transform 0.15s; }
.page-thumb:hover { transform: scale(1.04); }

.thumb-img {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 4px;
  overflow: hidden;
  background: var(--color-bg);
  border: 2px solid transparent;
}

.page-thumb.translated .thumb-img { border-color: var(--color-success); }
.thumb-img img { width: 100%; height: 100%; object-fit: cover; }

.translated-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  background: var(--color-bg-card);
  border-radius: 50%;
}

.thumb-label { margin-top: 4px; font-size: 12px; color: var(--color-text-secondary); }

.expand-enter-active, .expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.expand-enter-from, .expand-leave-to {
  opacity: 0;
  max-height: 0;
}
</style>
