<script setup lang="ts">
/**
 * 流式正文「洇入」（P1 试点）。
 * 只把新增的片段作为独立墨团渲染并动画，旧片段不重绘；
 * 高频 SSE 片段用 50ms 合并，超长文本或关闭动效时降级为纯文本。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    text: string
    disabled?: boolean
    maxChars?: number
    streaming?: boolean
    minChunkChars?: number
    maxWaitMs?: number
  }>(),
  { disabled: false, maxChars: 3000, streaming: false, minChunkChars: 8, maxWaitMs: 200 },
)

const chunks = ref<string[]>([])
let emittedLength = 0
let timer: ReturnType<typeof setTimeout> | null = null

const plain = computed(() => props.disabled || props.text.length > props.maxChars)

function clearTimer() {
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
}

function flush() {
  clearTimer()
  if (plain.value) {
    chunks.value = []
    emittedLength = 0
    return
  }
  const next = props.text.slice(emittedLength)
  emittedLength = props.text.length
  if (next) chunks.value.push(next)
}

/** 攒够墨量再落笔；太小的增量最多等 maxWaitMs，避免碎片闪烁。 */
function maybeFlush() {
  if (props.text.length - emittedLength >= props.minChunkChars) {
    flush()
    return
  }
  if (timer === null) timer = setTimeout(flush, props.maxWaitMs)
}

watch(
  () => [props.text, props.disabled, props.maxChars] as const,
  () => {
    // REVISION 会先把缓冲清空，再写入整稿：此时重置已渲染片段。
    if (props.text.length < emittedLength) {
      clearTimer()
      chunks.value = []
      emittedLength = 0
    }
    if (plain.value) {
      clearTimer()
      chunks.value = []
      emittedLength = 0
      return
    }
    if (chunks.value.length === 0 && emittedLength === 0) {
      flush()
      return
    }
    maybeFlush()
  },
  { immediate: true },
)

onBeforeUnmount(clearTimer)
</script>

<template>
  <span v-if="plain" class="ink-plain">{{ text }}</span>
  <span v-else class="ink-stream" :aria-busy="streaming"><span v-for="(chunk, i) in chunks" :key="i" class="motion-ink-reveal">{{ chunk }}</span></span>
</template>
