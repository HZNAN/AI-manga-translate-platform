<script setup lang="ts">
import { ref, computed } from 'vue'
import { uploadArchive, createManga } from '@/api/manga'
import { uploadChapterPages, createChapter } from '@/api/chapter'
import { UploadFilled, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'

const props = defineProps<{
  visible: boolean
  mangaId?: number
  chapterId?: number
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  success: []
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

type UploadMode = 'archive' | 'images'
const uploadMode = ref<UploadMode>('archive')
const uploading = ref(false)
const uploadPercent = ref(0)

const mangaTitle = ref('')
const mangaAuthor = ref('')
const archiveFile = ref<File | null>(null)
const imageFiles = ref<File[]>([])

function handleArchiveChange(uploadFile: UploadFile) {
  if (uploadFile.raw) {
    archiveFile.value = uploadFile.raw
  }
}

function handleImagesChange(_uploadFile: UploadFile, uploadFiles: UploadFile[]) {
  imageFiles.value = uploadFiles
    .map((f) => f.raw)
    .filter((f): f is File => !!f)
}

function handleRemoveImage(_uploadFile: UploadFile, uploadFiles: UploadFile[]) {
  imageFiles.value = uploadFiles
    .map((f) => f.raw)
    .filter((f): f is File => !!f)
}

const canSubmit = computed(() => {
  if (uploadMode.value === 'archive') {
    return !!archiveFile.value
  }
  return imageFiles.value.length > 0 && !!mangaTitle.value.trim()
})

async function handleSubmit() {
  if (!canSubmit.value) return
  uploading.value = true
  uploadPercent.value = 0

  try {
    if (uploadMode.value === 'archive') {
      const formData = new FormData()
      formData.append('file', archiveFile.value!)
      if (mangaTitle.value.trim()) {
        formData.append('title', mangaTitle.value.trim())
      }
      if (mangaAuthor.value.trim()) {
        formData.append('author', mangaAuthor.value.trim())
      }
      await uploadArchive(formData)
    } else {
      let targetMangaId = props.mangaId
      let targetChapterId = props.chapterId

      if (!targetMangaId) {
        const res = await createManga({
          title: mangaTitle.value.trim(),
          author: mangaAuthor.value.trim() || undefined,
        })
        targetMangaId = res.data.id
      }

      if (!targetChapterId) {
        const chRes = await createChapter(targetMangaId, { title: '第1话' })
        targetChapterId = chRes.data.id
      }

      const formData = new FormData()
      imageFiles.value.forEach((f) => formData.append('files', f))
      await uploadChapterPages(targetMangaId, targetChapterId, formData)
    }
    ElMessage.success('导入成功')
    resetForm()
    dialogVisible.value = false
    emit('success')
  } catch {
    // error handled by request interceptor
  } finally {
    uploading.value = false
  }
}

function resetForm() {
  mangaTitle.value = ''
  mangaAuthor.value = ''
  archiveFile.value = null
  imageFiles.value = []
  uploadPercent.value = 0
}

function handleClose() {
  if (!uploading.value) {
    resetForm()
  }
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入漫画"
    width="560px"
    :close-on-click-modal="!uploading"
    :close-on-press-escape="!uploading"
    @close="handleClose"
  >
    <el-radio-group v-model="uploadMode" style="margin-bottom: 20px">
      <el-radio-button value="archive">上传压缩包</el-radio-button>
      <el-radio-button value="images">上传图片</el-radio-button>
    </el-radio-group>

    <el-form label-width="80px">
      <el-form-item label="标题" :required="uploadMode === 'images'">
        <el-input
          v-model="mangaTitle"
          placeholder="漫画标题（压缩包模式可留空，自动取文件名）"
        />
      </el-form-item>
      <el-form-item label="作者">
        <el-input v-model="mangaAuthor" placeholder="作者（可选）" />
      </el-form-item>

      <el-form-item v-if="uploadMode === 'archive'" label="文件">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".zip,.rar,.cbz,.cbr"
          :on-change="handleArchiveChange"
          :show-file-list="false"
        >
          <el-icon :size="40"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            拖拽文件到此或 <em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">支持 ZIP / RAR / CBZ / CBR，最大 500MB</div>
          </template>
        </el-upload>
        <el-tooltip v-if="archiveFile" :content="archiveFile.name" placement="top" :show-after="300">
          <div class="selected-file">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ archiveFile.name }}</span>
          </div>
        </el-tooltip>
      </el-form-item>

      <el-form-item v-else label="图片">
        <el-upload
          drag
          multiple
          :auto-upload="false"
          accept="image/jpeg,image/png,image/webp"
          :on-change="handleImagesChange"
          :on-remove="handleRemoveImage"
          list-type="picture"
        >
          <el-icon :size="40"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            拖拽文件到此或 <em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">支持 JPG / PNG / WebP，可多选</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="uploading" @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!canSubmit" @click="handleSubmit">
        {{ uploading ? '上传中...' : '开始导入' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.selected-file {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
  max-width: 100%;
  overflow: hidden;
}

.selected-file .file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #67c23a;
}
</style>
