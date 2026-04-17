import { defineStore } from 'pinia'
import { ref } from 'vue'

export type TranslateStatus = 'idle' | 'translating' | 'completed' | 'failed'

export const useTranslateStore = defineStore('translate', () => {
  const status = ref<TranslateStatus>('idle')
  const progress = ref('')
  const errorMessage = ref('')

  function setTranslating() {
    status.value = 'translating'
    progress.value = ''
    errorMessage.value = ''
  }

  function setCompleted() {
    status.value = 'completed'
  }

  function setFailed(msg: string) {
    status.value = 'failed'
    errorMessage.value = msg
  }

  function reset() {
    status.value = 'idle'
    progress.value = ''
    errorMessage.value = ''
  }

  return { status, progress, errorMessage, setTranslating, setCompleted, setFailed, reset }
})
