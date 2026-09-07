<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getProductBySlug, type ProductIcon } from '../config/products'
import {
  ArrowRight,
  Download,
  Check,
  Notebook,
  Monitor,
  SetUp,
  DataLine,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const slug = computed(() => route.params.slug as string)
const product = computed(() => getProductBySlug(slug.value))

// 图标映射（与 products.ts ProductIcon 联合类型保持一致）
const iconMap: Record<ProductIcon, any> = { Notebook, Monitor, SetUp, DataLine }
const resolveIcon = (name: string) => {
  const hit = (iconMap as Record<string, any>)[name]
  if (!hit && import.meta.env.DEV) console.warn(`[products] unknown icon: ${name}`)
  return hit || Notebook
}
const enter = () => {
  if (!product.value) return
  if (product.value.status === 'live') {
    if (auth.isLoggedIn) router.push(product.value.ctaPath)
    else auth.openLoginModal()
  } else {
    // 未上线产品去下载中心预约，而非弹登录丢失意图
    router.push('/home#download')
  }
}
</script>

<template>
  <div v-if="product" class="product-detail">
    <!-- Hero: 复用 Home 的强视觉，但完全由配置驱动 -->
    <section class="pd-hero">
      <div class="pd-hero-inner">
        <div class="pd-hero-left">
          <div class="eyebrow"><span class="dot" /> {{ product.tagline }} · {{ product.shortName }}</div>
          <h1>
            <span class="t-serif">{{ product.name }}</span>
            <span class="pd-tagline">{{ product.tagline }}</span>
          </h1>
          <p class="pd-desc">{{ product.longDesc }}</p>
          <div class="pd-actions">
            <button class="btn-primary" :style="{ background: product.color }" @click="enter">
              {{ product.ctaLabel }} <el-icon><ArrowRight /></el-icon>
            </button>
            <button class="btn-ghost" @click="router.push('/home#download')">
              <el-icon><Download /></el-icon> 查看下载
            </button>
          </div>
          <div class="pd-tags">
            <span v-for="t in product.tags" :key="t" class="pd-tag">{{ t }}</span>
          </div>
          <div v-if="product.stats?.length" class="pd-stats">
            <div v-for="s in product.stats" :key="s.label" class="pd-stat">
              <strong>{{ s.value }}</strong><span>{{ s.label }}</span>
            </div>
          </div>
        </div>
        <div class="pd-visual">
          <!-- 根据 heroVisual 切换视觉模板 -->
          <div v-if="product.heroVisual === 'kb'" class="pd-mock kb">
            <div class="mock-bar"><span class="traffic"><i/><i/><i/></span> {{ product.shortName }} · 知识库</div>
            <div class="mock-body">
              <aside class="mock-nav"><div class="nav-item active" /><div class="nav-item" /><div class="nav-item" /></aside>
              <div class="mock-main">
                <div class="mock-card"><div class="line w60" /><div class="line w90" /><div class="line w80" /></div>
                <div class="mock-card"><div class="line w50" /><div class="line w70" /></div>
              </div>
            </div>
          </div>
          <div v-else-if="product.heroVisual === 'ocr'" class="pd-mock ocr">
            <div class="mock-bar"><span class="traffic"><i/><i/><i/></span> {{ product.shortName }} · 批量识别</div>
            <div class="ocr-drop">
              <el-icon><SetUp /></el-icon><span>拖入 128 张图片</span><em>本地 OCR 中…</em>
            </div>
            <div class="ocr-list">
              <div class="ocr-item"><span>发票_001.jpg</span><em class="done">完成</em></div>
              <div class="ocr-item"><span>合同.pdf</span><em>95%</em></div>
              <div class="ocr-item"><span>笔记.jpg</span><em>OCR</em></div>
            </div>
          </div>
          <div v-else class="pd-mock generic" :style="{ borderColor: product.color }">
            <div class="mock-bar"><span class="traffic"><i/><i/><i/></span> {{ product.shortName }}</div>
            <div class="generic-body">
              <el-icon :size="36" :color="product.color"><component :is="resolveIcon(product.icon)" /></el-icon>
              <p>{{ product.description }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Features: 完全由 product.features 驱动，交替布局 -->
    <section
      v-for="(f, i) in product.features"
      :key="f.num"
      :class="['feature', i % 2 === 1 ? 'alt' : '']"
    >
      <div :class="['feature-inner', i % 2 === 1 ? 'reverse' : '']">
        <div class="feature-text">
          <div class="feature-num">{{ f.num }}</div>
          <div class="eyebrow">{{ f.eyebrow }}</div>
          <h2>{{ f.title }}<br /><span class="t-serif" :style="{ color: product.color }">{{ f.titleAccent }}</span></h2>
          <p>{{ f.desc }}</p>
          <ul class="feature-list">
            <li v-for="b in f.bullets" :key="b"><el-icon><Check /></el-icon> {{ b }}</li>
          </ul>
        </div>
        <div class="feature-visual">
          <!-- 占位视觉，按 eyebrow 区分 -->
          <div class="fv-card">
            <div class="fv-head" :style="{ background: product.gradient }">{{ f.eyebrow }}</div>
            <div class="fv-body">
              <div v-for="b in f.bullets" :key="b" class="fv-item">{{ b }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="pd-cta">
      <h2>体验 {{ product.name }}</h2>
      <p>{{ product.longDesc }}</p>
      <button class="btn-primary" :style="{ background: product.color }" @click="enter">
        {{ product.ctaLabel }} <el-icon><ArrowRight /></el-icon>
      </button>
    </section>
  </div>
  <div v-else class="not-found">
    <h2>产品未找到</h2>
    <p>Slug: {{ slug }}</p>
    <el-button type="primary" @click="router.push('/home')">返回首页</el-button>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@500;700;800&family=Noto+Serif+SC:wght@800&display=swap');
.product-detail{--ink:#0B1220;--hair:#E2E8F0;--cloud:#F8FAFC;background:#fff;color:var(--ink);font-family:'Inter','PingFang SC',sans-serif}
.eyebrow{font:500 12px/1 'JetBrains Mono',monospace;letter-spacing:.12em;text-transform:uppercase;color:#2563EB;display:flex;gap:8px;align-items:center}
.dot{width:7px;height:7px;border-radius:50%;background:#2563EB;box-shadow:0 0 0 6px rgba(37,99,235,.12);display:inline-block}
.t-serif{font-family:'Noto Serif SC',serif;font-weight:800}
.pd-hero{padding:120px 48px 80px;background:linear-gradient(180deg,#F8FAFC 0%,#fff 60%);border-bottom:1px solid var(--hair)}
.pd-hero-inner{max-width:1280px;margin:0 auto;display:flex;gap:48px;align-items:center}
.pd-hero-left{flex:1}
.pd-hero-left h1{font-size:56px;line-height:.95;letter-spacing:-.03em;margin:12px 0 16px}
.pd-tagline{display:block;font-size:22px;font-weight:700;color:#475569;margin-top:8px;letter-spacing:-.01em}
.pd-desc{font-size:17px;line-height:1.8;color:#475569}
.pd-actions{margin-top:24px;display:flex;gap:12px;flex-wrap:wrap}
.btn-primary{color:#fff;border:none;padding:13px 22px;border-radius:999px;font-weight:700;display:inline-flex;gap:8px;align-items:center;cursor:pointer}
.btn-ghost{background:#fff;border:1px solid var(--hair);padding:13px 22px;border-radius:999px;font-weight:700;display:inline-flex;gap:8px;align-items:center;cursor:pointer}
.pd-tags{margin-top:16px;display:flex;gap:8px;flex-wrap:wrap}
.pd-tag{font:700 11px/1 Inter;background:#EEF2FF;color:#2563EB;padding:6px 10px;border-radius:999px}
.pd-stats{margin-top:18px;display:flex;gap:24px}
.pd-stat{display:flex;flex-direction:column;gap:4px}
.pd-stat strong{font:800 22px/1 Inter}
.pd-stat span{font:500 12px/1 Inter;color:#64748B}
.pd-visual{width:520px;flex-shrink:0}
.pd-mock{background:#fff;border:1px solid var(--hair);border-radius:16px;overflow:hidden;box-shadow:0 20px 50px rgba(15,23,42,.10)}
.mock-bar{padding:12px 16px;background:#F8FAFC;border-bottom:1px solid var(--hair);display:flex;gap:10px;align-items:center;font:600 12px/1 Inter;color:#64748B}
.traffic{display:flex;gap:6px}
.traffic i{width:10px;height:10px;border-radius:50%;background:#E2E8F0;display:block}
.mock-body{display:flex;min-height:260px}
.mock-nav{width:120px;background:#F8FAFC;border-right:1px solid var(--hair);padding:14px;display:flex;flex-direction:column;gap:10px}
.nav-item{height:22px;border-radius:8px;background:#E2E8F0}
.nav-item.active{background:#2563EB}
.mock-main{flex:1;padding:16px;display:flex;flex-direction:column;gap:12px}
.mock-card{background:#F8FAFC;border:1px solid var(--hair);border-radius:12px;padding:14px;display:flex;flex-direction:column;gap:8px}
.line{height:10px;border-radius:6px;background:#E2E8F0}
.line.w60{width:60%}.line.w90{width:90%}.line.w80{width:80%}.line.w50{width:50%}.line.w70{width:70%}
.ocr-drop{margin:16px;background:#F8FAFC;border:1px dashed #CBD5E1;border-radius:12px;padding:20px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:6px;color:#475569}
.ocr-list{padding:0 16px 16px;display:flex;flex-direction:column;gap:8px}
.ocr-item{display:flex;justify-content:space-between;font:600 13px/1 Inter;padding:10px 12px;background:#F8FAFC;border-radius:10px}
.ocr-item em{color:#2563EB;font-style:normal}.ocr-item em.done{color:#059669}
.generic-body{padding:40px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px;color:#475569}
.feature{padding:80px 48px;background:#fff}
.feature.alt{background:var(--cloud)}
.feature-inner{max-width:1180px;margin:0 auto;display:flex;gap:48px;align-items:center}
.feature-inner.reverse{flex-direction:row-reverse}
.feature-text{flex:1}
.feature-num{font:800 72px/1 'Noto Serif SC',serif;color:#EEF2FF;margin-bottom:-8px}
.feature-text h2{font-size:42px;line-height:1.05;font-weight:800;letter-spacing:-.02em}
.feature-text p{margin-top:12px;color:#475569;line-height:1.8}
.feature-list{margin-top:16px;display:grid;gap:8px;list-style:none}
.feature-list li{display:flex;gap:8px;font:600 14px/1 Inter}
.fv-card{background:#fff;border:1px solid var(--hair);border-radius:16px;overflow:hidden;flex:1}
.fv-head{padding:12px 16px;color:#fff;font:700 12px/1 Inter;letter-spacing:.06em}
.fv-body{padding:16px;display:flex;flex-direction:column;gap:10px}
.fv-item{padding:10px 12px;background:#F8FAFC;border-radius:10px;font:500 13px/1 Inter}
.pd-cta{padding:72px 48px;text-align:center;background:linear-gradient(135deg,#EFF6FF 0%,#fff 100%);}
.pd-cta h2{font-size:36px;font-weight:800}
.not-found{padding:120px 48px;text-align:center}
@media(max-width:900px){.pd-hero-inner,.feature-inner,.feature-inner.reverse{flex-direction:column}.pd-visual{width:100%}}
@media(max-width:720px){.pd-hero{padding:96px 20px 48px}.pd-hero-left h1{font-size:34px}.feature{padding:56px 20px}.feature-text h2{font-size:30px}.pd-cta{padding:56px 20px}}
</style>
