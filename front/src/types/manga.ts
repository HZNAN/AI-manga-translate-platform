export interface Manga {
  id: number
  userId: number
  title: string
  author?: string
  description?: string
  coverUrl?: string
  pageCount: number
  tags?: string
  readingDirection: 'ltr' | 'rtl'
  lastReadPage: number
  lastReadAt?: string
  activeConfigId?: number
  createdAt: string
  updatedAt: string
}

export interface Chapter {
  id: number
  mangaId: number
  title: string
  chapterNumber: number
  pageCount: number
  createdAt: string
  updatedAt: string
}

export interface MangaPage {
  id: number
  mangaId: number
  chapterId: number
  pageNumber: number
  originalFilename?: string
  imagePath: string
  thumbnailPath?: string
  width?: number
  height?: number
  fileSize?: number
  isTranslated: boolean
  translatedImagePath?: string
  createdAt: string
}

export interface TranslateConfig {
  id: number
  userId: number
  name: string
  isDefault: boolean
  targetLang: string
  translator: string
  detector: string
  detectionSize: number
  textThreshold: number
  boxThreshold: number
  unclipRatio: number
  ocr: string
  sourceLang: string
  useMocrMerge: boolean
  inpainter: string
  inpaintingSize: number
  inpaintingPrecision: string
  renderer: string
  alignment: string
  direction: string
  fontSizeOffset: number
  maskDilationOffset: number
  kernelSize: number
  upscaler?: string
  upscaleRatio?: number
  colorizer: string
  llmConfigId?: number
  extraConfig?: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface TranslationRecord {
  id: number
  userId: number
  mangaId: number
  pageId: number
  chapterId?: number
  pageNumber: number
  configId?: number
  taskId?: number
  status: 'queued' | 'translating' | 'machine_completed' | 'manual_corrected' | 'failed'
  translatedImagePath?: string
  translationJson?: unknown
  configSnapshot?: Record<string, unknown>
  errorMessage?: string
  durationMs?: number
  createdAt: string
  completedAt?: string
}

export interface TranslationTask {
  id: number
  userId: number
  mangaId: number
  configId?: number
  totalPages: number
  completedPages: number
  failedPages: number
  status: 'pending' | 'processing' | 'completed' | 'cancelled'
  createdAt: string
  updatedAt: string
}
