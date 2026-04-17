import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { TranslationRecord, TranslationTask } from '@/types/manga'

export interface TranslatePageParams {
  pageId: number
}

export function translatePage(data: TranslatePageParams) {
  return request.post<unknown, ApiResponse<TranslationRecord>>('/translate/page', data)
}

export function translatePageJson(data: { pageId: number }) {
  return request.post<unknown, ApiResponse<TranslationRecord>>('/translate/page/json', data)
}

export function batchTranslate(data: {
  mangaId: number
  pageIds?: number[]
  forceRetranslate?: boolean
}) {
  return request.post<unknown, ApiResponse<TranslationTask>>('/translate/batch', data)
}

export function getTranslateTasks(params?: { mangaId?: number }) {
  return request.get<unknown, ApiResponse<TranslationTask[]>>('/translate/tasks', { params })
}

export function getTaskDetail(taskId: number) {
  return request.get<unknown, ApiResponse<TranslationTask>>(`/translate/tasks/${taskId}`)
}

export function cancelTask(taskId: number) {
  return request.delete<unknown, ApiResponse<void>>(`/translate/tasks/${taskId}`)
}

export function getTranslateRecords(params?: { mangaId?: number; chapterId?: number; pageId?: number }) {
  return request.get<unknown, ApiResponse<TranslationRecord[]>>('/translate/records', { params })
}

export function getRecordStatus(recordId: number) {
  return request.get<unknown, ApiResponse<TranslationRecord>>(`/translate/records/${recordId}/status`)
}

export function rollbackToRecord(recordId: number) {
  return request.post<unknown, ApiResponse<void>>(`/translate/records/${recordId}/rollback`)
}

export function getQueueSize() {
  return request.get<unknown, ApiResponse<number>>('/translate/queue-size')
}
