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
  Upload,
  Picture
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

/* ---------- 星图数据：散落成序的现场 ---------- */
interface StarNode {
  x: number
  y: number
  r: number
  color: string
  label?: string
  halo?: boolean
}

const NODES: StarNode[] = [
  { x: 300, y: 250, r: 11, color: '#f2641e', label: '个人知识库', halo: true },
  { x: 180, y: 150, r: 7, color: '#7a5af8', label: 'OCR' },
  { x: 420, y: 140, r: 7, color: '#0ca789', label: '问答', halo: true },
  { x: 150, y: 320, r: 6, color: '#d9930d', label: '笔记' },
  { x: 450, y: 330, r: 6, color: '#7a5af8', label: '图谱' },
  { x: 250, y: 90, r: 5, color: '#0ca789' },
  { x: 370, y: 80, r: 5, color: '#d9930d' },
  { x: 90, y: 220, r: 5, color: '#f2641e' },
  { x: 510, y: 230, r: 5, color: '#0ca789' },
  { x: 220, y: 420, r: 6, color: '#f2641e', label: '整理' },
  { x: 390, y: 430, r: 5, color: '#7a5af8' },
  { x: 300, y: 350, r: 5, color: '#d9930d' },
  { x: 480, y: 420, r: 4, color: '#0ca789' }
]

const EDGES: Array<[number, number]> = [
  [0, 1], [0, 2], [0, 3], [0, 4], [0, 9], [0, 11],
  [1, 5], [1, 7], [2, 6], [2, 8], [4, 9], [4, 10], [10, 11], [4, 11], [10, 12]
]

const STARS = [
  [40, 60], [120, 420], [200, 40], [330, 30], [470, 60], [560, 140],
  [60, 330], [130, 470], [250, 480], [420, 480], [540, 330], [30, 140],
  [350, 200], [240, 300], [500, 90], [90, 90]
]

const FRAGMENTS = [
  { icon: Document, label: 'PDF 报告', x: '2%', y: '12%', delay: '0s' },
  { icon: Picture, label: '一张截图', x: '86%', y: '8%', delay: '0.8s' },
  { icon: EditPen, label: '灵感速记', x: '0%', y: '66%', delay: '1.6s' },
  { icon: Notebook, label: '读书笔记', x: '88%', y: '70%', delay: '2.4s' }
]

/* ---------- Hero 视差 ---------- */
const heroRef = ref<HTMLElement | null>(null)

const onHeroMove = (e: MouseEvent) => {
  if (reduceMotion) return
  if (typeof window !== 'undefined' && window.matchMedia('(pointer: coarse)').matches) return
  const el = heroRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width - 0.5
  const y = (e.clientY - rect.top) / rect.height - 0.5
  el.style.setProperty('--px', (x * 14).toFixed(1))
  el.style.setProperty('--py', (y * 10).toFixed(1))
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

onMounted(startStepTimer)
onBeforeUnmount(stopStepTimer)

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

    <!-- Hero：活的星图 -->
    <section
      ref="heroRef"
      class="hero-dark"
      @mousemove="onHeroMove"
    >
      <div class="hero-dark-inner">
        <div class="hero-copy">
          <div class="hero-eyebrow-row">
            <span class="seal" aria-hidden="true">知序</span>
            <span class="hero-eyebrow">FROM CHAOS, TO COSMOS</span>
          </div>
          <h1>化散落<br><span class="h1-grad">为有序</span></h1>
          <p class="hero-desc">
            照片、截图、文档、灵感——散落的信息在这里被识别、整理、连成图谱，
            变成一个随时可问的个人知识库。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" class="hero-primary" @click="enterWeb">
              免费开始整理
              <el-icon class="btn-icon"><ArrowRight /></el-icon>
            </el-button>
            <el-button size="large" class="hero-ghost-dark" @click="router.push('/home#how')">
              看看怎么运作
            </el-button>
          </div>
          <div class="hero-trust">
            <span><el-icon><Check /></el-icon> 免登录体验</span>
            <span><el-icon><Check /></el-icon> 本地优先</span>
            <span><el-icon><Check /></el-icon> 私有化部署</span>
          </div>
        </div>

        <div class="constellation" aria-hidden="true">
          <svg viewBox="0 0 600 520" class="stars-svg">
            <circle
              v-for="([sx, sy], i) in STARS"
              :key="`s${i}`"
              :cx="sx"
              :cy="sy"
              r="1.1"
              fill="#ffffff"
              opacity="0.35"
            />
            <line
              v-for="([a, b], i) in EDGES"
              :key="`e${i}`"
              :x1="NODES[a].x"
              :y1="NODES[a].y"
              :x2="NODES[b].x"
              :y2="NODES[b].y"
              class="edge"
              :style="{ animationDelay: `${0.3 + i * 0.12}s` }"
              pathLength="1"
            />
            <g
              v-for="(n, i) in NODES"
              :key="`n${i}`"
              class="node"
              :style="{ animationDelay: `${0.5 + i * 0.1}s` }"
            >
              <circle
                v-if="n.halo"
                :cx="n.x"
                :cy="n.y"
                :r="n.r + 4"
                :fill="n.color"
                class="halo"
                :style="{ animationDelay: `${i * 0.7}s` }"
              />
              <circle :cx="n.x" :cy="n.y" :r="n.r" :fill="n.color" class="core" />
              <text
                v-if="n.label"
                :x="n.x"
                :y="n.y - n.r - 10"
                text-anchor="middle"
                class="node-label"
              >{{ n.label }}</text>
            </g>
            <circle class="traveler t1" r="2.6" fill="#ffb48a">
              <animateMotion dur="7s" repeatCount="indefinite" path="M180,150 L300,250 L420,140" />
            </circle>
            <circle class="traveler t2" r="2.2" fill="#b9a5ff">
              <animateMotion dur="9s" repeatCount="indefinite" path="M150,320 L220,420 L300,350 L390,430" />
            </circle>
            <circle class="traveler t3" r="2.2" fill="#5fd8c8">
              <animateMotion dur="11s" repeatCount="indefinite" path="M450,330 L390,430 L300,350" />
            </circle>
          </svg>
          <div
            v-for="(f, i) in FRAGMENTS"
            :key="f.label"
            class="fragment"
            :style="{ left: f.x, top: f.y, animationDelay: f.delay }"
          >
            <el-icon><component :is="f.icon" /></el-icon>
            <span>{{ f.label }}</span>
            <em>FRAGMENT_0{{ i + 1 }}</em>
          </div>
        </div>
      </div>
      <div class="scroll-hint" aria-hidden="true">
        <span>SCROLL</span>
        <i />
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

/* ---------- Hero：暗夜星图 ---------- */
.hero-dark {
  --px: 0;
  --py: 0;
  position: relative;
  overflow: hidden;
  padding: 96px 48px 70px;
  background:
    radial-gradient(900px 480px at 82% -10%, rgba(242, 100, 30, 0.16), transparent 62%),
    radial-gradient(700px 460px at 8% 20%, rgba(122, 90, 248, 0.14), transparent 60%),
    radial-gradient(560px 420px at 50% 115%, rgba(12, 167, 137, 0.1), transparent 60%),
    linear-gradient(180deg, #0d1424 0%, #111a2e 100%);
  color: #fff;
}

.hero-dark-inner {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 48px;
}

.hero-copy {
  flex: 1;
  max-width: 520px;
  position: relative;
  z-index: 2;
}

.hero-eyebrow-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}

.hero-eyebrow {
  font-family: var(--zx-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 3px;
  color: #ffb48a;
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
    0 8px 18px rgba(242, 100, 30, 0.35);
  letter-spacing: 2px;
  user-select: none;
}

.hero-dark h1 {
  font-family: var(--zx-serif);
  font-size: clamp(56px, 7.4vw, 104px);
  font-weight: 900;
  line-height: 1.16;
  letter-spacing: 6px;
  color: #fff;
  margin-bottom: 24px;
}

.h1-grad {
  background: linear-gradient(100deg, #ff8a4d 0%, #f2641e 45%, #e8a13c 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-desc {
  font-size: 16px;
  line-height: 1.9;
  color: #b9c2d4;
  margin-bottom: 32px;
  max-width: 480px;
}

.hero-actions {
  display: flex;
  gap: 14px;
  margin-bottom: 28px;
}

.hero-primary {
  font-weight: 800;
  border-radius: 14px;
  padding: 13px 30px;
  height: auto;
  box-shadow: 0 14px 36px rgba(242, 100, 30, 0.4);
}

.hero-ghost-dark {
  font-weight: 700;
  border-radius: 14px;
  padding: 13px 30px;
  height: auto;
  background: transparent;
  border-color: rgba(255, 255, 255, 0.28);
  color: #fff;
}

.hero-actions .hero-ghost-dark:hover,
.hero-actions .hero-ghost-dark:focus-visible {
  border-color: #fff;
  color: #fff;
  background: rgba(255, 255, 255, 0.07);
}

.hero-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.hero-trust span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #cfd6e4;
}

.hero-trust .el-icon {
  color: #5fd8c8;
  font-size: 15px;
}

/* ---------- 星座 ---------- */
.constellation {
  position: relative;
  width: 560px;
  flex-shrink: 0;
  transform: translate3d(calc(var(--px, 0) * 1px), calc(var(--py, 0) * 1px), 0);
  transition: transform 0.35s ease-out;
}

.stars-svg {
  display: block;
  width: 100%;
  height: auto;
  animation: constellation-drift 11s ease-in-out infinite alternate;
}

@keyframes constellation-drift {
  from { transform: translateY(-5px); }
  to { transform: translateY(7px); }
}

.edge {
  stroke: rgba(255, 255, 255, 0.2);
  stroke-width: 1.2;
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: draw 1.4s ease forwards;
}

@keyframes draw {
  to { stroke-dashoffset: 0; }
}

.node {
  opacity: 0;
  transform: scale(0.3);
  transform-box: fill-box;
  transform-origin: center;
  animation: pop 0.7s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

@keyframes pop {
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.node .core {
  filter: drop-shadow(0 0 7px currentColor);
}

.node-label {
  font-family: var(--zx-mono);
  font-size: 12px;
  letter-spacing: 2px;
  fill: #cfd6e4;
}

.halo {
  opacity: 0.45;
  transform-box: fill-box;
  transform-origin: center;
  animation: halo 3.2s ease-out infinite;
}

@keyframes halo {
  0% {
    transform: scale(1);
    opacity: 0.45;
  }
  100% {
    transform: scale(2.1);
    opacity: 0;
  }
}

.traveler {
  opacity: 0.9;
  filter: drop-shadow(0 0 5px currentColor);
}

.fragment {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px 9px 10px;
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.07);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #e6e1d5;
  font-size: 12.5px;
  font-weight: 600;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.3);
  animation: fragment-float 5.5s ease-in-out infinite alternate;
}

.fragment .el-icon {
  font-size: 16px;
  color: #ffb48a;
}

.fragment em {
  font-family: var(--zx-mono);
  font-style: normal;
  font-size: 9px;
  letter-spacing: 1.5px;
  color: #7c8aa5;
}

@keyframes fragment-float {
  from { transform: translateY(-7px); }
  to { transform: translateY(9px); }
}

.scroll-hint {
  position: absolute;
  left: 50%;
  bottom: 18px;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-family: var(--zx-mono);
  font-size: 9px;
  letter-spacing: 3px;
  color: #5b6b8c;
}

.scroll-hint i {
  display: block;
  width: 1px;
  height: 34px;
  background: linear-gradient(to bottom, #5b6b8c, transparent);
  position: relative;
  overflow: hidden;
}

.scroll-hint i::after {
  content: '';
  position: absolute;
  left: 0;
  top: -40%;
  width: 100%;
  height: 40%;
  background: #ffb48a;
  animation: hint-drop 1.8s ease-in-out infinite;
}

@keyframes hint-drop {
  to { top: 110%; }
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
  background: rgba(255, 255, 255, 0.72);
  -webkit-backdrop-filter: blur(14px);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow: 0 16px 40px rgba(23, 32, 47, 0.16);
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

  .stars-svg,
  .edge,
  .node,
  .halo,
  .fragment,
  .scroll-hint i::after {
    animation: none !important;
  }

  .edge {
    stroke-dashoffset: 0;
  }

  .node {
    opacity: 1;
    transform: none;
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
  .hero-dark-inner {
    flex-direction: column;
  }

  .hero-copy {
    max-width: 100%;
  }

  .constellation {
    width: 100%;
    max-width: 560px;
  }

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

  .hero-dark {
    padding: 56px 22px 60px;
  }

  .hero-dark h1 {
    font-size: 52px;
    letter-spacing: 3px;
  }

  .hero-actions,
  .cta-actions {
    flex-direction: column;
  }

  .fragment {
    display: none;
  }

  .scroll-hint {
    display: none;
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
