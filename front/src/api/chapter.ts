import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { Chapter, MangaPage } from '@/types/manga'

export function getChapters(mangaId: number) {
  return request.get<unknown, ApiResponse<Chapter[]>>(`/mangas/${mangaId}/chapters`)
}

export function getChapter(mangaId: number, chapterId: number) {
  return request.get<unknown, ApiResponse<Chapter>>(`/mangas/${mangaId}/chapters/${chapterId}`)
}

export function createChapter(mangaId: number, data: { title: string }) {
  return request.post<unknown, ApiResponse<Chapter>>(`/mangas/${mangaId}/chapters`, data)
}

export function updateChapter(mangaId: number, chapterId: number, data: { title?: string }) {
  return request.put<unknown, ApiResponse<Chapter>>(`/mangas/${mangaId}/chapters/${chapterId}`, data)
}

export function deleteChapter(mangaId: number, chapterId: number) {
  return request.delete<unknown, ApiResponse<void>>(`/mangas/${mangaId}/chapters/${chapterId}`)
}

export function reorderChapters(mangaId: number, chapterIds: number[]) {
  return request.post<unknown, ApiResponse<void>>(`/mangas/${mangaId}/chapters/reorder`, { chapterIds })
}

export function getChapterPages(mangaId: number, chapterId: number) {
  return request.get<unknown, ApiResponse<MangaPage[]>>(`/mangas/${mangaId}/chapters/${chapterId}/pages`)
}

export function getAllChapterPages(mangaId: number) {
  return request.get<unknown, ApiResponse<Record<string, MangaPage[]>>>(`/mangas/${mangaId}/chapters/all-pages`)
}

export function uploadChapterPages(mangaId: number, chapterId: number, formData: FormData) {
  return request.post<unknown, ApiResponse<MangaPage[]>>(
    `/mangas/${mangaId}/chapters/${chapterId}/pages`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
}
