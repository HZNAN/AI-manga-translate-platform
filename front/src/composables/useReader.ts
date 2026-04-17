import { ref, computed, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMangaDetail, updateReadingProgress } from '@/api/manga'
import { getChapters, getChapterPages } from '@/api/chapter'
import { translatePage } from '@/api/translate'
import { useReaderStore } from '@/stores/reader'
import type { Manga, MangaPage, Chapter } from '@/types/manga'
import type { ImageLayer } from '@/stores/reader'
import { ElMessage } from 'element-plus'
import { useWebSocket, type WsMessage } from '@/utils/websocket'

export function useReader() {
  const route = useRoute()
  const router = useRouter()
  const store = useReaderStore()

  const manga = ref<Manga | null>(null)
  const chapters = ref<Chapter[]>([])
  const pages = ref<MangaPage[]>([])
  const currentChapterId = ref<number | null>(null)
  const loading = ref(true)
  const translating = ref(false)
  const translateStatus = ref<string>('')
  const showConfig = ref(false)

  const mangaId = computed(() => Number(route.params.id))
  const initialPage = computed(() => Number(route.query.page) || 1)
  const initialChapterId = computed(() => Number(route.query.chapterId) || 0)

  const currentChapter = computed(() =>
    chapters.value.find((ch) => ch.id === currentChapterId.value) ?? null,
  )

  const currentChapterIndex = computed(() =>
    chapters.value.findIndex((ch) => ch.id === currentChapterId.value),
  )

  const hasNextChapter = computed(() =>
    currentChapterIndex.value >= 0 && currentChapterIndex.value < chapters.value.length - 1,
  )

  const hasPrevChapter = computed(() =>
    currentChapterIndex.value > 0,
  )

  const currentPageData = computed(() =>
    pages.value.find((p) => p.pageNumber === store.currentPage),
  )

  const currentImageUrl = computed(() => {
    const p = currentPageData.value
    if (!p) return ''
    if (store.imageLayer === 'translated' && p.translatedImagePath) {
      return `/api/mangas/page-by-id/${p.id}/translated-image`
    }
    return `/api/mangas/page-by-id/${p.id}/image`
  })

  const canShowTranslated = computed(() => {
    return !!currentPageData.value?.isTranslated
  })

  async function loadData() {
    loading.value = true
    try {
      const [mangaRes, chaptersRes] = await Promise.all([
        getMangaDetail(mangaId.value),
        getChapters(mangaId.value),
      ])
      manga.value = mangaRes.data
      chapters.value = chaptersRes.data
      store.readingDirection = mangaRes.data.readingDirection

      let targetChapterId = initialChapterId.value
      if (!targetChapterId && chaptersRes.data.length > 0) {
        targetChapterId = chaptersRes.data[0]!.id
      }

      if (targetChapterId) {
        await loadChapter(targetChapterId, initialPage.value)
      }
    } finally {
      loading.value = false
    }
  }

  async function refreshManga() {
    try {
      const res = await getMangaDetail(mangaId.value)
      manga.value = res.data
    } catch { /* ignore */ }
  }

  async function loadChapter(chapterId: number, page?: number) {
    currentChapterId.value = chapterId
    const pagesRes = await getChapterPages(mangaId.value, chapterId)
    pages.value = pagesRes.data
    store.totalPages = pagesRes.data.length

    const targetPage = page && page > 0 && page <= store.totalPages ? page : 1
    store.setPage(targetPage)
  }

  async function goNextChapter() {
    if (!hasNextChapter.value) return
    const nextIdx = currentChapterIndex.value + 1
    const nextCh = chapters.value[nextIdx]
    if (nextCh) {
      loading.value = true
      try {
        await loadChapter(nextCh.id, 1)
      } finally {
        loading.value = false
      }
    }
  }

  async function goPrevChapter() {
    if (!hasPrevChapter.value) return
    const prevIdx = currentChapterIndex.value - 1
    const prevCh = chapters.value[prevIdx]
    if (prevCh) {
      loading.value = true
      try {
        await loadChapter(prevCh.id, 1)
      } finally {
        loading.value = false
      }
    }
  }

  function goNext() {
    if (store.readingDirection === 'rtl') {
      store.prevPage()
    } else {
      store.nextPage()
    }
  }

  function goPrev() {
    if (store.readingDirection === 'rtl') {
      store.nextPage()
    } else {
      store.prevPage()
    }
  }

  function toggleLayer() {
    if (canShowTranslated.value) {
      store.imageLayer = store.imageLayer === 'original' ? 'translated' : 'original'
    }
  }

  function setLayer(layer: ImageLayer) {
    store.imageLayer = layer
  }

  let pendingRecordId: number | null = null
  let translateTimeout: ReturnType<typeof setTimeout> | null = null

  function handleWsMessage(msg: WsMessage) {
    if (msg.type === 'RECORD_STATUS' && pendingRecordId != null && msg.recordId === pendingRecordId) {
      translateStatus.value = msg.status

      if (msg.status === 'machine_completed') {
        translating.value = false
        translateStatus.value = ''
        pendingRecordId = null
        clearTranslateTimeout()
        store.imageLayer = 'translated'
        if (currentChapterId.value) {
          getChapterPages(mangaId.value, currentChapterId.value).then((pagesRes) => {
            pages.value = pagesRes.data
          })
        }
        ElMessage.success('翻译完成')
      } else if (msg.status === 'failed') {
        translating.value = false
        translateStatus.value = ''
        pendingRecordId = null
        clearTranslateTimeout()
        ElMessage.error(msg.errorMessage || '翻译失败')
      }
    }
  }

  useWebSocket(handleWsMessage)

  function clearTranslateTimeout() {
    if (translateTimeout) {
      clearTimeout(translateTimeout)
      translateTimeout = null
    }
  }

  async function doTranslate() {
    const p = currentPageData.value
    if (!p) return

    if (!manga.value?.activeConfigId) {
      ElMessage.warning('请先设置翻译配置')
      showConfig.value = true
      return
    }

    translating.value = true
    translateStatus.value = 'queued'

    try {
      const res = await translatePage({ pageId: p.id })
      pendingRecordId = res.data.id

      clearTranslateTimeout()
      translateTimeout = setTimeout(() => {
        if (translating.value) {
          translating.value = false
          translateStatus.value = ''
          pendingRecordId = null
          ElMessage.warning('翻译超时，请在翻译历史中查看结果')
        }
      }, 10 * 60 * 1000)
    } catch {
      translating.value = false
      translateStatus.value = ''
    }
  }

  function goBack() {
    clearTranslateTimeout()
    router.push(`/manga/${mangaId.value}`)
  }

  watch(
    () => store.currentPage,
    (page) => {
      updateReadingProgress(mangaId.value, page).catch(() => {})
    },
  )

  onUnmounted(() => {
    clearTranslateTimeout()
  })

  return {
    manga,
    chapters,
    pages,
    currentChapter,
    currentChapterId,
    hasNextChapter,
    hasPrevChapter,
    loading,
    translating,
    translateStatus,
    store,
    currentPageData,
    currentImageUrl,
    canShowTranslated,
    showConfig,
    loadData,
    loadChapter,
    goNext,
    goPrev,
    goNextChapter,
    goPrevChapter,
    toggleLayer,
    setLayer,
    doTranslate,
    goBack,
    refreshManga,
  }
}
