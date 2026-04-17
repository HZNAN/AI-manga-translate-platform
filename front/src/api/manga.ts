import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { Manga, MangaPage } from '@/types/manga'

export function getMangaList(params?: {
  page?: number
  size?: number
  keyword?: string
  sort?: string
}) {
  return request.get<unknown, ApiResponse<PageResult<Manga>>>('/mangas', { params })
}

export function getMangaDetail(id: number) {
  return request.get<unknown, ApiResponse<Manga>>(`/mangas/${id}`)
}

export function createManga(data: { title: string; author?: string; description?: string }) {
  return request.post<unknown, ApiResponse<Manga>>('/mangas', data)
}

export function updateManga(id: number, data: Partial<Manga>) {
  return request.put<unknown, ApiResponse<Manga>>(`/mangas/${id}`, data)
}

export function deleteManga(id: number) {
  return request.delete<unknown, ApiResponse<void>>(`/mangas/${id}`)
}

export function getMangaPages(id: number) {
  return request.get<unknown, ApiResponse<MangaPage[]>>(`/mangas/${id}/pages`)
}

export function uploadPages(mangaId: number, formData: FormData) {
  return request.post<unknown, ApiResponse<MangaPage[]>>(
    `/mangas/${mangaId}/pages`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
}

export function uploadArchive(formData: FormData) {
  return request.post<unknown, ApiResponse<Manga>>(
    '/mangas/upload-archive',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 },
  )
}

export function updateReadingProgress(mangaId: number, page: number) {
  return request.put<unknown, ApiResponse<void>>(
    `/mangas/${mangaId}/reading-progress`,
    { page },
  )
}

export function setActiveConfig(mangaId: number, configId: number) {
  return request.put<unknown, ApiResponse<Manga>>(`/mangas/${mangaId}/active-config/${configId}`)
}

export function clearActiveConfig(mangaId: number) {
  return request.delete<unknown, ApiResponse<Manga>>(`/mangas/${mangaId}/active-config`)
}
