<script setup lang="ts">
import { computed } from 'vue'
import type { Manga } from '@/types/manga'
import { Picture } from '@element-plus/icons-vue'

const props = defineProps<{
  manga: Manga
}>()

defineEmits<{
  click: [manga: Manga]
}>()

const translatedPercent = computed(() => {
  if (!props.manga.pageCount) return 0
  return 0
})

const coverStyle = computed(() => {
  if (props.manga.coverUrl) {
    return { backgroundImage: `url(${props.manga.coverUrl})` }
  }
  return {}
})

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="manga-card" @click="$emit('click', manga)">
    <div class="card-cover" :style="coverStyle">
      <el-icon v-if="!manga.coverUrl" class="cover-placeholder" :size="48">
        <Picture />
      </el-icon>
      <div class="card-badge">{{ manga.pageCount }} 页</div>
      <div v-if="translatedPercent > 0" class="card-progress">
        <el-progress
          :percentage="translatedPercent"
          :stroke-width="3"
          :show-text="false"
          color="var(--color-success)"
        />
      </div>
    </div>
    <div class="card-body">
      <div class="card-title" :title="manga.title">{{ manga.title }}</div>
      <div class="card-meta">
        <span v-if="manga.author" class="card-author">{{ manga.author }}</span>
        <span class="card-date">{{ formatDate(manga.createdAt) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.manga-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border-light);
}

.manga-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--color-primary-light);
}

.card-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  background: var(--color-bg);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-placeholder {
  color: var(--color-text-muted);
}

.card-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(61, 61, 61, 0.65);
  color: #fff;
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 12px;
  backdrop-filter: blur(4px);
}

.card-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0 4px 4px;
}

.card-body {
  padding: 10px 12px 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.card-author {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60%;
}
</style>
