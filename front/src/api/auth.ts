import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

interface LoginResult {
  token: string
  user: {
    id: number
    username: string
    avatarUrl?: string
  }
}

export function login(data: { username: string; password: string }) {
  return request.post<unknown, ApiResponse<LoginResult>>('/auth/login', data)
}

export function register(data: { username: string; password: string }) {
  return request.post<unknown, ApiResponse<LoginResult>>('/auth/register', data)
}

export function getCurrentUser() {
  return request.get<unknown, ApiResponse<LoginResult['user']>>('/auth/me')
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put<unknown, ApiResponse<void>>('/auth/password', data)
}
