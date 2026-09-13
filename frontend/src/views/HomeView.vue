<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { SITE_PRODUCTS } from '../config/products'
import {
  ArrowRight,
  Download,
  Notebook,
  ChatDotRound,
  Share,
  MagicStick,
  Search,
  School,
  OfficeBuilding,
  EditPen,
  User,
  Star,
  Check,
  Document,
  DocumentCopy,
  Connection,
  Phone,
  Message,
  Location,
  QuestionFilled,
  Upload
} from '@element-plus/icons-vue'

const router = useRouter()
const auth = useAuthStore()

const enterWeb = () => {
  if (auth.isLoggedIn) {
    router.push('/notes')
  } else {
    auth.openLoginModal()
  }
}

const goDownload = () => {
  if (auth.isLoggedIn) {
    router.push('/home#download')
  } else {
    auth.openLoginModal()
  }
}

const reduceMotion =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

/* ---------- 现场问答演示：打字机逐字回答 ---------- */
const demoAnswer =
  '这周你读的 3 篇论文，争的其实是同一件事：RAG 的上限不在模型，在召回。我把 5 处关键原文标出来了，点引用可以直接跳回去。'
const typedText = ref('')
const typedDone = ref(false)
let typeTimer: ReturnType<typeof setInterval> | null = null

const startTyping = () => {
  if (reduceMotion) {
    typedText.value = demoAnswer
    typedDone.value = true
    return
  }
  let i = 0
  typeTimer = setInterval(() => {
    i += 1
    typedText.value = demoAnswer.slice(0, i)
    if (i >= demoAnswer.length && typeTimer) {
      clearInterval(typeTimer)
      typeTimer = null
      typedDone.value = true
    }
  }, 55)
}

const stopTyping = () => {
  if (typeTimer) {
    clearInterval(typeTimer)
    typeTimer = null
  }
}

/* ---------- 产品矩阵：基础信息唯一来源 config/products，落地页只补展示字段 ---------- */
import type { SiteProduct } from '../config/products'

interface LandingProduct extends SiteProduct {
  en: string
  img: string
  soft: string
  longDesc: string
  tags: string[]
  action: string
  primary?: boolean
}

const products: LandingProduct[] = [
  {
    ...SITE_PRODUCTS[0],
    en: 'KNOWLEDGE BASE',
    img: '/img/kb-notes.jpg',
    soft: '#fef0e9',
    longDesc: 'OCR 识别、AI 整理、知识图谱、RAG 问答一站式个人知识管理平台。',
    tags: ['已上线', '免费版可用'],
    action: 'Web 体验',
    primary: true
  },
  {
    ...SITE_PRODUCTS[1],
    en: 'TEAM SPACE',
    img: '/img/team-work.jpg',
    soft: '#f1edfe',
    longDesc: '面向团队的多人协作知识空间，权限管理、版本控制、AI 助手全集成。',
    tags: ['即将上线'],
    action: '预约体验'
  },
  {
    ...SITE_PRODUCTS[2],
    en: 'OCR TOOLKIT',
    img: '/img/ocr-manuscript.jpg',
    soft: '#e6f7f4',
    longDesc: '本地离线 OCR 识别套件，支持批量图片、PDF 与截图文字提取。',
    tags: ['客户端', 'Windows'],
    action: '预约下载'
  },
  {
    ...SITE_PRODUCTS[3],
    en: 'SYNC HELPER',
    img: '/img/sync-desk.jpg',
    soft: '#fdf6e3',
    longDesc: '多端知识库同步工具，本地文件、云端与 NAS 一键同步备份。',
    tags: ['即将上线'],
    action: '预约体验'
  }
]

/* ---------- 五步成序：可交互的流水线 ---------- */
const steps = [
  {
    key: 'capture',
    icon: Upload,
    label: '采集',
    en: 'CAPTURE',
    desc: '散落的信息，先有一个去处。',
    points: ['手机拍照、截图粘贴、拖拽文档，一键进库', '在库外随手记，回来自动归档', '批量上传不断线，百张图片一次收']
  },
  {
    key: 'recognize',
    icon: Search,
    label: '识别',
    en: 'RECOGNIZE',
    desc: '图片里的字，一个不落变成文本。',
    points: ['RapidOCR / PaddleOCR 双引擎自动切换', '透视矫正，还原原始版面', 'PDF / DOCX 直接提取正文']
  },
  {
    key: 'organize',
    icon: MagicStick,
    label: '整理',
    en: 'ORGANIZE',
    desc: '长文自己长出骨架。',
    points: ['AI 摘要、大纲、思维导图一键生成', '关键词与自动分类', '历史版本随时回滚']
  },
  {
    key: 'connect',
    icon: Share,
    label: '图谱',
    en: 'CONNECT',
    desc: '孤立的笔记，连成网络。',
    points: ['实体关系自动抽取', 'Neo4j 存储，力导图可视化', '跨笔记的关联自动浮现']
  },
  {
    key: 'ask',
    icon: ChatDotRound,
    label: '问答',
    en: 'ASK',
    desc: '只问你自己的知识库。',
    points: ['RAG 只检索你的笔记，不编造', '每条答案带引用，一键溯源原文', 'SSE 流式输出，边想边答']
  }
]

const stepIdx = ref(0)
const stepPaused = ref(false)
let stepTimer: ReturnType<typeof setInterval> | null = null

const selectStep = (idx: number) => {
  stepIdx.value = idx
}

const startStepTimer = () => {
  if (reduceMotion || stepTimer) return
  stepTimer = setInterval(() => {
    if (!stepPaused.value) {
      stepIdx.value = (stepIdx.value + 1) % steps.length
    }
  }, 6000)
}

const stopStepTimer = () => {
  if (stepTimer) {
    clearInterval(stepTimer)
    stepTimer = null
  }
}

/* marquee 复用同一数据源 */
const pipeline = steps.map((s) => ({ icon: s.icon, label: s.label, en: s.en }))

const solutions = [
  {
    icon: School,
    title: '高校教研',
    desc: '论文资料整理、课程讲义沉淀、科研成果可视化与智能答疑。'
  },
  {
    icon: OfficeBuilding,
    title: '企业知识管理',
    desc: '内部文档统一归档、新员工快速检索、项目经验沉淀与共享。'
  },
  {
    icon: EditPen,
    title: '内容创作',
    desc: '素材收集、灵感整理、文章大纲生成与多平台内容分发。'
  },
  {
    icon: User,
    title: '个人学习',
    desc: '读书笔记、网课截图、技术碎片统一整理，构建第二大脑。'
  }
]

const cases = [
  {
    org: '某 985 高校计算机学院',
    role: '科研团队',
    result: '将 3 年内 2000+ 篇论文资料结构化，知识问答准确率提升 40%。',
    tags: ['知识图谱', 'RAG 问答']
  },
  {
    org: '某智能制造企业',
    role: '技术中台',
    result: '建立设备运维知识库，售后问题平均处理时间从 2h 降至 20min。',
    tags: ['企业知识管理', 'OCR']
  },
  {
    org: '独立技术博主',
    role: '内容创作者',
    result: '累计沉淀 1500+ 技术笔记，通过公开分享获得 50 万+ 阅读。',
    tags: ['个人知识库', '公开分享']
  }
]

const resources = [
  { title: '快速开始', desc: '5 分钟部署本地知识库', icon: Document },
  { title: 'API 文档', desc: '开放的 RESTful 接口说明', icon: DocumentCopy },
  { title: '部署指南', desc: 'Docker 与离线安装教程', icon: Connection },
  { title: '更新日志', desc: '版本迭代与功能路线图', icon: Star }
]

const downloads = [
  { platform: 'Windows', version: 'v1.0.0', size: '约 180 MB', note: 'exe 安装包' },
  { platform: 'macOS', version: 'v1.0.0', size: '约 210 MB', note: 'dmg 安装包' },
  { platform: 'Linux', version: 'v1.0.0', size: '约 170 MB', note: 'AppImage' },
  { platform: 'Docker', version: 'latest', size: '一键部署', note: 'compose 模板' }
]

const faqs = [
  {
    q: '知序的产品是否需要联网使用？',
    a: '核心功能支持本地离线运行。OCR、AI 整理与知识图谱均可在本地 Docker 或客户端中完成，数据完全由你掌控。'
  },
  {
    q: 'Windows 客户端何时上线？',
    a: '知序 OCR 工具箱与数据同步助手的 Windows 客户端正在内测中，预计下个季度发布。你可以在下载中心预约。'
  },
  {
    q: '是否支持私有化部署？',
    a: '支持。我们提供 Docker Compose 一键部署方案，团队版用户可申请企业级私有化部署与定制开发。'
  },
  {
    q: '免费版与专业版有什么区别？',
    a: '免费版已包含 OCR 识别、AI 整理、知识图谱与 RAG 问答等核心能力，适合个人学习。专业版与团队版提供更高并发、导出能力与协作空间。'
  }
]

/* 产品卡聚光灯：高光跟随鼠标，减弱动效时不启用 */
const setupSpotlight = () => {
  if (reduceMotion) return
  const grid = document.querySelector('.product-grid')
  if (!grid) return
  grid.addEventListener('mousemove', (e: Event) => {
    const me = e as MouseEvent
    const cards = grid.querySelectorAll<HTMLElement>('.product-card')
    cards.forEach((card) => {
      const rect = card.getBoundingClientRect()
      card.style.setProperty('--mx', `${me.clientX - rect.left}px`)
      card.style.setProperty('--my', `${me.clientY - rect.top}px`)
    })
  })
}

onMounted(() => {
  startStepTimer()
  setupSpotlight()
  startTyping()
})
onBeforeUnmount(() => {
  stopStepTimer()
  stopTyping()
})

/* ---------- 滚动显现 ---------- */
const vReveal = {
  mounted(el: HTMLElement) {
    if (reduceMotion || typeof IntersectionObserver === 'undefined') {
      el.classList.add('revealed')
      return
    }
    el.classList.add('reveal')
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('revealed')
            observer.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.12 }
    )
    observer.observe(el)
  }
}
</script>

<template>
  <div class="landing-page">
    <div class="grain-overlay" aria-hidden="true" />

    <!-- Hero：纸上山水 + 现场问答 -->
    <section class="hero-paper">
      <div class="hero-center">
        <div class="hero-eyebrow-row">
          <span class="seal" aria-hidden="true">知序</span>
          <span class="hero-eyebrow">ZHI XU · KNOWLEDGE OS</span>
        </div>
        <h1>把散落的，变成<span class="h1-accent">可问的</span></h1>
        <p class="hero-desc">
          照片、截图、文档、灵感——知序把它们识别、整理、连成图谱，
          变成一个随时能回答你的个人知识库。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" class="hero-primary" @click="enterWeb">
            免费开始整理
            <el-icon class="btn-icon"><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" class="hero-ghost" @click="router.push('/home#how')">
            看看怎么运作
          </el-button>
        </div>
        <div class="hero-trust">
          <span><el-icon><Check /></el-icon> 免登录体验</span>
          <span><el-icon><Check /></el-icon> 本地优先</span>
          <span><el-icon><Check /></el-icon> 私有化部署</span>
        </div>
      </div>

      <!-- 现场问答演示：玻璃卡 -->
      <div class="demo-wrap" v-reveal>
        <div class="demo-card">
          <div class="demo-label">LIVE DEMO · 来自你的知识库</div>
          <div class="demo-q">
            <span class="demo-avatar">我</span>
            <p>这周那 3 篇 RAG 论文，到底在争什么？</p>
          </div>
          <div class="demo-a">
            <span class="demo-avatar ai">序</span>
            <p>
              {{ typedText }}<span v-if="!typedDone" class="typing-caret" aria-hidden="true" />
            </p>
          </div>
          <div v-if="typedDone" class="demo-sources">
            <span class="demo-src">引用 · RAG 调研笔记</span>
            <span class="demo-src">引用 · 召回实验记录</span>
            <a class="demo-go" @click="enterWeb">去亲自问一句 <el-icon><ArrowRight /></el-icon></a>
          </div>
        </div>
      </div>

      <!-- 知识山水：藏书阁、远山、浮书、落日 -->
      <div class="panorama" aria-hidden="true">
        <svg viewBox="0 0 1440 360" preserveAspectRatio="xMidYMax slice" class="panorama-svg">
          <!-- 落日 -->
          <circle cx="1150" cy="140" r="44" fill="#e8a13c" opacity="0.92" />
          <circle cx="1150" cy="140" r="62" fill="none" stroke="#e8a13c" stroke-width="1.5" opacity="0.35" />
          <circle cx="1150" cy="140" r="80" fill="none" stroke="#e8a13c" stroke-width="1" opacity="0.18" />
          <!-- 云 -->
          <g fill="#ffffff" opacity="0.9" class="cloud c1">
            <ellipse cx="300" cy="66" rx="56" ry="14" />
            <ellipse cx="344" cy="58" rx="38" ry="12" />
            <ellipse cx="262" cy="60" rx="28" ry="10" />
          </g>
          <g fill="#ffffff" opacity="0.75" class="cloud c2">
            <ellipse cx="800" cy="48" rx="46" ry="12" />
            <ellipse cx="838" cy="41" rx="30" ry="10" />
          </g>
          <g fill="#ffffff" opacity="0.6" class="cloud c3">
            <ellipse cx="1350" cy="70" rx="40" ry="11" />
            <ellipse cx="1382" cy="63" rx="26" ry="9" />
          </g>
          <!-- 飞鸟 -->
          <g fill="none" stroke="#3a3a3a" stroke-linecap="round">
            <path d="M600,76 q8,-8 16,0 q8,-8 16,0" stroke-width="2" />
            <path d="M652,96 q6,-6 12,0 q6,-6 12,0" stroke-width="1.6" />
            <path d="M900,100 q7,-7 14,0 q7,-7 14,0" stroke-width="1.8" />
            <path d="M150,120 q6,-6 12,0 q6,-6 12,0" stroke-width="1.6" />
          </g>
          <!-- 远山（含雪顶主峰） -->
          <path d="M0,236 L120,130 L210,210 L330,80 L400,130 L470,210 L610,110 L740,230 L880,140 L1010,235 L1140,120 L1260,220 L1360,150 L1440,215 L1440,360 L0,360 Z" fill="#e7d6b8" />
          <path d="M330,80 L368,112 L344,124 L318,110 L296,122 L278,108 Z" fill="#fdf8ee" />
          <path d="M1140,120 L1172,148 L1150,158 L1128,146 L1110,156 L1096,142 Z" fill="#fdf8ee" />
          <path d="M0,272 L160,180 L320,262 L500,165 L680,266 L860,178 L1040,266 L1220,182 L1440,266 L1440,360 L0,360 Z" fill="#d9c69c" opacity="0.9" />
          <!-- 山间雾带 -->
          <ellipse cx="720" cy="252" rx="420" ry="16" fill="#ffffff" opacity="0.45" />
          <!-- 中景丘陵 -->
          <path d="M0,306 L200,232 L420,300 L680,224 L940,300 L1180,228 L1440,300 L1440,360 L0,360 Z" fill="#8a9b6e" />
          <!-- 登山石阶小径（通往藏书阁） -->
          <path d="M880,330 L920,306 L960,288 L1000,272 L1030,262" fill="none" stroke="#f3e9d2" stroke-width="7" stroke-linecap="round" stroke-dasharray="1 12" opacity="0.9" />
          <!-- 三重塔（左） -->
          <g>
            <rect x="282" y="238" width="76" height="10" rx="2" fill="#8a8a86" />
            <rect x="292" y="206" width="56" height="32" fill="#efe2c8" />
            <path d="M284,208 L320,188 L356,208 Z" fill="#a84a26" />
            <rect x="300" y="182" width="40" height="24" fill="#efe2c8" />
            <path d="M294,184 L320,168 L346,184 Z" fill="#b8552f" />
            <rect x="308" y="162" width="24" height="16" fill="#efe2c8" />
            <path d="M302,164 L320,150 L338,164 Z" fill="#c65a2e" />
            <rect x="318" y="138" width="4" height="14" fill="#7a4a2e" />
            <circle cx="320" cy="134" r="3" fill="#d9930d" />
          </g>
          <!-- 藏书阁（右） -->
          <g>
            <rect x="986" y="252" width="148" height="10" rx="2" fill="#8a8a86" />
            <rect x="1000" y="196" width="120" height="56" fill="#f3e7d3" />
            <rect x="1000" y="196" width="120" height="10" fill="#e0cfae" />
            <rect x="1012" y="214" width="20" height="38" fill="#7a4a2e" />
            <rect x="1024" y="222" width="8" height="8" fill="#f3e7d3" opacity="0.7" />
            <rect x="1049" y="214" width="22" height="38" fill="#7a4a2e" />
            <rect x="1086" y="214" width="20" height="38" fill="#7a4a2e" />
            <rect x="1096" y="222" width="8" height="8" fill="#f3e7d3" opacity="0.7" />
            <path d="M988,198 L1060,164 L1132,198 Z" fill="#a84a26" />
            <path d="M988,198 L1060,164 L1132,198" fill="none" stroke="#7e3a1e" stroke-width="2" />
            <rect x="1054" y="150" width="12" height="18" fill="#7a4a2e" />
            <path d="M1002,152 L1060,124 L1118,152 Z" fill="#c65a2e" />
            <rect x="1058" y="112" width="4" height="14" fill="#7a4a2e" />
          </g>
          <!-- 松林（左） -->
          <g>
            <rect x="120" y="262" width="9" height="40" rx="4" fill="#5d4a36" />
            <path d="M124,208 L100,262 L149,262 Z" fill="#4c6148" />
            <path d="M124,226 L106,262 L143,262 Z" fill="#56704f" />
            <rect x="160" y="270" width="8" height="32" rx="4" fill="#5d4a36" />
            <path d="M164,224 L144,270 L184,270 Z" fill="#5f7355" />
          </g>
          <!-- 柿子树 -->
          <g>
            <rect x="180" y="258" width="9" height="42" rx="4" fill="#7a5a3e" />
            <circle cx="184" cy="240" r="26" fill="#f2641e" opacity="0.92" />
            <circle cx="166" cy="250" r="16" fill="#e85a17" />
            <circle cx="202" cy="250" r="16" fill="#ff8a4d" />
            <circle cx="184" cy="232" r="5" fill="#ffd9bd" opacity="0.8" />
          </g>
          <!-- 大松（右） -->
          <g>
            <rect x="880" y="252" width="10" height="48" rx="4" fill="#5d4a36" />
            <circle cx="885" cy="232" r="30" fill="#5f7355" />
            <circle cx="862" cy="244" r="20" fill="#6e8b67" />
            <circle cx="908" cy="244" r="20" fill="#56704f" />
          </g>
          <!-- 浮书：外层 g 只管定位，内层做浮动（CSS transform 会覆盖属性定位，不可同层） -->
          <g transform="translate(600,36)">
            <g class="float-book b1">
              <rect x="0" y="0" width="46" height="60" rx="4" fill="#f2641e" transform="rotate(-8)" />
              <rect x="8" y="12" width="30" height="4" rx="2" fill="#ffffff" opacity="0.85" transform="rotate(-8)" />
              <rect x="8" y="22" width="30" height="4" rx="2" fill="#ffffff" opacity="0.6" transform="rotate(-8)" />
              <rect x="8" y="32" width="20" height="4" rx="2" fill="#ffffff" opacity="0.6" transform="rotate(-8)" />
            </g>
          </g>
          <g transform="translate(742,120)">
            <g class="float-book b2">
              <rect x="0" y="0" width="40" height="54" rx="4" fill="#0ca789" transform="rotate(7)" />
              <rect x="7" y="11" width="26" height="4" rx="2" fill="#ffffff" opacity="0.85" transform="rotate(7)" />
              <rect x="7" y="20" width="26" height="4" rx="2" fill="#ffffff" opacity="0.6" transform="rotate(7)" />
              <rect x="7" y="29" width="17" height="4" rx="2" fill="#ffffff" opacity="0.6" transform="rotate(7)" />
            </g>
          </g>
          <g transform="translate(268,52)">
            <g class="float-book b3">
              <rect x="0" y="0" width="36" height="48" rx="4" fill="#7a5af8" transform="rotate(-5)" />
              <rect x="6" y="10" width="24" height="4" rx="2" fill="#ffffff" opacity="0.85" transform="rotate(-5)" />
              <rect x="6" y="19" width="24" height="4" rx="2" fill="#ffffff" opacity="0.6" transform="rotate(-5)" />
            </g>
          </g>
          <!-- 近景 -->
          <path d="M0,318 L360,288 L760,318 L1080,290 L1440,318 L1440,360 L0,360 Z" fill="#2e3b2f" />
          <g stroke="#4a5a48" stroke-width="2" stroke-linecap="round">
            <path d="M120,322 l4,-10 M128,322 l-3,-9 M136,322 l4,-11" />
            <path d="M700,320 l4,-10 M708,320 l-3,-9" />
            <path d="M1250,318 l4,-10 M1258,318 l-3,-9 M1266,318 l4,-11" />
          </g>
        </svg>
      </div>
    </section>

    <!-- 跑马灯 -->
    <div class="marquee" aria-hidden="true">
      <div class="marquee-track">
        <span v-for="n in 2" :key="n" class="marquee-chunk">
          <span v-for="s in pipeline" :key="s.label + n" class="marquee-item">
            {{ s.label }}<i>◆</i><em>{{ s.en }}</em>
          </span>
          <span class="marquee-item"><em>ZHI XU KNOWLEDGE OS</em><i>◆</i></span>
        </span>
      </div>
    </div>

    <!-- 五步成序 -->
    <section id="how" class="how">
      <div class="section-head" v-reveal>
        <span class="section-eyebrow">HOW IT WORKS · 五步成序</span>
        <h2>散落，是怎么归位的</h2>
        <p>点每一步，看一条笔记从碎片变成答案</p>
      </div>
      <div
        class="stepper"
        v-reveal
        @mouseenter="stepPaused = true"
        @mouseleave="stepPaused = false"
        @focusin="stepPaused = true"
        @focusout="stepPaused = false"
      >
        <div class="step-list" role="tablist" aria-label="知识流水线">
          <button
            v-for="(s, idx) in steps"
            :key="s.key"
            type="button"
            role="tab"
            :aria-selected="stepIdx === idx"
            :class="['step-btn', { active: stepIdx === idx }]"
            @click="selectStep(idx)"
          >
            <span class="step-num">0{{ idx + 1 }}</span>
            <span class="step-name">{{ s.label }}</span>
            <span class="step-en">{{ s.en }}</span>
          </button>
        </div>
        <div class="step-panel">
          <div class="step-visual" aria-hidden="true">
            <template v-if="steps[stepIdx].key === 'capture'">
              <div class="mini-tray" />
              <div class="mini-chips"><span /><span /><span /></div>
            </template>
            <template v-else-if="steps[stepIdx].key === 'recognize'">
              <div class="mini-scan">
                <div class="mini-line w90" />
                <div class="mini-line w65" />
                <div class="mini-line w80" />
                <i class="mini-scanline" />
              </div>
            </template>
            <template v-else-if="steps[stepIdx].key === 'organize'">
              <div class="mini-outline">
                <span class="l1" /><span class="l2" /><span class="l2" /><span class="l3" /><span class="l1" />
              </div>
            </template>
            <template v-else-if="steps[stepIdx].key === 'connect'">
              <div class="mini-graph">
                <i class="g1" /><i class="g2" /><i class="g3" /><i class="g4" />
              </div>
            </template>
            <template v-else>
              <div class="mini-chat">
                <span class="bubble user">这篇论文的核心结论？</span>
                <span class="bubble ai">三点，附原文引用 →</span>
              </div>
            </template>
          </div>
          <div class="step-info">
            <div class="step-info-icon">
              <el-icon><component :is="steps[stepIdx].icon" /></el-icon>
            </div>
            <h3>{{ steps[stepIdx].label }}<em>{{ steps[stepIdx].en }}</em></h3>
            <p class="step-desc">{{ steps[stepIdx].desc }}</p>
            <ul class="step-points">
              <li v-for="pt in steps[stepIdx].points" :key="pt">
                <el-icon><Check /></el-icon>{{ pt }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats -->
    <section class="stats">
      <div class="stats-inner" v-reveal>
        <div class="stat-item"><strong>10,000+</strong><span>知识库笔记</span><em>NOTES</em></div>
        <div class="stat-item"><strong>99.2%</strong><span>OCR 识别准确率</span><em>ACCURACY</em></div>
        <div class="stat-item"><strong>500ms</strong><span>AI 平均响应</span><em>RESPONSE</em></div>
        <div class="stat-item"><strong>4</strong><span>产品矩阵</span><em>PRODUCTS</em></div>
      </div>
    </section>

    <!-- Products -->
    <section id="products" class="products">
      <div class="section-head" v-reveal>
        <span class="section-eyebrow">PRODUCTS · 产品矩阵</span>
        <h2>覆盖知识管理全场景</h2>
        <p>从个人学习到企业协作，知序提供端到端的知识管理工具链</p>
      </div>
      <div class="product-grid">
        <div
          v-for="p in products"
          :key="p.key"
          v-reveal
          :class="['product-card', { primary: p.primary, coming: p.coming }]"
          :style="{ '--p': p.color, '--ps': p.soft }"
        >
          <div class="product-photo">
            <img :src="p.img" :alt="p.label" loading="lazy" />
            <span class="product-photo-tag">{{ p.tags[0] }}</span>
          </div>
          <div class="product-icon">
            <el-icon><component :is="p.icon" /></el-icon>
          </div>
          <div class="product-en">{{ p.en }}</div>
          <h3>{{ p.label }}</h3>
          <p>{{ p.longDesc }}</p>
          <div class="product-tags">
            <el-tag v-for="tag in p.tags" :key="tag" size="small" type="info" effect="plain">{{ tag }}</el-tag>
          </div>
          <el-button
            :type="p.primary ? 'primary' : 'default'"
            class="product-action"
            @click="p.primary ? enterWeb() : auth.openLoginModal()"
          >
            {{ p.action }}
            <el-icon v-if="!p.coming" class="btn-icon"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </section>

    <!-- Solutions -->
    <section id="solutions" class="solutions">
      <div class="section-head light" v-reveal>
        <span class="section-eyebrow">SCENARIOS · 解决方案</span>
        <h2>为不同场景量身打造</h2>
        <p>无论你是学生、教师、创作者还是企业技术团队，都能找到合适的落地方式</p>
      </div>
      <div class="solution-grid">
        <div v-for="s in solutions" :key="s.title" v-reveal class="solution-card">
          <div class="solution-icon">
            <el-icon><component :is="s.icon" /></el-icon>
          </div>
          <h3>{{ s.title }}</h3>
          <p>{{ s.desc }}</p>
        </div>
      </div>
    </section>

    <!-- Download -->
    <section id="download" class="download">
      <div class="section-head" v-reveal>
        <span class="section-eyebrow">DOWNLOAD · 下载中心</span>
        <h2>多平台客户端与部署方案</h2>
        <p>Windows / macOS / Linux 客户端与 Docker 一键部署，满足不同环境需求</p>
      </div>
      <div class="download-grid">
        <div v-for="d in downloads" :key="d.platform" v-reveal class="download-card">
          <div class="download-os">{{ d.platform }}</div>
          <div class="download-version">{{ d.version }}</div>
          <div class="download-meta">
            <span>{{ d.size }}</span>
            <span class="download-note">{{ d.note }}</span>
          </div>
          <el-button type="primary" plain class="download-btn" @click="goDownload">
            <el-icon><Download /></el-icon>
            下载
          </el-button>
        </div>
      </div>
      <div class="download-enterprise" v-reveal>
        <div>
          <strong>需要企业私有化部署或 OEM 定制？</strong>
          <p>我们提供企业版源码授权、私有云部署与二次开发支持。</p>
        </div>
        <el-button type="primary" size="large" @click="router.push('/home#contact')">
          联系商务
          <el-icon class="btn-icon"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </section>

    <!-- Cases -->
    <section id="cases" class="cases">
      <div class="section-head light" v-reveal>
        <span class="section-eyebrow">REVIEWS · 客户案例</span>
        <h2>已经被不同场景验证</h2>
        <div class="case-rating">
          <span class="case-score">4.9</span>
          <span class="case-stars">
            <el-icon v-for="n in 5" :key="n"><Star /></el-icon>
          </span>
          <span class="case-rating-note">来自早期用户评价</span>
        </div>
      </div>
      <div class="case-grid">
        <div v-for="c in cases" :key="c.org" v-reveal class="case-card">
          <div class="case-org">{{ c.org }}</div>
          <div class="case-role">{{ c.role }}</div>
          <p class="case-result">“{{ c.result }}”</p>
          <div class="case-tags">
            <el-tag v-for="tag in c.tags" :key="tag" size="small" type="primary" effect="light">{{ tag }}</el-tag>
          </div>
        </div>
      </div>
    </section>

    <!-- Resources / Docs -->
    <section id="docs" class="resources">
      <div class="section-head" v-reveal>
        <span class="section-eyebrow">DOCS · 开发者与文档</span>
        <h2>快速接入与二次开发</h2>
        <p>完善的文档、API 与部署模板，降低使用与集成门槛</p>
      </div>
      <div class="resource-grid">
        <div v-for="r in resources" :key="r.title" v-reveal class="resource-card">
          <div class="resource-icon">
            <el-icon><component :is="r.icon" /></el-icon>
          </div>
          <h3>{{ r.title }}</h3>
          <p>{{ r.desc }}</p>
          <a class="resource-link" @click="router.push('/home')">
            查看文档 <el-icon><ArrowRight /></el-icon>
          </a>
        </div>
      </div>
    </section>

    <!-- About -->
    <section id="about" class="about">
      <div class="about-inner" v-reveal>
        <div class="about-photo">
          <img src="/img/library-hall.jpg" alt="图书馆藏书" loading="lazy" />
          <div class="glass-chip chip-a">
            <strong>2000+</strong><span>论文资料结构化</span>
          </div>
          <div class="glass-chip chip-b">
            <strong>50万+</strong><span>公开分享阅读</span>
          </div>
        </div>
        <div class="about-text">
          <span class="section-eyebrow">ABOUT · 关于知序</span>
          <h2>专注于知识管理的长期价值</h2>
          <p class="serif-quote">“知识的沉淀，比信息的堆砌更有价值。”</p>
          <p>
            知序（ZhiXu Tech）致力于用 AI 与图谱技术，帮助个人和组织把零散信息转化为结构化、可复用的知识资产。
            从 OCR 识别到 AI 整理，从个人知识库到企业知识中枢，知序的产品矩阵覆盖知识获取、整理、应用与传承的全生命周期。
          </p>
          <div class="about-values">
            <div><strong>本地优先</strong><span>数据自主可控</span></div>
            <div><strong>开放集成</strong><span>API 与插件扩展</span></div>
            <div><strong>持续进化</strong><span>紧跟大模型能力</span></div>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section class="faq">
      <div class="section-head light" v-reveal>
        <span class="section-eyebrow">FAQ · 常见问题</span>
        <h2>你可能想了解的</h2>
      </div>
      <div class="faq-list" v-reveal>
        <el-collapse>
          <el-collapse-item v-for="(item, idx) in faqs" :key="idx">
            <template #title>
              <div class="faq-title">
                <el-icon class="faq-icon"><QuestionFilled /></el-icon>
                <span>{{ item.q }}</span>
              </div>
            </template>
            <div class="faq-answer">{{ item.a }}</div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta">
      <div class="cta-inner" v-reveal>
        <div class="cta-eyebrow">GET STARTED · 现在开始</div>
        <h2>开启你的知识管理升级</h2>
        <p>注册账号后即可免费体验知序智能知识库 Web 版。</p>
        <div class="cta-actions">
          <el-button size="large" class="cta-primary" @click="enterWeb">
            Web 体验
            <el-icon class="btn-icon"><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" class="cta-ghost" @click="router.push('/home#contact')">
            联系我们
          </el-button>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer id="contact" class="landing-footer">
      <div class="footer-wordmark" aria-hidden="true">知序</div>
      <div class="footer-inner">
        <div class="footer-brand">
          <div class="brand-logo">知序</div>
          <div>
            <div class="footer-brand-name">知序 <span>ZhiXu Tech</span></div>
            <div class="footer-brand-desc">让知识创造价值</div>
          </div>
        </div>
        <div class="footer-col">
          <h4>产品</h4>
          <a @click="router.push('/notes')">智能知识库</a>
          <a @click="router.push('/home#products')">AI 工作台</a>
          <a @click="router.push('/home#products')">OCR 工具箱</a>
          <a @click="router.push('/home#products')">数据同步助手</a>
        </div>
        <div class="footer-col">
          <h4>资源</h4>
          <a @click="router.push('/home#docs')">快速开始</a>
          <a @click="router.push('/home#docs')">API 文档</a>
          <a @click="router.push('/home#docs')">部署指南</a>
          <a @click="router.push('/home#docs')">更新日志</a>
        </div>
        <div class="footer-col">
          <h4>公司</h4>
          <a @click="router.push('/home#about')">关于我们</a>
          <a @click="router.push('/home#cases')">客户案例</a>
          <a @click="router.push('/home#contact')">联系我们</a>
          <a @click="router.push('/home')">加入我们</a>
        </div>
        <div class="footer-col contact">
          <h4>联系我们</h4>
          <span><el-icon><Phone /></el-icon> 400-000-0000</span>
          <span><el-icon><Message /></el-icon> contact@zhixu.tech</span>
          <span><el-icon><Location /></el-icon> 中国 · 杭州</span>
        </div>
      </div>
      <div class="footer-bottom">
        <span>© 2026 知序 ZhiXu Tech. All rights reserved.</span>
        <div class="footer-bottom-links">
          <a @click="router.push('/home')">隐私政策</a>
          <a @click="router.push('/home')">服务条款</a>
          <a @click="router.push('/home')">ICP 备案</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.landing-page {
  --p: var(--zx-brand);
  min-height: 100vh;
  background: var(--zx-paper);
  color: var(--zx-ink);
  font-family:
    'Helvetica Neue',
    Helvetica,
    'PingFang SC',
    'Hiragino Sans GB',
    'Microsoft YaHei',
    Arial,
    sans-serif;
  overflow-x: clip;
}

.landing-page button:focus-visible,
.landing-page a:focus-visible {
  outline: 2px solid var(--zx-brand);
  outline-offset: 2px;
  border-radius: 8px;
}

.landing-page ::selection {
  background: var(--zx-brand);
  color: #fff;
}

section {
  padding: 96px 48px;
}

/* ---------- 滚动显现 ---------- */
.reveal {
  opacity: 0;
  transform: translateY(18px);
  transition:
    opacity 0.6s ease,
    transform 0.6s ease;
}

.reveal.revealed {
  opacity: 1;
  transform: none;
}

/* ---------- 通用眉题 ---------- */
.section-head {
  text-align: center;
  margin-bottom: 56px;
}

.section-head h2 {
  font-family: var(--zx-serif);
  font-size: 38px;
  font-weight: 900;
  letter-spacing: 3px;
  color: var(--zx-ink);
  margin-bottom: 12px;
}

.section-head p {
  font-size: 16px;
  color: #6b7280;
  max-width: 640px;
  margin: 0 auto;
  line-height: 1.7;
}

.section-eyebrow {
  display: inline-block;
  font-family: var(--zx-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2.5px;
  color: var(--zx-brand-ink);
  background: var(--zx-brand-soft);
  border: 1px solid #f5d9c6;
  padding: 7px 16px;
  border-radius: 999px;
  margin-bottom: 18px;
}

.section-head.light h2 {
  color: #ffffff;
}

.section-head.light p {
  color: #a3adc2;
}

.section-head.light .section-eyebrow {
  color: #ffb48a;
  background: rgba(242, 100, 30, 0.12);
  border-color: rgba(242, 100, 30, 0.35);
}

.btn-icon {
  margin-left: 6px;
}

/* 全站 CTA 按压回弹（对标 Wandor active:scale-95 的触感） */
.hero-primary:active,
.hero-ghost:active,
.product-action:active,
.download-btn:active,
.cta-primary:active,
.cta-ghost:active,
.step-btn:active {
  transform: scale(0.96);
}

.hero-primary,
.hero-ghost,
.product-action,
.download-btn,
.cta-primary,
.cta-ghost,
.step-btn {
  transition:
    transform 0.15s ease,
    background 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    color 0.2s ease;
}

/* ---------- Hero：纸上山水 ---------- */
.hero-paper {
  position: relative;
  overflow: hidden;
  padding: 88px 48px 0;
  background:
    radial-gradient(760px 380px at 50% -6%, rgba(242, 100, 30, 0.08), transparent 65%),
    var(--zx-paper);
}

.hero-center {
  position: relative;
  z-index: 2;
  max-width: 820px;
  margin: 0 auto;
  text-align: center;
}

.hero-eyebrow-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-bottom: 24px;
}

.hero-eyebrow {
  font-family: var(--zx-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--zx-brand-ink);
}

.seal {
  font-family: var(--zx-serif);
  font-weight: 900;
  font-size: 15px;
  line-height: 1;
  color: #fff;
  background: linear-gradient(145deg, #f2671f 0%, #d94f0e 100%);
  border-radius: 7px 10px 8px 11px;
  padding: 8px 9px 9px 10px;
  transform: rotate(-4deg);
  box-shadow:
    inset 0 0 0 1.5px rgba(255, 255, 255, 0.55),
    0 8px 18px var(--zx-brand-ring);
  letter-spacing: 2px;
  user-select: none;
}

.hero-paper h1 {
  font-family: var(--zx-serif);
  font-size: clamp(50px, 7vw, 96px);
  font-weight: 900;
  line-height: 1.18;
  letter-spacing: 4px;
  color: var(--zx-ink);
  margin-bottom: 22px;
}

.h1-accent {
  position: relative;
  color: var(--zx-brand);
  white-space: nowrap;
}

.h1-accent::after {
  content: '';
  position: absolute;
  left: 2px;
  right: 2px;
  bottom: 8px;
  height: 12px;
  border-radius: 999px;
  background: var(--zx-brand-soft);
  border: 1px solid #f5d9c6;
  z-index: -1;
}

.hero-paper h1 {
  position: relative;
  z-index: 0;
}

.hero-desc {
  font-size: 16px;
  line-height: 1.9;
  color: #4b5563;
  margin: 0 auto 30px;
  max-width: 600px;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-bottom: 26px;
}

.hero-primary {
  font-weight: 800;
  border-radius: 14px;
  padding: 13px 30px;
  height: auto;
  box-shadow: 0 14px 36px var(--zx-brand-ring);
}

.hero-actions .hero-ghost {
  font-weight: 700;
  border-radius: 14px;
  padding: 13px 30px;
  height: auto;
  background: #fff;
  border-color: #e8ddc9;
  color: var(--zx-ink);
}

.hero-actions .hero-ghost:hover {
  border-color: var(--zx-brand);
  color: var(--zx-brand-ink);
  background: #fff;
}

.hero-trust {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 20px;
  margin-bottom: 8px;
}

.hero-trust span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4b5563;
}

.hero-trust .el-icon {
  color: var(--zx-teal);
  font-size: 15px;
}

/* ---------- 现场问答玻璃卡 ---------- */
.demo-wrap {
  position: relative;
  z-index: 2;
  max-width: 701px;
  margin: 34px auto 0;
}

.demo-card {
  position: relative;
  text-align: left;
  min-height: 208px;
  background: rgba(255, 255, 255, 0.55);
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
  border: 3px solid #ffffff;
  border-radius: 44px;
  box-shadow: 0 0 4px 0 rgba(0, 0, 0, 0.15);
  padding: 30px 34px 28px;
  overflow: hidden;
}

.demo-label {
  font-family: var(--zx-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2.5px;
  color: var(--zx-brand-ink);
  margin-bottom: 16px;
}

.demo-q,
.demo-a {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.demo-q {
  margin-bottom: 14px;
}

.demo-avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zx-ink);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.demo-avatar.ai {
  background: var(--zx-brand);
}

.demo-q p {
  font-size: 17px;
  font-weight: 500;
  line-height: 1.625;
  color: #905831;
  padding-top: 4px;
}

.demo-a p {
  flex: 1;
  font-size: 15px;
  line-height: 1.85;
  color: #374151;
  min-height: 56px;
}

.typing-caret {
  display: inline-block;
  width: 2px;
  height: 1.1em;
  vertical-align: -0.2em;
  margin-left: 2px;
  background: var(--zx-brand);
  animation: caret-blink 0.9s steps(1) infinite;
}

@keyframes caret-blink {
  50% { opacity: 0; }
}

.demo-sources {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  padding-left: 42px;
}

.demo-src {
  font-size: 12px;
  color: var(--zx-brand-ink);
  background: var(--zx-brand-soft);
  border: 1px solid #f5d9c6;
  border-radius: 999px;
  padding: 4px 12px;
}

.demo-go {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 700;
  color: var(--zx-ink);
  cursor: pointer;
  margin-left: auto;
}

.demo-go:hover {
  color: var(--zx-brand-ink);
  gap: 8px;
}

/* ---------- 知识山水 ---------- */
.panorama {
  position: relative;
  margin-top: -64px;
  line-height: 0;
}

.panorama-svg {
  display: block;
  width: 100%;
  height: clamp(230px, 27vw, 360px);
}

.cloud {
  animation: cloud-drift 26s ease-in-out infinite alternate;
}

.cloud.c2 {
  animation-duration: 34s;
  animation-delay: -12s;
}

@keyframes cloud-drift {
  from { transform: translateX(-26px); }
  to { transform: translateX(30px); }
}

.float-book {
  animation: book-bob 5s ease-in-out infinite alternate;
}

.float-book.b2 {
  animation-duration: 6.5s;
  animation-delay: -2s;
}

.float-book.b3 {
  animation-duration: 7.5s;
  animation-delay: -4s;
}

@keyframes book-bob {
  from { transform: translateY(0); }
  to { transform: translateY(-12px); }
}

/* ---------- 跑马灯 ---------- */
.marquee {
  overflow: hidden;
  background: var(--zx-ink);
  border-top: 1px solid #2a364f;
  border-bottom: 1px solid #2a364f;
  padding: 13px 0;
}

.marquee-track {
  display: flex;
  width: max-content;
  animation: marquee 26s linear infinite;
}

.marquee:hover .marquee-track {
  animation-play-state: paused;
}

.marquee-chunk {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.marquee-item {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  padding: 0 26px;
  font-family: var(--zx-serif);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  color: #e6e1d5;
  white-space: nowrap;
}

.marquee-item i {
  font-style: normal;
  font-size: 9px;
  color: var(--zx-brand);
}

.marquee-item em {
  font-family: var(--zx-mono);
  font-style: normal;
  font-size: 10px;
  letter-spacing: 2.5px;
  color: #7c8aa5;
}

@keyframes marquee {
  to { transform: translateX(-50%); }
}

/* ---------- 五步成序 ---------- */
.how {
  background: var(--zx-paper);
}

.stepper {
  max-width: 1080px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 28px;
  align-items: stretch;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-btn {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: baseline;
  gap: 12px;
  text-align: left;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid #f0e7d8;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.step-btn:hover {
  transform: translateX(3px);
  border-color: #e8d5bd;
}

.step-btn.active {
  background: var(--zx-ink);
  border-color: var(--zx-ink);
}

.step-btn.active .step-name {
  color: #fff;
}

.step-btn.active .step-num {
  color: #ffb48a;
}

.step-num {
  font-family: var(--zx-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #c9bfae;
}

.step-name {
  font-size: 16px;
  font-weight: 800;
  color: var(--zx-ink);
}

.step-en {
  font-family: var(--zx-mono);
  font-size: 9px;
  letter-spacing: 1.5px;
  color: #a8a29e;
}

.step-panel {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 30px;
  align-items: center;
  background: #fff;
  border: 1px solid #f0e7d8;
  border-top: 4px solid var(--zx-brand);
  border-radius: 20px;
  padding: 34px 36px;
  box-shadow: 0 20px 50px rgba(23, 32, 47, 0.07);
}

.step-visual {
  height: 190px;
  border-radius: 14px;
  background: #faf7f0;
  border: 1px solid #f0e7d8;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  overflow: hidden;
}

.mini-tray {
  width: 110px;
  height: 76px;
  border-radius: 12px;
  border: 2px dashed #d8c9ae;
  position: relative;
}

.mini-chips {
  position: absolute;
  display: flex;
  gap: 6px;
}

.mini-chips span {
  width: 26px;
  height: 34px;
  border-radius: 6px;
  background: var(--zx-brand);
  box-shadow: 0 6px 14px var(--zx-brand-ring);
}

.mini-chips span:nth-child(2) {
  background: var(--zx-iris);
  transform: translateY(-8px);
}

.mini-chips span:nth-child(3) {
  background: var(--zx-teal);
  transform: translateY(4px);
}

.step-visual:has(.mini-tray) {
  position: relative;
}

.mini-scan {
  position: relative;
  width: 130px;
  background: #fff;
  border: 1px solid #eee4d2;
  border-radius: 10px;
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  overflow: hidden;
}

.mini-line {
  height: 7px;
  border-radius: 999px;
  background: #ece4d3;
}

.mini-line.w90 { width: 90%; }
.mini-line.w65 { width: 65%; }
.mini-line.w80 { width: 80%; }

.mini-scanline {
  position: absolute;
  left: 8px;
  right: 8px;
  top: 8px;
  height: 2px;
  border-radius: 999px;
  background: var(--zx-brand);
  box-shadow: 0 0 10px var(--zx-brand);
  animation: scan 2.4s ease-in-out infinite alternate;
}

@keyframes scan {
  from { top: 8px; }
  to { top: calc(100% - 10px); }
}

.mini-outline {
  display: flex;
  flex-direction: column;
  gap: 9px;
  width: 130px;
}

.mini-outline span {
  height: 9px;
  border-radius: 999px;
  background: #e3d5bc;
}

.mini-outline .l1 {
  width: 100%;
  background: var(--zx-ink);
}

.mini-outline .l2 {
  width: 82%;
  margin-left: 14px;
}

.mini-outline .l3 {
  width: 64%;
  margin-left: 28px;
  background: var(--zx-brand);
}

.mini-graph {
  position: relative;
  width: 140px;
  height: 110px;
}

.mini-graph::before {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  top: 50%;
  height: 2px;
  background: repeating-linear-gradient(to right, #d8c9ae 0 6px, transparent 6px 12px);
}

.mini-graph i {
  position: absolute;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 4px 12px rgba(23, 32, 47, 0.18);
}

.mini-graph .g1 { left: 4px; top: 45px; background: var(--zx-brand); }
.mini-graph .g2 { left: 60px; top: 12px; background: var(--zx-iris); }
.mini-graph .g3 { left: 60px; top: 78px; background: var(--zx-teal); }
.mini-graph .g4 { right: 4px; top: 45px; background: var(--zx-amber, #d9930d); }

.mini-chat {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.bubble {
  max-width: 88%;
  font-size: 12.5px;
  line-height: 1.6;
  padding: 9px 13px;
  border-radius: 13px;
}

.bubble.user {
  align-self: flex-end;
  background: var(--zx-brand-soft);
  border: 1px solid #f5d9c6;
  color: var(--zx-ink);
  border-bottom-right-radius: 4px;
}

.bubble.ai {
  align-self: flex-start;
  background: #fff;
  border: 1px solid #eee4d2;
  color: #374151;
  border-bottom-left-radius: 4px;
}

.step-info-icon {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--zx-ink);
  color: #fff;
  font-size: 22px;
  margin-bottom: 14px;
}

.step-info h3 {
  font-family: var(--zx-serif);
  font-size: 26px;
  font-weight: 900;
  letter-spacing: 2px;
  color: var(--zx-ink);
  margin-bottom: 8px;
}

.step-info h3 em {
  font-family: var(--zx-mono);
  font-style: normal;
  font-size: 11px;
  letter-spacing: 2px;
  color: #a8a29e;
  margin-left: 10px;
  font-weight: 400;
}

.step-desc {
  font-size: 15px;
  color: #4b5563;
  margin-bottom: 16px;
}

.step-points {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.step-points li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
}

.step-points .el-icon {
  color: var(--zx-teal);
  font-size: 15px;
  flex-shrink: 0;
  margin-top: 3px;
}

/* ---------- Stats ---------- */
.stats {
  padding: 52px 48px;
  background: var(--zx-night);
  color: #ffffff;
}

.stats-inner {
  max-width: 1020px;
  margin: 0 auto;
  display: flex;
  justify-content: space-around;
  gap: 24px;
}

.stat-item {
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-item strong {
  font-family: var(--zx-display);
  font-size: 42px;
  font-weight: 800;
  color: #ffb48a;
  line-height: 1.1;
}

.stat-item span {
  font-size: 14px;
  color: #e6e1d5;
}

.stat-item em {
  font-family: var(--zx-mono);
  font-style: normal;
  font-size: 10px;
  letter-spacing: 2.5px;
  color: #7c8aa5;
}

/* ---------- Products ---------- */
.products {
  background: var(--zx-paper);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  max-width: 1220px;
  margin: 0 auto;
}

.product-card {
  --p: var(--zx-brand);
  --ps: var(--zx-brand-soft);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0 28px 30px;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #f0e7d8;
  border-top: 4px solid var(--p);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease;
}

.product-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.25s ease;
  background: radial-gradient(
    320px circle at var(--mx, 50%) var(--my, 50%),
    rgba(242, 100, 30, 0.1),
    transparent 65%
  );
}

.product-card:hover::after {
  opacity: 1;
}

.product-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 20px 44px rgba(23, 32, 47, 0.1);
}

.product-card.primary {
  box-shadow: 0 16px 40px rgba(23, 32, 47, 0.08);
}

.product-card.coming {
  background: #fffdf8;
}

.product-photo {
  position: relative;
  margin: 0 -28px 20px;
  height: 150px;
  overflow: hidden;
}

.product-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transform: scale(1.02);
  transition: transform 0.4s ease;
}

.product-card:hover .product-photo img {
  transform: scale(1.08);
}

.product-photo::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, transparent 55%, rgba(255, 255, 255, 0.9));
}

.product-photo-tag {
  position: absolute;
  left: 14px;
  bottom: 10px;
  z-index: 1;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: var(--p);
  border-radius: 999px;
  padding: 3px 10px;
}

.product-icon {
  width: 52px;
  height: 52px;
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ps);
  color: var(--p);
  font-size: 24px;
  margin-bottom: 16px;
}

.product-en {
  font-family: var(--zx-mono);
  font-size: 10px;
  letter-spacing: 2px;
  color: #a8a29e;
  margin-bottom: 6px;
}

.product-card h3 {
  font-family: var(--zx-display);
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: var(--zx-ink);
  margin-bottom: 10px;
}

.product-card > p {
  flex: 1;
  font-size: 14px;
  line-height: 1.75;
  color: #4b5563;
  margin-bottom: 16px;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.product-action {
  width: 100%;
  border-radius: 12px;
  font-weight: 700;
}

/* ---------- Solutions ---------- */
.solutions {
  background: var(--zx-night);
  color: #ffffff;
}

.solution-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  max-width: 1120px;
  margin: 0 auto;
}

.solution-card {
  padding: 30px 28px;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 18px;
  transition:
    background 0.22s ease,
    transform 0.22s ease;
}

.solution-card:hover {
  background: rgba(255, 255, 255, 0.08);
  transform: translateY(-4px);
}

.solution-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(242, 100, 30, 0.16);
  color: #ffb48a;
  font-size: 22px;
  margin-bottom: 18px;
}

.solution-card h3 {
  font-family: var(--zx-display);
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: #ffffff;
  margin-bottom: 10px;
}

.solution-card p {
  font-size: 14px;
  line-height: 1.75;
  color: #a3adc2;
}

/* ---------- Download ---------- */
.download-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  max-width: 1100px;
  margin: 0 auto 30px;
}

.download-card {
  text-align: center;
  padding: 30px 26px;
  background: #ffffff;
  border: 1px solid #f0e7d8;
  border-radius: 18px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.download-card:hover {
  border-color: var(--zx-brand);
  box-shadow: 0 14px 32px rgba(23, 32, 47, 0.08);
  transform: translateY(-4px);
}

.download-os {
  font-family: var(--zx-display);
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: var(--zx-ink);
  margin-bottom: 8px;
}

.download-version {
  display: inline-block;
  font-family: var(--zx-mono);
  font-size: 11px;
  letter-spacing: 1px;
  color: var(--zx-brand-ink);
  background: var(--zx-brand-soft);
  border-radius: 999px;
  padding: 4px 12px;
  margin-bottom: 12px;
}

.download-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 20px;
}

.download-note {
  color: #a8a29e;
}

.download-btn {
  width: 100%;
  border-radius: 12px;
  font-weight: 700;
}

.download-enterprise {
  max-width: 920px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  background: var(--zx-ink);
  border-radius: 20px;
  color: #fff;
}

.download-enterprise strong {
  font-size: 18px;
}

.download-enterprise p {
  font-size: 14px;
  color: #a3adc2;
  margin-top: 4px;
}

/* ---------- Cases ---------- */
.cases {
  background: #faf6ee;
}

.case-rating {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 18px;
}

.case-score {
  font-family: var(--zx-display);
  font-size: 30px;
  font-weight: 800;
  color: var(--zx-ink);
}

.case-stars {
  display: inline-flex;
  gap: 3px;
  color: #d9930d;
  font-size: 17px;
}

.case-rating-note {
  font-size: 13px;
  color: #6b7280;
}

.case-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  max-width: 1100px;
  margin: 0 auto;
}

.case-card {
  padding: 30px 28px;
  background: #ffffff;
  border-radius: 18px;
  border: 1px solid #f0e7d8;
  transition:
    box-shadow 0.22s ease,
    transform 0.22s ease;
}

.case-card:hover {
  box-shadow: 0 14px 34px rgba(23, 32, 47, 0.08);
  transform: translateY(-4px);
}

.case-org {
  font-size: 17px;
  font-weight: 800;
  color: var(--zx-ink);
  margin-bottom: 4px;
}

.case-role {
  font-family: var(--zx-mono);
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--zx-brand-ink);
  margin-bottom: 14px;
}

.case-result {
  font-size: 15px;
  line-height: 1.8;
  color: #374151;
  margin-bottom: 18px;
}

.case-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ---------- Resources ---------- */
.resources {
  background: var(--zx-paper);
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  max-width: 1100px;
  margin: 0 auto;
}

.resource-card {
  padding: 28px 26px;
  background: #ffffff;
  border-radius: 18px;
  border: 1px solid #f0e7d8;
  transition:
    background 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.resource-card:hover {
  box-shadow: 0 14px 32px rgba(23, 32, 47, 0.08);
  transform: translateY(-4px);
}

.resource-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zx-ink);
  color: #fff;
  font-size: 20px;
  margin-bottom: 16px;
}

.resource-card h3 {
  font-size: 17px;
  font-weight: 700;
  color: var(--zx-ink);
  margin-bottom: 8px;
}

.resource-card p {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 16px;
  line-height: 1.7;
}

.resource-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--zx-brand-ink);
  cursor: pointer;
}

.resource-link:hover {
  gap: 8px;
}

/* ---------- About ---------- */
.about {
  background: #fff;
  border-top: 1px solid #f3ece0;
}

.about-inner {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 60px;
}

.about-text {
  flex: 1;
}

.about-text h2 {
  font-family: var(--zx-serif);
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 3px;
  color: var(--zx-ink);
  margin-bottom: 20px;
}

.about-text > p {
  font-size: 16px;
  line-height: 1.85;
  color: #4b5563;
  margin-bottom: 16px;
}

.about-values {
  display: flex;
  gap: 32px;
  margin-top: 28px;
}

.about-values div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.about-values strong {
  font-family: var(--zx-display);
  font-size: 19px;
  color: var(--zx-ink);
}

.about-values span {
  font-size: 13px;
  color: #6b7280;
}

.about-photo {
  position: relative;
  width: 400px;
  flex-shrink: 0;
  border-radius: 22px;
}

.about-photo > img {
  display: block;
  width: 100%;
  height: 440px;
  object-fit: cover;
  border-radius: 22px;
  box-shadow: 0 30px 70px rgba(23, 32, 47, 0.18);
}

.glass-chip {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
  border: 1.5px solid rgba(255, 255, 255, 0.75);
  box-shadow:
    0 16px 40px rgba(23, 32, 47, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.glass-chip strong {
  font-family: var(--zx-display);
  font-size: 22px;
  font-weight: 800;
  color: var(--zx-ink);
  line-height: 1.1;
}

.glass-chip span {
  font-size: 12px;
  color: #4b5563;
}

.glass-chip.chip-a {
  top: 28px;
  left: -28px;
}

.glass-chip.chip-b {
  bottom: 32px;
  right: -24px;
}

.serif-quote {
  font-family: var(--zx-serif);
  font-size: 21px;
  font-weight: 600;
  line-height: 1.7;
  color: var(--zx-ink);
  border-left: 3px solid var(--zx-brand);
  padding-left: 18px;
  margin-bottom: 20px;
}

/* ---------- FAQ ---------- */
.faq {
  background: var(--zx-night);
  color: #ffffff;
}

.faq-list {
  max-width: 760px;
  margin: 0 auto;
}

.faq-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #ffffff;
}

.faq-icon {
  color: var(--zx-brand);
}

.faq-answer {
  font-size: 14px;
  line-height: 1.85;
  color: #e6e1d5;
  padding-left: 28px;
}

:deep(.el-collapse) {
  border: none;
}

:deep(.el-collapse-item__header) {
  background: transparent;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  color: #ffffff;
  padding: 16px 0;
  font-size: 15px;
  font-weight: 600;
}

:deep(.el-collapse-item__wrap) {
  background: transparent;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

:deep(.el-collapse-item__content) {
  color: #a3adc2;
  padding-bottom: 20px;
}

/* ---------- CTA ---------- */
.cta {
  position: relative;
  padding: 96px 48px;
  background:
    linear-gradient(rgba(17, 26, 46, 0.88), rgba(17, 26, 46, 0.94)),
    url('/img/books-stack.jpg') center / cover no-repeat,
    var(--zx-night);
  text-align: center;
  overflow: hidden;
}

.cta-eyebrow {
  font-family: var(--zx-mono);
  font-size: 11px;
  letter-spacing: 3px;
  color: #ffb48a;
  margin-bottom: 16px;
}

.cta h2 {
  font-family: var(--zx-serif);
  font-size: 40px;
  font-weight: 900;
  letter-spacing: 4px;
  color: #fff;
  margin-bottom: 12px;
}

.cta p {
  font-size: 16px;
  color: #a3adc2;
  margin-bottom: 28px;
}

.cta-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
}

.cta-primary {
  font-weight: 800;
  border-radius: 14px;
  padding: 12px 30px;
  height: auto;
  background: var(--zx-brand);
  border-color: var(--zx-brand);
  color: #fff;
  box-shadow: 0 14px 34px rgba(242, 100, 30, 0.35);
}

.cta-primary:hover {
  background: var(--zx-brand-ink);
  border-color: var(--zx-brand-ink);
  color: #fff;
}

.cta-actions .cta-ghost {
  font-weight: 700;
  border-radius: 14px;
  padding: 12px 30px;
  height: auto;
  background: transparent;
  border-color: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.cta-actions .cta-ghost:hover,
.cta-actions .cta-ghost:focus-visible {
  border-color: #fff;
  color: #fff;
  background: rgba(255, 255, 255, 0.06);
}

/* ---------- Footer ---------- */
.landing-footer {
  position: relative;
  background: #0c1222;
  color: #d1d5db;
  padding: 40px 48px 24px;
  overflow: hidden;
}

.footer-wordmark {
  font-family: var(--zx-serif);
  font-weight: 900;
  font-size: clamp(120px, 22vw, 300px);
  line-height: 0.9;
  text-align: center;
  letter-spacing: 0.1em;
  color: transparent;
  -webkit-text-stroke: 1px rgba(255, 255, 255, 0.09);
  user-select: none;
  margin-bottom: 8px;
}

.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr 1.5fr;
  gap: 40px;
  margin-bottom: 48px;
}

.footer-brand {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.footer-brand .brand-logo {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f2641e 0%, #d94f0e 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
}

.footer-brand-name {
  font-family: var(--zx-display);
  font-size: 18px;
  font-weight: 800;
  color: #ffffff;
  margin-bottom: 4px;
}

.footer-brand-name span {
  font-family: var(--zx-mono);
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 1.5px;
  color: #7c8aa5;
}

.footer-brand-desc {
  font-size: 13px;
  color: #7c8aa5;
}

.footer-col h4 {
  font-size: 13px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 16px;
  font-family: var(--zx-mono);
  letter-spacing: 2px;
}

.footer-col a,
.footer-col span {
  display: block;
  font-size: 14px;
  color: #7c8aa5;
  margin-bottom: 10px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.footer-col a:hover {
  color: #ffb48a;
}

.footer-col.contact span {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: default;
}

.footer-bottom {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 24px;
  border-top: 1px solid #1c2740;
  font-size: 13px;
  color: #5b6b8c;
}

.footer-bottom-links {
  display: flex;
  gap: 24px;
}

.footer-bottom-links a {
  color: #5b6b8c;
  cursor: pointer;
  transition: color 0.2s ease;
}

.footer-bottom-links a:hover {
  color: #ffb48a;
}

/* ---------- 减弱动效 ---------- */
@media (prefers-reduced-motion: reduce) {
  .reveal {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .cloud,
  .float-book,
  .typing-caret {
    animation: none !important;
  }

  .product-card:hover,
  .solution-card:hover,
  .download-card:hover,
  .case-card:hover,
  .resource-card:hover,
  .step-btn:hover {
    transform: none;
  }

  .marquee-track {
    animation: none;
  }

  .marquee {
    display: none;
  }

  .mini-scanline {
    animation: none;
  }
}

@media (max-width: 1024px) {
  .product-grid,
  .solution-grid,
  .download-grid,
  .resource-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .case-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stepper {
    grid-template-columns: 1fr;
  }

  .step-list {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8px;
  }

  .step-btn {
    grid-template-columns: 1fr;
    justify-items: center;
    text-align: center;
    gap: 4px;
    padding: 12px 8px;
  }

  .step-btn .step-en {
    display: none;
  }

  .about-inner {
    flex-direction: column;
  }

  .about-photo {
    width: 100%;
    max-width: 520px;
  }

  .about-photo > img {
    height: 320px;
  }

  .glass-chip.chip-a {
    left: 12px;
  }

  .glass-chip.chip-b {
    right: 12px;
  }

  .footer-inner {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  section {
    padding: 60px 22px;
  }

  .hero-paper {
    padding: 56px 22px 0;
  }

  .hero-paper h1 {
    font-size: 46px;
    letter-spacing: 2px;
  }

  .hero-actions,
  .cta-actions {
    flex-direction: column;
  }

  .demo-card {
    border-radius: 28px;
    padding: 22px 20px;
  }

  .demo-sources {
    padding-left: 0;
  }

  .demo-go {
    margin-left: 0;
  }

  .stepper {
    grid-template-columns: 1fr;
  }

  .step-list {
    grid-template-columns: repeat(5, 1fr);
  }

  .step-btn .step-name {
    font-size: 13px;
  }

  .step-panel {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .step-visual {
    height: 160px;
  }

  .product-grid,
  .solution-grid,
  .download-grid,
  .resource-grid,
  .case-grid {
    grid-template-columns: 1fr;
  }

  .stats-inner {
    flex-wrap: wrap;
    gap: 20px;
  }

  .stat-item {
    flex: 1 1 40%;
  }

  .stat-item strong {
    font-size: 30px;
  }

  .download-enterprise {
    flex-direction: column;
    align-items: flex-start;
  }

  .about-values {
    flex-direction: column;
    gap: 16px;
  }

  .footer-inner {
    grid-template-columns: 1fr;
  }

  .footer-bottom {
    flex-direction: column;
    gap: 12px;
    text-align: center;
  }
}
</style>
