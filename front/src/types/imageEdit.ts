export type EditorTool = 'pointer' | 'rect' | 'ellipse' | 'eraser'

export interface RegionBox {
  x: number
  y: number
  width: number
  height: number
  type: 'rect' | 'ellipse'
}

export interface TextRegion {
  id: string
  box: RegionBox
  originalText: string
  translatedText: string
  fontFamily: string
  fontSize: number
  fontColor: string
  fontWeight: 'normal' | 'bold'
  fontStyle: 'normal' | 'italic'
  textDirection: 'horizontal' | 'vertical'
  lineHeight: number
}

export interface InpaintRequest {
  pageId: number
  regions: RegionBox[]
}

export interface OcrRegionRequest {
  pageId: number
  region: RegionBox
}

export interface OcrRegionResult {
  text: string
  confidence: number
}

export interface EditorHistoryState {
  canvasJson: string
  regions: TextRegion[]
}
