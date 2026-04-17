import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ReadingMode = 'page' | 'scroll'
export type ReadingDirection = 'ltr' | 'rtl'
export type FitMode = 'width' | 'height' | 'original' | 'auto'
export type ImageLayer = 'original' | 'translated'

export const useReaderStore = defineStore('reader', () => {
  const currentPage = ref(1)
  const totalPages = ref(0)
  const readingMode = ref<ReadingMode>('page')
  const readingDirection = ref<ReadingDirection>('rtl')
  const fitMode = ref<FitMode>('width')
  const imageLayer = ref<ImageLayer>('original')
  const isFullscreen = ref(false)

  function setPage(page: number) {
    currentPage.value = Math.max(1, Math.min(page, totalPages.value))
  }

  function nextPage() {
    setPage(currentPage.value + 1)
  }

  function prevPage() {
    setPage(currentPage.value - 1)
  }

  function reset() {
    currentPage.value = 1
    totalPages.value = 0
    imageLayer.value = 'original'
  }

  return {
    currentPage, totalPages, readingMode, readingDirection,
    fitMode, imageLayer, isFullscreen,
    setPage, nextPage, prevPage, reset,
  }
})
