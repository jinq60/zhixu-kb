<script setup lang="ts">
/**
 * Lucide 线条图标（ISC 协议，本地 assets/lucide/*.svg）。
 * SVG 使用 stroke="currentColor"，颜色随父级 color 走，可直接套主题色。
 */
import { computed } from 'vue'

const props = defineProps<{
  /** 不带后缀的文件名，如 notebook-pen */
  name: string
}>()

const modules = import.meta.glob('../assets/lucide/*.svg', {
  query: '?raw',
  import: 'default',
  eager: true
}) as Record<string, string>

const svg = computed(() => modules[`../assets/lucide/${props.name}.svg`] ?? '')
</script>

<template>
  <span class="svg-icon" aria-hidden="true" v-html="svg" />
</template>

<style scoped>
.svg-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 0;
}

.svg-icon :deep(svg) {
  width: 1em;
  height: 1em;
}
</style>
