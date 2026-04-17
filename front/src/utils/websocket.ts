import { ref, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'

export interface WsRecordStatus {
  type: 'RECORD_STATUS'
  recordId: number
  pageId: number
  status: string
  errorMessage: string
}

export interface WsTaskProgress {
  type: 'TASK_PROGRESS'
  taskId: number
  status: string
  completedPages: number
  failedPages: number
  totalPages: number
}

export type WsMessage = WsRecordStatus | WsTaskProgress

type MessageHandler = (msg: WsMessage) => void

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 10
const handlers = new Set<MessageHandler>()
const connected = ref(false)

function getWsUrl(): string {
  const userStore = useUserStore()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = location.host
  return `${protocol}//${host}/api/ws/translate?token=${userStore.token}`
}

function doConnect() {
  if (ws && (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)) {
    return
  }

  const userStore = useUserStore()
  if (!userStore.token) return

  ws = new WebSocket(getWsUrl())

  ws.onopen = () => {
    connected.value = true
    reconnectAttempts = 0
  }

  ws.onmessage = (event) => {
    try {
      const msg: WsMessage = JSON.parse(event.data)
      handlers.forEach((h) => h(msg))
    } catch {
      // ignore malformed messages
    }
  }

  ws.onclose = () => {
    connected.value = false
    ws = null
    scheduleReconnect()
  }

  ws.onerror = () => {
    ws?.close()
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return

  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
  reconnectAttempts++
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    doConnect()
  }, delay)
}

export function connectWs() {
  doConnect()
}

export function disconnectWs() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  reconnectAttempts = MAX_RECONNECT_ATTEMPTS
  if (ws) {
    ws.close()
    ws = null
  }
  connected.value = false
}

export function onWsMessage(handler: MessageHandler) {
  handlers.add(handler)
}

export function offWsMessage(handler: MessageHandler) {
  handlers.delete(handler)
}

export function useWebSocket(handler: MessageHandler) {
  connectWs()
  onWsMessage(handler)

  onUnmounted(() => {
    offWsMessage(handler)
  })

  return { connected }
}
