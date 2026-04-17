import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Manga } from '@/types/manga'

export const useMangaStore = defineStore('manga', () => {
  const mangaList = ref<Manga[]>([])
  const currentManga = ref<Manga | null>(null)
  const loading = ref(false)

  function setMangaList(list: Manga[]) {
    mangaList.value = list
  }

  function setCurrentManga(manga: Manga | null) {
    currentManga.value = manga
  }

  return { mangaList, currentManga, loading, setMangaList, setCurrentManga }
})
