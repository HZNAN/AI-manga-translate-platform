import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface LlmConfig {
  id: number
  userId?: number
  name: string
  provider: string
  apiKey: string
  modelName: string
  baseUrl?: string
  isDefault: boolean
  multimodal: boolean
  secretKey?: string
  isSystem: boolean
  createdAt: string
  updatedAt: string
}

export function getLlmConfigs() {
  return request.get<unknown, ApiResponse<LlmConfig[]>>('/llm-configs')
}

export function getLlmConfig(id: number) {
  return request.get<unknown, ApiResponse<LlmConfig>>(`/llm-configs/${id}`)
}

export function createLlmConfig(data: Partial<LlmConfig>) {
  return request.post<unknown, ApiResponse<LlmConfig>>('/llm-configs', data)
}

export function updateLlmConfig(id: number, data: Partial<LlmConfig>) {
  return request.put<unknown, ApiResponse<LlmConfig>>(`/llm-configs/${id}`, data)
}

export function deleteLlmConfig(id: number) {
  return request.delete<unknown, ApiResponse<void>>(`/llm-configs/${id}`)
}
