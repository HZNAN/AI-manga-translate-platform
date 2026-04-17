<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMangaList } from '@/api/manga'
import type { Manga } from '@/types/manga'
import MangaCard from '@/components/MangaCard.vue'
import UploadDialog from '@/components/UploadDialog.vue'
import { Plus, Search, Grid, List } from '@element-plus/icons-vue'

const router = useRouter()
const mangaList = ref<Manga[]>([])
const loading = ref(false)
const keyword = ref('')
const sortBy = ref('createdAt')
const viewMode = ref<'grid' | 'list'>('grid')
const showUpload = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(24)

async function loadMangas() {
  loading.value = true
  try {
    const res = await getMangaList({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      sort: sortBy.value,
    })
    mangaList.value = res.data.records
    total.value = res.data.total
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleCardClick(manga: Manga) {
  router.push(`/manga/${manga.id}`)
}

function handleSearch() {
  page.value = 1
  loadMangas()
}

function handleSortChange() {
  page.value = 1
  loadMangas()
}

function handlePageChange(val: number) {
  page.value = val
  loadMangas()
}

function handleUploadSuccess() {
  page.value = 1
  loadMangas()
}

onMounted(loadMangas)
</script>

<template>
  <div class="home">
    <div class="home-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">我的书架</h2>
        <el-input
          v-model="keyword"
          placeholder="搜索漫画..."
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="sortBy" style="width: 150px" @change="handleSortChange">
          <el-option label="最新导入" value="createdAt" />
          <el-option label="最近阅读" value="lastReadAt" />
          <el-option label="名称排序" value="title" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button-group>
          <el-button :type="viewMode === 'grid' ? 'primary' : 'default'" :icon="Grid" @click="viewMode = 'grid'" />
          <el-button :type="viewMode === 'list' ? 'primary' : 'default'" :icon="List" @click="viewMode = 'list'" />
        </el-button-group>
        <el-button type="primary" :icon="Plus" @click="showUpload = true">
          导入漫画
        </el-button>
      </div>
    </div>

    <div v-loading="loading">
      <template v-if="mangaList.length > 0">
        <!-- 卡片视图 -->
        <div v-if="viewMode === 'grid'" class="manga-grid">
          <MangaCard
            v-for="manga in mangaList"
            :key="manga.id"
            :manga="manga"
            @click="handleCardClick"
          />
        </div>
        <!-- 列表视图 -->
        <el-table v-else :data="mangaList" stripe @row-click="handleCardClick" style="cursor: pointer">
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="author" label="作者" width="150" />
          <el-table-column prop="pageCount" label="页数" width="80" align="center" />
          <el-table-column prop="createdAt" label="导入时间" width="120">
            <template #default="{ row }">
              {{ new Date(row.createdAt).toLocaleDateString('zh-CN') }}
            </template>
          </el-table-column>
        </el-table>

        <div v-if="total > pageSize" class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </template>

      <el-empty v-else-if="!loading" description="还没有漫画，点击右上角导入第一本吧">
        <el-button type="primary" @click="showUpload = true">导入漫画</el-button>
      </el-empty>
    </div>

    <UploadDialog v-model:visible="showUpload" @success="handleUploadSuccess" />
  </div>
</template>

<style scoped>
.home-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  white-space: nowrap;
  color: var(--color-text);
}

.manga-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 20px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
