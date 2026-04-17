import { ref, shallowRef, computed, type Ref } from 'vue'
import { Canvas, Rect, Ellipse, FabricImage, Textbox, PencilBrush, type FabricObject } from 'fabric'
import type { TPointerEventInfo, TPointerEvent } from 'fabric'
import { inpaintRegions, inpaintAll, restoreRegions, saveEditedImage } from '@/api/imageEdit'
import { ElMessage } from 'element-plus'
import type { EditorTool, TextRegion, RegionBox } from '@/types/imageEdit'

let regionIdCounter = 0
function genRegionId() {
  return `region_${++regionIdCounter}`
}

export function useImageEditor(canvasElRef: Ref<HTMLCanvasElement | null>) {
  const fabricCanvas = shallowRef<Canvas | null>(null)
  const activeTool = ref<EditorTool>('pointer')
  const regions = ref<TextRegion[]>([])
  const selectedRegionId = ref<string | null>(null)
  const isDrawing = ref(false)
  const loading = ref(false)
  const eraserSize = ref(20)
  const eraserColor = ref('#FFFFFF')

  interface HistoryEntry {
    canvasJson: string
    regions: TextRegion[]
    objectDataList: (Record<string, unknown> | undefined)[]
  }

  const historyStack = ref<HistoryEntry[]>([])
  const historyIndex = ref(-1)

  let imageWidth = 0
  let imageHeight = 0
  let canvasScale = 1
  let pageId = 0

  let drawStartX = 0
  let drawStartY = 0
  let drawingObject: Rect | Ellipse | null = null
  let isExporting = false

  const selectedRegion = computed(() =>
    regions.value.find(r => r.id === selectedRegionId.value) ?? null,
  )

  const canUndo = computed(() => historyIndex.value > 0)
  const canRedo = computed(() => historyIndex.value < historyStack.value.length - 1)

  // ---- Canvas 初始化 ----

  async function initCanvas(imageUrl: string, currentPageId: number, containerEl: HTMLElement) {
    pageId = currentPageId

    if (fabricCanvas.value) {
      fabricCanvas.value.dispose()
    }

    const img = await loadImage(imageUrl)
    imageWidth = img.naturalWidth
    imageHeight = img.naturalHeight

    const containerW = containerEl.clientWidth
    const containerH = containerEl.clientHeight
    canvasScale = Math.min(containerW / imageWidth, containerH / imageHeight, 1)

    const displayW = Math.round(imageWidth * canvasScale)
    const displayH = Math.round(imageHeight * canvasScale)

    const canvasEl = canvasElRef.value
    if (!canvasEl) return

    canvasEl.width = displayW
    canvasEl.height = displayH

    const fc = new Canvas(canvasEl, {
      width: displayW,
      height: displayH,
      selection: false,
      preserveObjectStacking: true,
    })

    const fabricImg = await FabricImage.fromURL(imageUrl, { crossOrigin: 'anonymous' })
    fabricImg.scaleToWidth(displayW)
    fabricImg.set({
      originX: 'left',
      originY: 'top',
      left: 0,
      top: 0,
      selectable: false,
      evented: false,
      erasable: false,
    })
    fc.add(fabricImg)
    fc.sendObjectToBack(fabricImg)

    fc.on('mouse:down', handleMouseDown)
    fc.on('mouse:move', handleMouseMove)
    fc.on('mouse:up', handleMouseUp)
    fc.on('selection:created', handleSelection)
    fc.on('selection:updated', handleSelection)
    fc.on('selection:cleared', handleSelectionCleared)
    fc.on('object:moving', handleObjectMoving)
    fc.on('object:modified', handleObjectModified)
    fc.on('path:created', handlePathCreated)

    fabricCanvas.value = fc
    regions.value = []
    selectedRegionId.value = null
    historyStack.value = []
    historyIndex.value = -1
    pushHistory()
  }

  function dispose() {
    if (fabricCanvas.value) {
      fabricCanvas.value.dispose()
      fabricCanvas.value = null
    }
    regions.value = []
    selectedRegionId.value = null
  }

  // ---- 绘图事件 ----

  function handleMouseDown(opt: TPointerEventInfo<TPointerEvent>) {
    const fc = fabricCanvas.value
    if (!fc || activeTool.value === 'pointer' || activeTool.value === 'eraser') return

    fc.discardActiveObject()

    const pointer = fc.getScenePoint(opt.e)
    drawStartX = pointer.x
    drawStartY = pointer.y
    isDrawing.value = true

    const commonOpts = {
      originX: 'left' as const,
      originY: 'top' as const,
      left: pointer.x,
      top: pointer.y,
      width: 0,
      height: 0,
      fill: 'rgba(64, 158, 255, 0.15)',
      stroke: '#409eff',
      strokeWidth: 2,
      strokeDashArray: [6, 3],
      selectable: false,
      evented: false,
    }

    if (activeTool.value === 'rect') {
      drawingObject = new Rect(commonOpts)
    } else {
      drawingObject = new Ellipse({
        ...commonOpts,
        rx: 0,
        ry: 0,
      })
    }
    fc.add(drawingObject)
  }

  function handleMouseMove(opt: TPointerEventInfo<TPointerEvent>) {
    if (!isDrawing.value || !drawingObject || !fabricCanvas.value) return

    const pointer = fabricCanvas.value.getScenePoint(opt.e)
    const left = Math.min(drawStartX, pointer.x)
    const top = Math.min(drawStartY, pointer.y)
    const w = Math.abs(pointer.x - drawStartX)
    const h = Math.abs(pointer.y - drawStartY)

    drawingObject.set({ left, top, width: w, height: h })

    if (drawingObject instanceof Ellipse) {
      drawingObject.set({ rx: w / 2, ry: h / 2 })
    }

    fabricCanvas.value.requestRenderAll()
  }

  function handleMouseUp() {
    if (!isDrawing.value || !drawingObject || !fabricCanvas.value) return
    isDrawing.value = false

    const fc = fabricCanvas.value
    const w = drawingObject.width ?? 0
    const h = drawingObject.height ?? 0

    if (w < 10 || h < 10) {
      fc.remove(drawingObject)
      drawingObject = null
      return
    }

    const regionId = genRegionId()
    const shapeType = activeTool.value === 'rect' ? 'rect' : 'ellipse'
    drawingObject.set({
      selectable: true,
      evented: true,
      data: { regionId },
    })
    drawingObject.setCoords()

    const box: RegionBox = {
      x: Math.round((drawingObject.left ?? 0) / canvasScale),
      y: Math.round((drawingObject.top ?? 0) / canvasScale),
      width: Math.round(w / canvasScale),
      height: Math.round(h / canvasScale),
      type: shapeType,
    }

    const region: TextRegion = {
      id: regionId,
      box,
      originalText: '',
      translatedText: '',
      fontFamily: 'Noto Sans SC, sans-serif',
      fontSize: 45,
      fontColor: '#000000',
      fontWeight: 'normal',
      fontStyle: 'normal',
      textDirection: 'horizontal',
      lineHeight: 1.0,
    }

    regions.value.push(region)
    selectedRegionId.value = regionId

    const drawnObj = drawingObject
    drawingObject = null
    activeTool.value = 'pointer'
    syncCanvasInteractivity()

    fc.setActiveObject(drawnObj)
    fc.requestRenderAll()
    pushHistory()
  }

  function handleSelection() {
    const fc = fabricCanvas.value
    if (!fc) return
    const active = fc.getActiveObject()
    if (active?.data?.regionId) {
      selectedRegionId.value = active.data.regionId
    }
  }

  function handleSelectionCleared() {
    if (isExporting) return
    if (activeTool.value !== 'pointer') return
    selectedRegionId.value = null
  }

  function handleObjectMoving(opt: { target: FabricObject }) {
    const obj = opt.target
    if (!obj?.data?.regionId) return
    const fc = fabricCanvas.value
    if (!fc) return

    const regionId = obj.data.regionId as string
    const textObj = fc.getObjects().find(o => o.data?.textForRegion === regionId)
    if (!textObj) return

    const w = (obj.width ?? 0) * (obj.scaleX ?? 1)
    const h = (obj.height ?? 0) * (obj.scaleY ?? 1)
    textObj.set({
      left: (obj.left ?? 0) + w / 2,
      top: (obj.top ?? 0) + h / 2,
      width: w,
    })
    fc.requestRenderAll()
  }

  function handleObjectModified(opt: { target: FabricObject }) {
    const obj = opt.target
    if (!obj?.data?.regionId) return

    const regionId = obj.data.regionId as string
    const region = regions.value.find(r => r.id === regionId)
    if (!region) return

    const newW = (obj.width ?? 0) * (obj.scaleX ?? 1)
    const newH = (obj.height ?? 0) * (obj.scaleY ?? 1)

    region.box.x = Math.round((obj.left ?? 0) / canvasScale)
    region.box.y = Math.round((obj.top ?? 0) / canvasScale)
    region.box.width = Math.round(newW / canvasScale)
    region.box.height = Math.round(newH / canvasScale)

    obj.set({ scaleX: 1, scaleY: 1, width: newW, height: newH })
    if (obj instanceof Ellipse) {
      obj.set({ rx: newW / 2, ry: newH / 2 })
    }
    obj.setCoords()

    syncRegionToCanvas(regionId)
    pushHistory()
  }

  function handlePathCreated(opt: { path: FabricObject }) {
    if (activeTool.value === 'eraser' && opt.path) {
      opt.path.set({
        selectable: false,
        evented: false,
        data: { eraserPath: true },
      })
      pushHistory()
    }
  }

  // ---- 橡皮擦控制 ----

  function buildEraserCursor(displaySize: number): string {
    const size = Math.max(6, Math.min(Math.round(displaySize), 128))
    const half = size / 2
    const r = half - 1
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">`
      + `<circle cx="${half}" cy="${half}" r="${r}" fill="none" stroke="black" stroke-width="1" opacity="0.5"/>`
      + `<circle cx="${half}" cy="${half}" r="${r}" fill="none" stroke="white" stroke-width="1" opacity="0.8" stroke-dasharray="3 3"/>`
      + `<circle cx="${half}" cy="${half}" r="1.5" fill="black" opacity="0.5"/>`
      + `</svg>`
    return `url("data:image/svg+xml,${encodeURIComponent(svg)}") ${half} ${half}, crosshair`
  }

  function setupEraserBrush(fc: Canvas) {
    const brush = new PencilBrush(fc)
    brush.color = eraserColor.value
    brush.width = eraserSize.value * canvasScale
    fc.freeDrawingBrush = brush
    fc.freeDrawingCursor = buildEraserCursor(eraserSize.value * canvasScale)
  }

  function setEraserSize(size: number) {
    eraserSize.value = size
    const fc = fabricCanvas.value
    if (fc?.freeDrawingBrush) {
      fc.freeDrawingBrush.width = size * canvasScale
      fc.freeDrawingCursor = buildEraserCursor(size * canvasScale)
    }
  }

  function setEraserColor(color: string) {
    eraserColor.value = color
    const fc = fabricCanvas.value
    if (fc?.freeDrawingBrush) {
      (fc.freeDrawingBrush as PencilBrush).color = color
    }
  }

  // ---- 区域操作 ----

  function deleteRegion(regionId: string) {
    const fc = fabricCanvas.value
    if (!fc) return

    const obj = fc.getObjects().find(o => o.data?.regionId === regionId)
    if (obj) fc.remove(obj)

    const textObj = fc.getObjects().find(o => o.data?.textForRegion === regionId)
    if (textObj) fc.remove(textObj)

    regions.value = regions.value.filter(r => r.id !== regionId)
    if (selectedRegionId.value === regionId) {
      selectedRegionId.value = null
    }
    fc.discardActiveObject()
    fc.requestRenderAll()
    pushHistory()
  }

  function deleteSelectedRegion() {
    if (selectedRegionId.value) {
      deleteRegion(selectedRegionId.value)
    }
  }

  // ---- 导出当前画布背景（不含区域框和文本叠层） ----

  async function exportCurrentBackground(): Promise<Blob> {
    const fc = fabricCanvas.value
    if (!fc) throw new Error('Canvas not initialized')

    isExporting = true
    const prevSelectedId = selectedRegionId.value

    fc.discardActiveObject()

    const objects = fc.getObjects()
    const overlays = objects.filter(o => o.data?.regionId || o.data?.textForRegion)
    overlays.forEach(o => o.set({ visible: false }))
    fc.requestRenderAll()

    const multiplier = 1 / canvasScale
    const dataUrl = fc.toDataURL({ format: 'png', quality: 1, multiplier })

    overlays.forEach(o => o.set({ visible: true }))

    if (prevSelectedId) {
      const obj = fc.getObjects().find(o => o.data?.regionId === prevSelectedId)
      if (obj) fc.setActiveObject(obj)
    }
    fc.requestRenderAll()
    isExporting = false

    const res = await fetch(dataUrl)
    return res.blob()
  }

  // ---- 抠字 ----

  async function inpaintDrawnRegions() {
    if (regions.value.length === 0) {
      ElMessage.warning('请先框选区域')
      return
    }
    loading.value = true
    try {
      const bgBlob = await exportCurrentBackground()
      const boxes = regions.value.map(r => r.box)
      const blob = await inpaintRegions(bgBlob, boxes) as unknown as Blob
      await replaceBackgroundImage(blob)
      ElMessage.success('框选区域文字已清除')
      pushHistory()
    } catch {
      // handled by interceptor
    } finally {
      loading.value = false
    }
  }

  async function inpaintAllRegions() {
    loading.value = true
    try {
      const bgBlob = await exportCurrentBackground()
      const blob = await inpaintAll(bgBlob) as unknown as Blob
      await replaceBackgroundImage(blob)

      // 全部抠字完成后清除所有区域框
      const fc = fabricCanvas.value
      if (fc) {
        const regionObjs = fc.getObjects().filter(o => o.data?.regionId || o.data?.textForRegion)
        regionObjs.forEach(o => fc.remove(o))
        fc.discardActiveObject()
        fc.requestRenderAll()
      }
      regions.value = []
      selectedRegionId.value = null

      ElMessage.success('全部文字已清除')
      pushHistory()
    } catch {
      // handled by interceptor
    } finally {
      loading.value = false
    }
  }

  async function restoreDrawnRegions() {
    if (regions.value.length === 0) {
      ElMessage.warning('请先框选要复原的区域')
      return
    }
    loading.value = true
    try {
      const bgBlob = await exportCurrentBackground()
      const boxes = regions.value.map(r => r.box)
      const blob = await restoreRegions(bgBlob, pageId, boxes) as unknown as Blob
      await replaceBackgroundImage(blob)
      ElMessage.success('区域已从原图复原')
      pushHistory()
    } catch {
      // handled by interceptor
    } finally {
      loading.value = false
    }
  }

  async function replaceBackgroundImage(blob: Blob) {
    const fc = fabricCanvas.value
    if (!fc) return

    const url = URL.createObjectURL(blob)
    const newImg = await FabricImage.fromURL(url, { crossOrigin: 'anonymous' })
    newImg.scaleToWidth(fc.width!)
    newImg.set({
      originX: 'left',
      originY: 'top',
      left: 0,
      top: 0,
      selectable: false,
      evented: false,
      erasable: false,
    })

    const objects = fc.getObjects()
    const oldBg = objects[0]
    if (oldBg && oldBg instanceof FabricImage) {
      fc.remove(oldBg)
    }
    fc.add(newImg)
    fc.sendObjectToBack(newImg)
    fc.requestRenderAll()
    URL.revokeObjectURL(url)
  }

  // ---- 文本渲染（实时同步） ----

  function buildVerticalText(
    text: string,
    boxHeightPx: number,
    fontSizePx: number,
    lineHeight: number,
  ): string {
    const chars = [...text]
    if (chars.length === 0) return ''

    const charHeight = fontSizePx * lineHeight
    const charsPerCol = Math.max(1, Math.floor(boxHeightPx / charHeight))

    if (chars.length <= charsPerCol) {
      return chars.join('\n')
    }

    const numCols = Math.ceil(chars.length / charsPerCol)
    const columns: string[][] = []

    for (let c = 0; c < numCols; c++) {
      const colChars = chars.slice(c * charsPerCol, (c + 1) * charsPerCol)
      while (colChars.length < charsPerCol) {
        colChars.push('\u3000')
      }
      columns.push(colChars)
    }

    const rows: string[] = []
    for (let r = 0; r < charsPerCol; r++) {
      let row = ''
      for (let c = numCols - 1; c >= 0; c--) {
        row += columns[c]![r]
      }
      rows.push(row)
    }
    return rows.join('\n')
  }

  function syncRegionToCanvas(regionId: string) {
    const fc = fabricCanvas.value
    if (!fc) return

    const region = regions.value.find(r => r.id === regionId)
    if (!region) return

    const existingText = fc.getObjects().find(o => o.data?.textForRegion === regionId) as Textbox | undefined

    if (!region.translatedText.trim()) {
      if (existingText) {
        fc.remove(existingText)
        fc.requestRenderAll()
      }
      return
    }

    const box = region.box
    const scaledW = box.width * canvasScale
    const scaledH = box.height * canvasScale
    const centerX = box.x * canvasScale + scaledW / 2
    const centerY = box.y * canvasScale + scaledH / 2
    const isVertical = region.textDirection === 'vertical'
    const vLineHeight = 1.1
    const fontSizePx = region.fontSize * canvasScale
    const displayText = isVertical
      ? buildVerticalText(region.translatedText, scaledH, fontSizePx, vLineHeight)
      : region.translatedText

    const commonProps = {
      text: displayText,
      left: centerX,
      top: centerY,
      width: scaledW,
      fontSize: fontSizePx,
      fontFamily: region.fontFamily,
      fill: region.fontColor,
      fontWeight: region.fontWeight,
      fontStyle: region.fontStyle,
      lineHeight: isVertical ? vLineHeight : region.lineHeight,
      textAlign: 'center' as const,
      splitByGrapheme: !isVertical,
    }

    if (existingText) {
      existingText.set(commonProps)
      fc.requestRenderAll()
    } else {
      const textbox = new Textbox(displayText, {
        ...commonProps,
        originX: 'center',
        originY: 'center',
        editable: false,
        selectable: false,
        evented: false,
        data: { textForRegion: regionId },
      })
      fc.add(textbox)
      fc.requestRenderAll()
    }
  }

  const textSyncKeys: (keyof TextRegion)[] = [
    'translatedText', 'fontFamily', 'fontSize', 'fontColor',
    'fontWeight', 'fontStyle', 'textDirection', 'lineHeight',
  ]

  function updateRegionProperty<K extends keyof TextRegion>(
    regionId: string,
    key: K,
    value: TextRegion[K],
  ) {
    const region = regions.value.find(r => r.id === regionId)
    if (region) {
      region[key] = value
      if (textSyncKeys.includes(key)) {
        syncRegionToCanvas(regionId)
      }
    }
  }

  // ---- 导出与保存 ----

  function exportToDataUrl(): string {
    const fc = fabricCanvas.value
    if (!fc) return ''

    isExporting = true
    const prevSelectedId = selectedRegionId.value

    fc.discardActiveObject()

    const objects = fc.getObjects()
    const regionShapes = objects.filter(o => o.data?.regionId && !o.data?.textForRegion)
    regionShapes.forEach(o => o.set({ visible: false }))

    fc.requestRenderAll()

    const multiplier = 1 / canvasScale
    const dataUrl = fc.toDataURL({
      format: 'png',
      quality: 1,
      multiplier,
    })

    regionShapes.forEach(o => o.set({ visible: true }))

    if (prevSelectedId) {
      const obj = fc.getObjects().find(o => o.data?.regionId === prevSelectedId)
      if (obj) fc.setActiveObject(obj)
    }
    fc.requestRenderAll()
    isExporting = false

    return dataUrl
  }

  async function saveImage() {
    const fc = fabricCanvas.value
    if (!fc) return

    loading.value = true
    try {
      const dataUrl = exportToDataUrl()
      if (!dataUrl) return

      const response = await fetch(dataUrl)
      const blob = await response.blob()
      await saveEditedImage(pageId, blob)

      await replaceBackgroundImage(blob)

      const regionObjs = fc.getObjects().filter(o => o.data?.regionId || o.data?.textForRegion)
      regionObjs.forEach(o => fc.remove(o))
      fc.discardActiveObject()
      fc.requestRenderAll()

      regions.value = []
      selectedRegionId.value = null

      pushHistory()
      ElMessage.success('已保存并保存所有文本')
    } catch {
      ElMessage.error('保存失败')
    } finally {
      loading.value = false
    }
  }

  async function flattenSelectedRegion() {
    const fc = fabricCanvas.value
    if (!fc) return
    const region = selectedRegion.value
    if (!region) {
      ElMessage.warning('请先选择一个区域')
      return
    }

    loading.value = true
    try {
      isExporting = true
      fc.discardActiveObject()

      const objects = fc.getObjects()
      const allShapes = objects.filter(o => o.data?.regionId && !o.data?.textForRegion)
      allShapes.forEach(o => o.set({ visible: false }))
      const otherTexts = objects.filter(
        o => o.data?.textForRegion && o.data.textForRegion !== region.id,
      )
      otherTexts.forEach(o => o.set({ visible: false }))
      fc.requestRenderAll()

      const multiplier = 1 / canvasScale
      const dataUrl = fc.toDataURL({ format: 'png', quality: 1, multiplier })

      allShapes.forEach(o => o.set({ visible: true }))
      otherTexts.forEach(o => o.set({ visible: true }))
      fc.requestRenderAll()
      isExporting = false

      const response = await fetch(dataUrl)
      const blob = await response.blob()
      await replaceBackgroundImage(blob)

      const regionObj = fc.getObjects().find(
        o => o.data?.regionId === region.id && !o.data?.textForRegion,
      )
      if (regionObj) fc.remove(regionObj)
      const textObj = fc.getObjects().find(o => o.data?.textForRegion === region.id)
      if (textObj) fc.remove(textObj)

      regions.value = regions.value.filter(r => r.id !== region.id)
      selectedRegionId.value = null
      fc.requestRenderAll()

      pushHistory()
      ElMessage.success('区域文本已保存到图片')
    } catch {
      ElMessage.error('操作失败')
    } finally {
      loading.value = false
    }
  }

  // ---- 撤销/重做 ----

  function pushHistory() {
    const fc = fabricCanvas.value
    if (!fc) return
    const canvasJson = JSON.stringify(fc.toJSON(['data']))
    const regionsSnapshot = JSON.parse(JSON.stringify(regions.value)) as TextRegion[]
    const objectDataList = fc.getObjects().map(o =>
      o.data ? JSON.parse(JSON.stringify(o.data)) : undefined,
    )
    if (historyIndex.value < historyStack.value.length - 1) {
      historyStack.value = historyStack.value.slice(0, historyIndex.value + 1)
    }
    historyStack.value.push({ canvasJson, regions: regionsSnapshot, objectDataList })
    historyIndex.value = historyStack.value.length - 1
  }

  async function undo() {
    if (!canUndo.value || !fabricCanvas.value) return
    historyIndex.value--
    await restoreFromHistory()
  }

  async function redo() {
    if (!canRedo.value || !fabricCanvas.value) return
    historyIndex.value++
    await restoreFromHistory()
  }

  async function restoreFromHistory() {
    const fc = fabricCanvas.value
    if (!fc) return
    const entry = historyStack.value[historyIndex.value]
    if (!entry) return

    await fc.loadFromJSON(entry.canvasJson)

    const objects = fc.getObjects()
    entry.objectDataList.forEach((data, i) => {
      if (i < objects.length && data) {
        objects[i].data = { ...data }
      }
    })

    regions.value = JSON.parse(JSON.stringify(entry.regions)) as TextRegion[]
    if (selectedRegionId.value && !regions.value.find(r => r.id === selectedRegionId.value)) {
      selectedRegionId.value = null
    }

    fc.getObjects().forEach(obj => {
      obj.setCoords()
      if (obj instanceof FabricImage && !obj.data?.regionId) {
        obj.set({ selectable: false, evented: false })
      }
    })

    if (activeTool.value === 'eraser') {
      setupEraserBrush(fc)
      fc.isDrawingMode = true
    }
    syncCanvasInteractivity()
    fc.requestRenderAll()
  }

  // ---- 工具辅助 ----

  function loadImage(src: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      img.onload = () => resolve(img)
      img.onerror = reject
      img.src = src
    })
  }

  function syncCanvasInteractivity() {
    const fc = fabricCanvas.value
    if (!fc) return
    const isPointer = activeTool.value === 'pointer'
    fc.getObjects().forEach(obj => {
      if (obj instanceof FabricImage && !obj.data?.regionId) return
      if (obj.data?.eraserPath) {
        obj.set({ selectable: false, evented: false })
      } else if (obj.data?.textForRegion) {
        obj.set({ selectable: false, evented: false })
      } else if (obj.data?.regionId) {
        obj.set({ selectable: isPointer, evented: isPointer })
      }
    })
    fc.requestRenderAll()
  }

  function setTool(tool: EditorTool) {
    activeTool.value = tool
    const fc = fabricCanvas.value
    if (!fc) return

    if (tool === 'eraser') {
      fc.discardActiveObject()
      setupEraserBrush(fc)
      fc.isDrawingMode = true
    } else {
      fc.isDrawingMode = false
      if (tool !== 'pointer') {
        fc.discardActiveObject()
      }
    }
    syncCanvasInteractivity()
  }

  return {
    fabricCanvas,
    activeTool,
    regions,
    selectedRegionId,
    selectedRegion,
    isDrawing,
    loading,
    canUndo,
    canRedo,
    eraserSize,
    eraserColor,

    initCanvas,
    dispose,
    setTool,

    deleteRegion,
    deleteSelectedRegion,
    inpaintDrawnRegions,
    inpaintAllRegions,
    restoreDrawnRegions,

    updateRegionProperty,
    setEraserSize,
    setEraserColor,

    exportToDataUrl,
    saveImage,
    flattenSelectedRegion,
    undo,
    redo,
  }
}
