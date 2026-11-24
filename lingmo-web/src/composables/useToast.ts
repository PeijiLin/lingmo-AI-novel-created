import { ref } from 'vue'

export type ToastVariant = 'default' | 'success' | 'error' | 'warning'

export interface ToastItem {
  id: number
  title: string
  description?: string
  variant: ToastVariant
  duration: number
}

const toasts = ref<ToastItem[]>([])
let seq = 0

function dismiss(id: number) {
  const idx = toasts.value.findIndex((t) => t.id === id)
  if (idx !== -1) toasts.value.splice(idx, 1)
}

function push(title: string, options?: { description?: string; variant?: ToastVariant; duration?: number }) {
  const id = ++seq
  const item: ToastItem = {
    id,
    title,
    description: options?.description,
    variant: options?.variant ?? 'default',
    duration: options?.duration ?? 3200,
  }
  toasts.value.push(item)
  if (item.duration > 0) setTimeout(() => dismiss(id), item.duration)
  return id
}

/** 组件内轻提示（纸墨自研，替代 vue-sonner） */
export function useToast() {
  return {
    toasts,
    dismiss,
    toast: push,
    success: (title: string, description?: string) => push(title, { description, variant: 'success' }),
    error: (title: string, description?: string) => push(title, { description, variant: 'error', duration: 5000 }),
    warning: (title: string, description?: string) => push(title, { description, variant: 'warning', duration: 4200 }),
  }
}

/** 非组件上下文使用的具名导出（api 层降级提示等） */
export const toast = {
  info: (title: string, description?: string) => push(title, { description }),
  success: (title: string, description?: string) => push(title, { description, variant: 'success' }),
  error: (title: string, description?: string) => push(title, { description, variant: 'error', duration: 5000 }),
  warning: (title: string, description?: string) => push(title, { description, variant: 'warning', duration: 4200 }),
}
