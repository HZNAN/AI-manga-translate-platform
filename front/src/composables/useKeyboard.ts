import { onMounted, onUnmounted } from 'vue'

type KeyHandler = (e: KeyboardEvent) => void

interface KeyMap {
  [key: string]: KeyHandler
}

export function useKeyboard(keyMap: KeyMap) {
  function handler(e: KeyboardEvent) {
    const tag = (e.target as HTMLElement)?.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return

    const key = e.key
    if (keyMap[key]) {
      e.preventDefault()
      keyMap[key](e)
    }
  }

  onMounted(() => window.addEventListener('keydown', handler))
  onUnmounted(() => window.removeEventListener('keydown', handler))
}
