import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { TranslateConfig } from '@/types/manga'

export function getConfigs() {
  return request.get<unknown, ApiResponse<TranslateConfig[]>>('/configs')
}

export function createConfig(data: Partial<TranslateConfig>) {
  return request.post<unknown, ApiResponse<TranslateConfig>>('/configs', data)
}

export function updateConfig(id: number, data: Partial<TranslateConfig>) {
  return request.put<unknown, ApiResponse<TranslateConfig>>(`/configs/${id}`, data)
}

export function deleteConfig(id: number) {
  return request.delete<unknown, ApiResponse<void>>(`/configs/${id}`)
}

export function getPresets() {
  return request.get<unknown, ApiResponse<TranslateConfig[]>>('/configs/presets')
}
