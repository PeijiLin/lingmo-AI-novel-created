<script setup lang="ts">
/**
 * 可搜索下拉 + 自由输入（combobox）
 * 既能从预设项中选择，也能直接输入自定义文本。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, ChevronDown } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

export interface ComboboxOption {
  label: string
  value: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    options: ComboboxOption[]
    placeholder?: string
    disabled?: boolean
    /** 触发器宽度类 */
    widthClass?: string
    /** 输入最大长度 */
    maxlength?: string | number
  }>(),
  { placeholder: '请选择或输入', widthClass: 'w-full' },
)

const model = defineModel<string>({ default: '' })

const open = ref(false)
const query = ref('')
const rootEl = ref<HTMLElement | null>(null)

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter(
    (o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q),
  )
})

const showFree = computed(() => {
  const v = query.value.trim()
  return !!v && !props.options.some((o) => o.value === v)
})

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) query.value = model.value
  else query.value = ''
}

function pick(o: ComboboxOption) {
  if (o.disabled) return
  model.value = o.value
  query.value = o.value
  open.value = false
}

/** 输入即更新模型（自定义自由文本） */
function onInput(e: Event) {
  const v = (e.target as HTMLInputElement).value
  model.value = v
  query.value = v
  open.value = true
}

function onDocumentClick(e: MouseEvent) {
  if (rootEl.value && !rootEl.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <div ref="rootEl" :class="cn('relative', widthClass)">
    <div class="relative">
      <input
        :value="model"
        :placeholder="placeholder"
        :disabled="disabled"
        :maxlength="maxlength"
        type="text"
        class="h-9 w-full cursor-text rounded-md border border-mist-line bg-paper-surface pr-8 text-sm text-ink transition-colors duration-150 ease-[var(--ease-paper)] hover:border-mist focus:border-ink-indigo/50 focus:outline-none focus:ring-2 focus:ring-ink-indigo/20 disabled:cursor-not-allowed disabled:bg-paper disabled:opacity-60"
        @input="onInput"
        @focus="open = true"
        @keydown.esc="open = false"
      />
      <button
        type="button"
        :disabled="disabled"
        class="absolute top-0 right-0 flex h-9 w-8 cursor-pointer items-center justify-center text-ink-secondary transition-colors hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
        @click="toggle"
      >
        <ChevronDown class="size-4" :class="open && 'rotate-180'" />
      </button>
    </div>

    <div
      v-if="open && !disabled"
      class="absolute z-50 mt-1 max-h-52 w-full overflow-auto rounded-md border border-mist-line bg-paper-surface p-1 shadow-card-hover"
    >
      <button
        v-for="opt in filtered"
        :key="opt.value"
        type="button"
        :disabled="opt.disabled"
        :class="cn(
          'relative flex w-full cursor-pointer select-none items-center rounded-sm py-1.5 pr-8 pl-3 text-left text-sm text-ink transition-colors',
          'hover:bg-paper disabled:pointer-events-none disabled:opacity-50',
          model === opt.value && 'text-ink-indigo',
        )"
        @mousedown.prevent="pick(opt)"
      >
        <span class="truncate">{{ opt.label }}</span>
        <span v-if="model === opt.value" class="absolute right-2.5 inline-flex">
          <Check class="size-3.5 text-cinnabar" />
        </span>
      </button>
      <p v-if="filtered.length === 0 && query.trim()" class="px-3 py-2 text-xs text-ink-secondary">
        无匹配，继续输入可作为自定义方向
      </p>
      <p v-if="filtered.length === 0 && !query.trim()" class="px-3 py-2 text-xs text-ink-secondary">
        暂无预设，可直接输入
      </p>
    </div>
  </div>
</template>