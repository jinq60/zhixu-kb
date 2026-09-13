<script setup lang="ts">
/**
 * Wandor 1:1 复刻演示页（/demo-wandor，仅用于对照验证，不挂导航）。
 * 严格对照提示词 spec 转写：色值/字号/间距/圆角/动效参数全部取自原文，
 * Tailwind 类逐项换算为等值 CSS；仅 lucide-react Upload 换为同名 Element Plus 图标。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'

const NAV_LINKS = ['Discover', 'Pricing', 'FAQs']
const PROMPT_TEXT =
  "I'm planning a 7-day trip to Japan in October. I love food, hidden cafes, scenic hikes, and want to avoid crowds...."

const fileInputRef = ref<HTMLInputElement | null>(null)
const VIDEO_SRC =
  'https://pollen-batch-41236914.figma.site/_components/v2/f0ee2dae7671c170c34f12e31c4cb41418976c98/769c564298c132f7919405cd9f17c1b1231f341d.769c5642.mp4'

const onUploadClick = () => {
  fileInputRef.value?.click()
}

const onNav = (label: string) => {
  // 演示页：导航仅做悬停态展示，不跳转
  console.debug(`[wandor-demo] nav: ${label}`)
}

onMounted(() => {
  document.title = 'Wandor — Where will you go next?'
})

onBeforeUnmount(() => {
  document.title = '知序智能知识库'
})
</script>

<template>
  <div class="wandor-demo">
    <section class="wandor-hero">
      <video
        class="wandor-bg"
        :src="VIDEO_SRC"
        autoplay
        muted
        loop
        playsinline
      />
      <div
        class="wandor-fade"
        style="background: linear-gradient(180deg, rgba(255,255,255,1) 0%, rgba(255,255,255,0) 100%)"
      />
      <div class="wandor-content">
        <nav class="wandor-nav">
          <span class="wandor-wordmark">wandor</span>
          <div class="wandor-links">
            <button
              v-for="link in NAV_LINKS"
              :key="link"
              type="button"
              class="wandor-link"
              @click="onNav(link)"
            >
              {{ link }}
            </button>
          </div>
          <div class="wandor-actions">
            <button type="button" class="wandor-login" @click="onNav('Login')">Login</button>
            <button type="button" class="wandor-cta" @click="onNav('Plan My Trip')">Plan My Trip</button>
          </div>
        </nav>

        <div class="wandor-body">
          <h1>Where will you go next?</h1>
          <p class="wandor-sub">
            Tell our AI where you're going and what you love. We'll create a personalized itinerary for you.
          </p>

          <div class="wandor-card">
            <p class="wandor-prompt">{{ PROMPT_TEXT }}</p>
            <button type="button" class="wandor-card-cta" @click="onNav('Plan My Trip')">
              Plan My Trip
            </button>
            <input ref="fileInputRef" type="file" accept="image/*,.pdf" class="wandor-file" />
            <button
              type="button"
              class="wandor-upload"
              aria-label="Upload inspiration"
              @click="onUploadClick"
            >
              <el-icon><Upload /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.wandor-demo {
  font-family: 'Geist', sans-serif;
  background: #fff;
}

.wandor-hero {
  position: relative;
  min-height: 100vh;
  min-height: 100svh;
  width: 100%;
  overflow: hidden;
}

.wandor-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
}

.wandor-fade {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 687px;
  pointer-events: none;
  z-index: 1;
}

.wandor-content {
  position: relative;
  z-index: 2;
  max-width: 1360px;
  margin: 0 auto;
}

.wandor-nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 80px 16px;
}

.wandor-wordmark {
  font-family: 'Special Elite', serif;
  font-size: 40px;
  color: #000;
  line-height: 1;
  user-select: none;
}

.wandor-links {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 32px;
}

.wandor-link {
  background: transparent;
  border: none;
  cursor: pointer;
  font-family: 'Geist', sans-serif;
  font-size: 15px;
  font-weight: 500;
  text-transform: uppercase;
  color: #1a1a1a;
  letter-spacing: 0.04em;
  transition: opacity 0.2s ease;
  padding: 0;
}

.wandor-link:hover {
  opacity: 0.55;
}

.wandor-actions {
  display: flex;
  align-items: center;
  gap: 32px;
}

.wandor-login {
  background: transparent;
  border: none;
  cursor: pointer;
  font-family: 'Geist', sans-serif;
  font-size: 15px;
  font-weight: 600;
  text-transform: uppercase;
  color: #292929;
  letter-spacing: 0.04em;
  transition: opacity 0.2s ease;
  padding: 0;
}

.wandor-login:hover {
  opacity: 0.55;
}

.wandor-cta {
  background: #0a0a0a;
  color: #fafafa;
  border: none;
  cursor: pointer;
  font-family: 'Geist', sans-serif;
  font-size: 15px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 14px 20px;
  border-radius: 999px;
  transition: all 0.2s ease;
}

.wandor-cta:hover {
  background: #333;
}

.wandor-cta:active {
  transform: scale(0.95);
}

.wandor-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 24px 96px;
  text-align: center;
}

.wandor-body h1 {
  font-family: 'Geist', sans-serif;
  font-size: clamp(40px, 6vw, 68px);
  font-weight: 500;
  color: #1a1a1a;
  line-height: 1.05;
  letter-spacing: -0.04em;
  max-width: 820px;
  margin: 0 0 20px;
}

.wandor-sub {
  font-family: 'Geist', sans-serif;
  font-size: 20px;
  font-weight: 500;
  color: #767676;
  line-height: 1.625;
  max-width: 500px;
  margin: 0 0 40px;
}

.wandor-card {
  position: relative;
  width: 701px;
  min-height: 208px;
  background: rgba(255, 255, 255, 0.06);
  border: 3px solid #ffffff;
  border-radius: 44px;
  box-shadow: 0 0 4px 0 rgba(0, 0, 0, 0.15);
  overflow: hidden;
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
}

.wandor-prompt {
  position: absolute;
  left: 29px;
  top: 57px;
  transform: translateY(-50%);
  width: 609px;
  margin: 0;
  text-align: left;
  font-family: 'Geist', sans-serif;
  font-size: 20px;
  font-weight: 500;
  color: #905831;
  line-height: 1.625;
  overflow-wrap: break-word;
}

.wandor-card-cta {
  position: absolute;
  bottom: 21px;
  right: 21px;
  width: 156px;
  height: 56px;
  background: #000;
  border: none;
  border-radius: 44px;
  box-shadow: 0 0 2px 0 rgba(0, 0, 0, 0.05);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Geist', sans-serif;
  font-size: 16px;
  font-weight: 500;
  color: #fafafa;
  text-transform: uppercase;
  letter-spacing: 0.02em;
  transition: all 0.2s ease;
}

.wandor-card-cta:hover {
  background: #333;
}

.wandor-card-cta:active {
  transform: scale(0.95);
}

.wandor-file {
  display: none;
}

.wandor-upload {
  position: absolute;
  left: 21px;
  top: 137px;
  width: 44px;
  height: 44px;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-backdrop-filter: blur(14px);
  backdrop-filter: blur(14px);
  transition: transform 0.2s ease;
  padding: 0;
}

.wandor-upload:hover {
  transform: scale(1.05);
}

.wandor-upload:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.wandor-upload .el-icon {
  width: 18px;
  height: 18px;
  font-size: 18px;
  color: #1a1a1a;
  flex-shrink: 0;
}

@media (max-width: 767px) {
  .wandor-nav {
    padding: 20px 24px;
  }

  .wandor-wordmark {
    font-size: 32px;
  }

  .wandor-links,
  .wandor-login {
    display: none;
  }

  .wandor-card {
    width: calc(100vw - 48px);
  }

  .wandor-prompt {
    width: calc(100% - 58px);
    font-size: 17px;
  }
}
</style>
