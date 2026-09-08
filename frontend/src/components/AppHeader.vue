<script setup lang="ts">
/**
 * 官网导航栏：纯展示组件，不关联任何业务代码。
 * - 导航/产品目录来自 config/products（上架新产品只改配置）
 * - 任务轮询、任务弹窗等业务逻辑已搬到 App.vue（工作台布局内）
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { navigateToProduct, SITE_PRODUCTS } from '../config/products'
import {
  ArrowRight,
  ArrowDown,
  Download,
  Menu
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mobileMenuOpen = ref(false)

const isHome = computed(() => route.path === '/' || route.path === '/home')
const isWorkspace = computed(() => !isHome.value)

const enterWeb = () => {
  if (auth.isLoggedIn) {
    router.push('/notes')
  } else {
    auth.openLoginModal()
  }
}

const goDownload = () => {
  if (auth.isLoggedIn) {
    goHomeHash('#download')
  } else {
    auth.openLoginModal()
  }
}

/** 产品目录唯一来源：config/products，上架新产品无需改本文件 */
const products = SITE_PRODUCTS

const navLinks = [
  { label: '产品', type: 'dropdown' },
  { label: '下载', hash: '#download' },
  { label: '解决方案', hash: '#solutions' },
  { label: '客户案例', hash: '#cases' },
  { label: '文档', hash: '#docs' },
  { label: '关于我们', hash: '#about' },
  { label: '联系我们', hash: '#contact' }
]

const goHomeHash = (hash: string) => {
  mobileMenuOpen.value = false
  if (isHome.value) {
    const el = document.querySelector(hash)
    if (el) el.scrollIntoView({ behavior: 'smooth' })
  } else {
    router.push({ path: '/home', hash }).then(() => {
      setTimeout(() => {
        const el = document.querySelector(hash)
        if (el) el.scrollIntoView({ behavior: 'smooth' })
      }, 100)
    })
  }
}
</script>

<template>
  <header class="app-header" :class="{ 'in-workspace': isWorkspace }">
    <div class="header-inner">
      <div class="header-brand" @click="router.push('/home')">
        <div class="brand-logo">知序</div>
        <div class="brand-text">
          <span class="brand-name">知序</span>
          <span class="brand-slogan">ZhiXu Tech</span>
        </div>
      </div>

      <nav class="header-nav">
        <el-dropdown
          v-for="link in navLinks"
          :key="link.label"
          placement="bottom"
          :show-timeout="120"
          :hide-timeout="150"
        >
          <span
            v-if="link.type === 'dropdown'"
            class="nav-item"
          >
            {{ link.label }}
            <el-icon class="nav-arrow"><ArrowDown /></el-icon>
          </span>
          <span
            v-else
            class="nav-item"
            @click="goHomeHash(link.hash!)"
          >
            {{ link.label }}
          </span>
          <template v-if="link.type === 'dropdown'" #dropdown>
            <el-dropdown-menu class="product-menu">
              <el-dropdown-item
                v-for="p in products"
                :key="p.key"
                @click="navigateToProduct(router, p)"
              >
                <div class="product-item">
                  <div
                    class="product-icon"
                    :class="{ coming: p.coming }"
                    :style="{ '--p': p.color }"
                  >
                    <el-icon><component :is="p.icon" /></el-icon>
                  </div>
                  <div class="product-info">
                    <div class="product-name">
                      {{ p.label }}
                      <el-tag v-if="p.coming" size="small" type="info" effect="plain">即将上线</el-tag>
                    </div>
                    <div class="product-desc">{{ p.desc }}</div>
                  </div>
                </div>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </nav>

      <div class="header-actions">
        <el-button
          text
          class="download-link"
          @click="goDownload"
        >
          <el-icon><Download /></el-icon>
          下载
        </el-button>
        <el-button
          type="primary"
          class="console-btn"
          @click="enterWeb"
        >
          {{ isWorkspace ? '工作台' : 'Web 体验' }}
          <el-icon class="btn-icon"><ArrowRight /></el-icon>
        </el-button>

      </div>

      <div class="mobile-toggle" @click="mobileMenuOpen = !mobileMenuOpen">
        <el-icon><Menu /></el-icon>
      </div>
    </div>

    <div v-show="mobileMenuOpen" class="mobile-menu">
      <a class="mobile-link" @click="router.push('/home'); mobileMenuOpen = false">首页</a>
      <a class="mobile-link" @click="goHomeHash('#products')">产品</a>
      <a class="mobile-link" @click="goHomeHash('#download')">下载</a>
      <a class="mobile-link" @click="goHomeHash('#solutions')">解决方案</a>
      <a class="mobile-link" @click="goHomeHash('#cases')">客户案例</a>
      <a class="mobile-link" @click="goHomeHash('#docs')">文档</a>
      <a class="mobile-link" @click="goHomeHash('#about')">关于我们</a>
      <a class="mobile-link" @click="goHomeHash('#contact')">联系我们</a>
      <el-button type="primary" class="mobile-console-btn" @click="enterWeb">
        Web 体验
      </el-button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: 68px;
  background: rgba(255, 254, 250, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid #f3e9dc;
}

.app-header button:focus-visible,
.app-header a:focus-visible,
.app-header .nav-item:focus-visible {
  outline: 2px solid var(--zx-brand);
  outline-offset: 2px;
  border-radius: 8px;
}

/* 工作台内恢复蓝白（落地页保持柿色身份） */
.app-header.in-workspace {
  --el-color-primary: #409eff;
  --el-color-primary-light-3: #79bbff;
  --el-color-primary-light-5: #a0cfff;
  --el-color-primary-light-7: #c6e2ff;
  --el-color-primary-light-8: #d9ecff;
  --el-color-primary-light-9: #ecf5ff;
  --el-color-primary-dark-2: #337ecc;
}

.app-header.in-workspace .brand-logo {
  background: #2563eb;
  box-shadow: none;
}

.app-header.in-workspace .nav-item:hover {
  color: #2563eb;
  background: #f4f7fd;
}

.header-inner {
  max-width: 1280px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.brand-logo {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f2641e 0%, #d94f0e 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  box-shadow: 0 6px 16px var(--zx-brand-ring);
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.brand-name {
  font-family: var(--zx-display);
  font-size: 19px;
  font-weight: 800;
  color: var(--zx-ink);
  line-height: 1.1;
  letter-spacing: 0.5px;
}

.brand-slogan {
  font-family: var(--zx-mono);
  font-size: 10px;
  color: #a8a29e;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  line-height: 1.2;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border-radius: 8px;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: var(--zx-brand-ink);
  background: var(--zx-brand-soft);
}

.nav-arrow {
  font-size: 12px;
}

.product-menu {
  width: 320px;
  padding: 8px;
  border-radius: 16px;
}

.product-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 6px;
  border-radius: 12px;
}

.product-icon {
  --p: var(--zx-brand);
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fef0e9;
  background: color-mix(in srgb, var(--p) 12%, white);
  color: var(--p);
  font-size: 19px;
  flex-shrink: 0;
}

.product-icon.coming {
  background: #f5f3ef;
  color: #a8a29e;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 2px;
}

.product-desc {
  font-size: 12px;
  color: #6b7280;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.download-link {
  color: #4b5563;
  font-weight: 500;
}

.download-link .el-icon {
  margin-right: 4px;
}

.console-btn {
  font-weight: 600;
}

.btn-icon {
  margin-left: 4px;
}

.mobile-toggle {
  display: none;
  font-size: 22px;
  color: #4b5563;
  cursor: pointer;
  padding: 8px;
}

.mobile-menu {
  display: none;
  position: absolute;
  top: 68px;
  left: 0;
  right: 0;
  background: #fff;
  border-bottom: 1px solid #eef2f7;
  padding: 16px 24px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
}

.mobile-link {
  display: block;
  padding: 12px 0;
  color: #4b5563;
  font-size: 15px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}

.mobile-console-btn {
  width: 100%;
  margin-top: 16px;
}

@media (max-width: 1024px) {
  .header-nav,
  .download-link {
    display: none;
  }

  .mobile-toggle {
    display: block;
  }

  .mobile-menu {
    display: block;
  }
}
</style>
