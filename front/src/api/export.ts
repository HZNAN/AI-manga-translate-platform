import request from '@/utils/request'

export function exportZip(mangaId: number, options?: { onlyTranslated?: boolean }) {
  return request.post('/export/zip', { mangaId, ...options }, { responseType: 'blob' })
}

export function exportPdf(mangaId: number, options?: { onlyTranslated?: boolean }) {
  return request.post('/export/pdf', { mangaId, ...options }, { responseType: 'blob' })
}
