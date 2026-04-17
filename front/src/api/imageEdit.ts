import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  RegionBox,
  OcrRegionResult,
} from '@/types/imageEdit'

export function inpaintRegions(imageBlob: Blob, regions: RegionBox[]) {
  const formData = new FormData()
  formData.append('image', imageBlob, 'canvas.png')
  formData.append('regions', JSON.stringify(regions))
  return request.post('/edit/inpaint', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    responseType: 'blob',
    timeout: 120000,
  })
}

export function inpaintAll(imageBlob: Blob) {
  const formData = new FormData()
  formData.append('image', imageBlob, 'canvas.png')
  return request.post('/edit/inpaint-all', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    responseType: 'blob',
    timeout: 120000,
  })
}

export function ocrRegion(imageBlob: Blob, region: RegionBox) {
  const formData = new FormData()
  formData.append('image', imageBlob, 'canvas.png')
  formData.append('region', JSON.stringify(region))
  return request.post<unknown, ApiResponse<OcrRegionResult>>(
    '/edit/ocr-region',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    },
  )
}

export function restoreRegions(imageBlob: Blob, pageId: number, regions: RegionBox[]) {
  const formData = new FormData()
  formData.append('image', imageBlob, 'canvas.png')
  formData.append('pageId', String(pageId))
  formData.append('regions', JSON.stringify(regions))
  return request.post('/edit/restore-regions', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    responseType: 'blob',
    timeout: 30000,
  })
}

export function saveEditedImage(pageId: number, imageBlob: Blob) {
  const formData = new FormData()
  formData.append('pageId', String(pageId))
  formData.append('image', imageBlob, 'edited.png')
  return request.post<unknown, ApiResponse<void>>(
    '/edit/save',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
}
