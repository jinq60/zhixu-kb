<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  ArrowRight,
  Download,
  Notebook,
  ChatDotRound,
  Share,
  MagicStick,
  Search,
  Monitor,
  SetUp,
  DataLine,
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

/** 产品矩阵：color 为该产品的主题色（导航 mega 菜单与 Tab 共用同一套） */
const products = [
  {
    name: '知序智能知识库',
    en: 'KNOWLEDGE BASE',
    desc: 'OCR 识别、AI 整理、知识图谱、RAG 问答一站式个人知识管理平台。',
    icon: Notebook,
    color: '#f2641e',
    soft: '#fef0e9',
    tags: ['已上线', '免费版可用'],
    action: 'Web 体验',
    path: '/notes',
    primary: true,
    mock: 'kb' as const,
    points: ['拍照即识别：RapidOCR / PaddleOCR 双引擎', 'AI 摘要、大纲、思维导图一键生成', 'Neo4j 知识图谱 + 个人库 RAG 问答']
  },
  {
    name: '知序 AI 工作台',
    en: 'TEAM SPACE',
    desc: '面向团队的多人协作知识空间，权限管理、版本控制、AI 助手全集成。',
    icon: Monitor,
    color: '#7a5af8',
    soft: '#f1edfe',
    tags: ['即将上线'],
    action: '预约体验',
    path: '/home',
    coming: true,
    mock: 'board' as const,
    points: ['多人协作空间与细粒度权限', '文档版本历史与一键回滚', '团队 AI 助手接入知识库']
  },
  {
    name: '知序 OCR 工具箱',
    en: 'OCR TOOLKIT',
    desc: '本地离线 OCR 识别套件，支持批量图片、PDF 与截图文字提取。',
    icon: SetUp,
    color: '#0ca789',
    soft: '#e6f7f4',
    tags: ['客户端', 'Windows'],
    action: '预约下载',
    path: '/home',
    coming: true,
    mock: 'scan' as const,
    points: ['纯本地运行，图片不出设备', '批量图片 / PDF / 截图一键提取', '透视矫正 + 版面还原']
  },
  {
    name: '知序数据同步助手',
    en: 'SYNC HELPER',
    desc: '多端知识库同步工具，本地文件、云端与 NAS 一键同步备份。',
    icon: DataLine,
    color: '#d9930d',
    soft: '#fdf6e3',
    tags: ['即将上线'],
    action: '预约体验',
    path: '/home',
    coming: true,
    mock: 'sync' as const,
    points: ['本地 / 云端 / NAS 三端同步', '增量备份与版本快照', '断点续传，大文件无忧']
  }
]

/** 知识流水线：内容本身就是顺序，每一步都可点进对应能力 */
const pipeline = [
  { icon: Upload, label: '采集', en: 'CAPTURE', desc: '拍照、截图、文档一键入库' },
  { icon: Search, label: '识别', en: 'RECOGNIZE', desc: 'OCR 与版面解析' },
  { icon: MagicStick, label: '整理', en: 'ORGANIZE', desc: '摘要、大纲、思维导图' },
  { icon: Share, label: '图谱', en: 'CONNECT', desc: '实体关系自动成网' },
  { icon: ChatDotRound, label: '问答', en: 'ASK', desc: '基于个人库的 RAG' }
]

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

/* ---------- Hero 产品 Tab：自动轮播，悬停/聚焦暂停，减弱动效时不自动播 ---------- */
const activeTab = ref(0)
const tabPaused = ref(false)
let tabTimer: ReturnType<typeof setInterval> | null = null
const reduceMotion =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

const selectTab = (idx: number) => {
  activeTab.value = idx
}

const startTabTimer = () => {
  if (reduceMotion || tabTimer) return
  tabTimer = setInterval(() => {
    if (!tabPaused.value) {
      activeTab.value = (activeTab.value + 1) % products.length
    }
  }, 5500)
}

const stopTabTimer = () => {
  if (tabTimer) {
    clearInterval(tabTimer)
    tabTimer = null
  }
}

onMounted(startTabTimer)
onBeforeUnmount(stopTabTimer)

/* ---------- 滚动显现：一次性，减弱动效时直接呈现 ---------- */
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
    <!-- Hero -->
    <section class="hero">
      <div class="hero-inner">
        <div class="hero-content">
          <div class="hero-eyebrow">ZHI XU · KNOWLEDGE OS</div>
          <h1>让知识<span class="h1-accent">创造价值</span></h1>
          <p class="hero-desc">
            知序专注于知识管理与 AI 赋能，提供从个人笔记整理到企业知识中枢的完整产品矩阵。
            用 OCR、大模型与知识图谱，把散落的信息变成可检索、可问答、可传承的组织资产。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" class="hero-primary" @click="enterWeb">
              Web 体验
              <el-icon class="btn-icon"><ArrowRight /></el-icon>
            </el-button>
            <el-button size="large" class="hero-ghost" @click="goDownload">
              <el-icon class="btn-icon"><Download /></el-icon>
              下载客户端
            </el-button>
          </div>
          <div class="hero-trust">
            <span><el-icon><Check /></el-icon> 免登录体验</span>
            <span><el-icon><Check /></el-icon> 本地优先</span>
            <span><el-icon><Check /></el-icon> 私有化部署</span>
            <span><el-icon><Check /></el-icon> 持续迭代</span>
          </div>
        </div>

        <!-- 招牌：产品主题 Tab（自动轮播 + 按产品换肤） -->
        <div
          class="showcase"
          role="tablist"
          aria-label="产品预览"
          @mouseenter="tabPaused = true"
          @mouseleave="tabPaused = false"
          @focusin="tabPaused = true"
          @focusout="tabPaused = false"
        >
          <div class="showcase-tabs">
            <button
              v-for="(p, idx) in products"
              :key="p.name"
              type="button"
              role="tab"
              :aria-selected="activeTab === idx"
              :class="['showcase-tab', { active: activeTab === idx }]"
              :style="{ '--p': p.color, '--ps': p.soft }"
              @click="selectTab(idx)"
            >
              <span class="tab-dot" />
              {{ p.name.replace('知序 ', '') }}
            </button>
          </div>

          <div
            class="showcase-stage"
            :style="{ '--p': products[activeTab].color, '--ps': products[activeTab].soft }"
          >
            <div class="stage-head">
              <div class="stage-traffic"><span /><span /><span /></div>
              <div class="stage-title">
                {{ products[activeTab].name }}
                <span class="stage-en">{{ products[activeTab].en }}</span>
              </div>
              <div
                class="stage-icon"
                :style="{ background: products[activeTab].color }"
              >
                <el-icon><component :is="products[activeTab].icon" /></el-icon>
              </div>
            </div>

            <div class="stage-body">
              <!-- 知识库：笔记网格 -->
              <div v-if="products[activeTab].mock === 'kb'" class="mock-grid">
                <div v-for="n in 6" :key="n" class="mock-note">
                  <div class="mock-line w80" />
                  <div class="mock-line w60" />
                  <div class="mock-line w70" />
                </div>
              </div>
              <!-- 工作台：看板列 -->
              <div v-else-if="products[activeTab].mock === 'board'" class="mock-board">
                <div v-for="c in 3" :key="c" class="mock-col">
                  <div class="mock-line w70" />
                  <div class="mock-card" />
                  <div class="mock-card short" />
                </div>
              </div>
              <!-- OCR：扫描框 -->
              <div v-else-if="products[activeTab].mock === 'scan'" class="mock-scan">
                <div class="mock-doc">
                  <div v-for="n in 5" :key="n" class="mock-line" :class="`w${[90, 70, 80, 60, 75][n - 1]}`" />
                  <div class="scanline" />
                </div>
              </div>
              <!-- 同步：双端对传 -->
              <div v-else class="mock-sync">
                <div class="mock-device" />
                <div class="mock-arrows"><span /><span /></div>
                <div class="mock-device" />
              </div>
            </div>

            <ul class="stage-points">
              <li v-for="pt in products[activeTab].points" :key="pt">
                <el-icon><Check /></el-icon>{{ pt }}
              </li>
            </ul>

            <div class="stage-foot">
              <el-button
                :type="products[activeTab].primary ? 'primary' : 'default'"
                class="stage-cta"
                @click="products[activeTab].primary ? enterWeb() : auth.openLoginModal()"
              >
                {{ products[activeTab].action }}
                <el-icon v-if="!products[activeTab].coming" class="btn-icon"><ArrowRight /></el-icon>
              </el-button>
              <span v-if="products[activeTab].coming" class="stage-coming">COMING SOON</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 知识流水线：内容本身就是顺序 -->
    <section class="pipeline">
      <div class="pipeline-inner" v-reveal>
        <div class="pipeline-track">
          <div v-for="(s, idx) in pipeline" :key="s.label" class="pipeline-node">
            <div class="pipeline-icon">
              <el-icon><component :is="s.icon" /></el-icon>
            </div>
            <div class="pipeline-label">{{ s.label }}</div>
            <div class="pipeline-en">{{ s.en }}</div>
            <div class="pipeline-desc">{{ s.desc }}</div>
            <div v-if="idx < pipeline.length - 1" class="pipeline-link" aria-hidden="true" />
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
          :key="p.name"
          v-reveal
          :class="['product-card', { primary: p.primary, coming: p.coming }]"
          :style="{ '--p': p.color, '--ps': p.soft }"
        >
          <div class="product-icon">
            <el-icon><component :is="p.icon" /></el-icon>
          </div>
          <div class="product-en">{{ p.en }}</div>
          <h3>{{ p.name }}</h3>
          <p>{{ p.desc }}</p>
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
        <div class="about-text">
          <span class="section-eyebrow">ABOUT · 关于知序</span>
          <h2>专注于知识管理的长期价值</h2>
          <p>
            知序（ZhiXu Tech）致力于用 AI 与图谱技术，帮助个人和组织把零散信息转化为结构化、可复用的知识资产。
            我们相信，知识的沉淀比信息的堆砌更有价值。
          </p>
          <p>
            从 OCR 识别到 AI 整理，从个人知识库到企业知识中枢，知序的产品矩阵覆盖知识获取、整理、应用与传承的全生命周期。
          </p>
          <div class="about-values">
            <div><strong>本地优先</strong><span>数据自主可控</span></div>
            <div><strong>开放集成</strong><span>API 与插件扩展</span></div>
            <div><strong>持续进化</strong><span>紧跟大模型能力</span></div>
          </div>
        </div>
        <div class="about-visual">
          <div class="about-card">
            <div class="about-card-title">使命 · MISSION</div>
            <p>让每个人都能拥有属于自己的智能知识库。</p>
          </div>
          <div class="about-card">
            <div class="about-card-title">愿景 · VISION</div>
            <p>成为个人与组织最信赖的知识管理基础设施。</p>
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

/* ---------- 通用眉题/标题 ---------- */
.section-head {
  text-align: center;
  margin-bottom: 56px;
}

.section-head h2 {
  font-family: var(--zx-display);
  font-size: 36px;
  font-weight: 800;
  letter-spacing: 1px;
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

/* ---------- Hero ---------- */
.hero {
  position: relative;
  padding: 72px 48px 64px;
  overflow: hidden;
  background:
    radial-gradient(720px 420px at 88% -8%, rgba(242, 100, 30, 0.1), transparent 65%),
    radial-gradient(560px 380px at 4% 12%, rgba(122, 90, 248, 0.08), transparent 60%),
    var(--zx-paper);
}

.hero-inner {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 56px;
}

.hero-content {
  flex: 1;
  max-width: 540px;
}

.hero-eyebrow {
  font-family: var(--zx-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--zx-brand-ink);
  margin-bottom: 20px;
}

.hero h1 {
  font-family: var(--zx-display);
  font-size: 58px;
  font-weight: 800;
  line-height: 1.12;
  letter-spacing: 1px;
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
  left: 0;
  right: 0;
  bottom: 6px;
  height: 10px;
  border-radius: 999px;
  background: var(--zx-brand-soft);
  border: 1px solid #f5d9c6;
  z-index: -1;
}

.hero h1 {
  position: relative;
  z-index: 0;
}

.hero-desc {
  font-size: 16px;
  line-height: 1.85;
  color: #4b5563;
  margin-bottom: 30px;
}

.hero-actions {
  display: flex;
  gap: 14px;
  margin-bottom: 26px;
}

.hero-primary {
  font-weight: 700;
  border-radius: 14px;
  padding: 12px 26px;
  height: auto;
  box-shadow: 0 12px 28px var(--zx-brand-ring);
}

.hero-actions .hero-ghost {
  font-weight: 600;
  border-radius: 14px;
  padding: 12px 26px;
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
  gap: 18px;
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

/* ---------- 招牌：产品主题 Tab ---------- */
.showcase {
  width: 540px;
  flex-shrink: 0;
}

.showcase-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.showcase-tab {
  --p: var(--zx-brand);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border-radius: 999px;
  border: 1px solid #ece5d8;
  background: rgba(255, 255, 255, 0.75);
  color: #6b7280;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.showcase-tab:hover {
  transform: translateY(-1px);
  border-color: var(--p);
  color: var(--zx-ink);
}

.showcase-tab.active {
  background: var(--zx-ink);
  border-color: var(--zx-ink);
  color: #fff;
}

.showcase-tab.active .tab-dot {
  background: var(--p);
}

.tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--p);
  flex-shrink: 0;
}

.showcase-stage {
  --p: var(--zx-brand);
  --ps: var(--zx-brand-soft);
  background: #ffffff;
  border-radius: 22px;
  border: 1px solid #f0e7d8;
  border-top: 4px solid var(--p);
  box-shadow: 0 28px 70px rgba(23, 32, 47, 0.1);
  overflow: hidden;
  transition: border-color 0.3s ease;
}

.stage-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  background: var(--ps);
  border-bottom: 1px solid #f3ece0;
  transition: background 0.3s ease;
}

.stage-traffic {
  display: flex;
  gap: 7px;
}

.stage-traffic span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #e3d9c8;
}

.stage-title {
  font-size: 13px;
  font-weight: 800;
  color: var(--zx-ink);
}

.stage-en {
  font-family: var(--zx-mono);
  font-size: 10px;
  letter-spacing: 1.5px;
  color: #a8a29e;
  margin-left: 8px;
  font-weight: 400;
}

.stage-icon {
  margin-left: auto;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
}

.stage-body {
  padding: 20px;
  min-height: 208px;
}

.mock-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.mock-note {
  border-radius: 12px;
  border: 1px solid #f0e7d8;
  background: #fffdf8;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mock-line {
  height: 8px;
  border-radius: 999px;
  background: #ece4d3;
}

.mock-line.w60 { width: 60%; }
.mock-line.w70 { width: 70%; }
.mock-line.w75 { width: 75%; }
.mock-line.w80 { width: 80%; }
.mock-line.w90 { width: 90%; }

.mock-note:first-child {
  border-color: var(--p);
  box-shadow: 0 8px 20px var(--zx-brand-ring);
}

.mock-note:first-child .mock-line:first-child {
  background: var(--p);
}

.mock-board {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.mock-col {
  border-radius: 12px;
  background: #faf7f0;
  border: 1px solid #f0e7d8;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mock-card {
  height: 44px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #eee4d2;
  border-left: 3px solid var(--p);
}

.mock-card.short {
  height: 30px;
}

.mock-scan {
  display: flex;
  justify-content: center;
}

.mock-doc {
  position: relative;
  width: 78%;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #f0e7d8;
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.scanline {
  position: absolute;
  left: 12px;
  right: 12px;
  top: 12px;
  height: 2px;
  border-radius: 999px;
  background: var(--p);
  box-shadow: 0 0 12px var(--p);
  animation: scan 2.6s ease-in-out infinite alternate;
}

@keyframes scan {
  from { top: 12px; }
  to { top: calc(100% - 14px); }
}

.mock-sync {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  padding: 30px 0;
}

.mock-device {
  width: 92px;
  height: 120px;
  border-radius: 14px;
  background: #fff;
  border: 2px solid #eee4d2;
  position: relative;
}

.mock-device::after {
  content: '';
  position: absolute;
  inset: 12px;
  border-radius: 8px;
  background: var(--ps);
  border: 1px dashed var(--p);
}

.mock-device:first-child {
  border-color: var(--p);
}

.mock-arrows {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mock-arrows span {
  display: block;
  width: 34px;
  height: 3px;
  border-radius: 999px;
  background: var(--p);
  animation: arrowsync 1.6s ease-in-out infinite;
}

.mock-arrows span:last-child {
  animation-delay: 0.25s;
  opacity: 0.55;
}

@keyframes arrowsync {
  0%, 100% { transform: translateX(0); opacity: 1; }
  50% { transform: translateX(7px); opacity: 0.5; }
}

.stage-points {
  list-style: none;
  padding: 0 22px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-bottom: 18px;
}

.stage-points li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13.5px;
  color: #374151;
}

.stage-points .el-icon {
  color: var(--p);
  font-size: 15px;
  flex-shrink: 0;
}

.stage-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 22px 22px;
}

.stage-cta {
  font-weight: 700;
  border-radius: 12px;
}

.stage-coming {
  font-family: var(--zx-mono);
  font-size: 10px;
  letter-spacing: 2px;
  color: #a8a29e;
}

/* ---------- 流水线 ---------- */
.pipeline {
  padding: 56px 48px;
  background: #fff;
  border-top: 1px solid #f3ece0;
  border-bottom: 1px solid #f3ece0;
}

.pipeline-inner {
  max-width: 1200px;
  margin: 0 auto;
}

.pipeline-track {
  display: flex;
  align-items: stretch;
}

.pipeline-node {
  position: relative;
  flex: 1;
  text-align: center;
  padding: 0 8px;
}

.pipeline-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zx-ink);
  color: #fff;
  font-size: 24px;
  box-shadow: 0 10px 24px rgba(23, 32, 47, 0.18);
}

.pipeline-node:first-child .pipeline-icon {
  background: var(--zx-brand);
  box-shadow: 0 10px 24px var(--zx-brand-ring);
}

.pipeline-label {
  font-size: 17px;
  font-weight: 800;
  color: var(--zx-ink);
}

.pipeline-en {
  font-family: var(--zx-mono);
  font-size: 10px;
  letter-spacing: 2px;
  color: #a8a29e;
  margin: 3px 0 6px;
}

.pipeline-desc {
  font-size: 12.5px;
  color: #6b7280;
  line-height: 1.6;
}

.pipeline-link {
  position: absolute;
  top: 28px;
  left: calc(50% + 44px);
  width: calc(100% - 88px);
  height: 2px;
  border-radius: 999px;
  background-image: repeating-linear-gradient(
    to right,
    var(--zx-brand) 0 8px,
    transparent 8px 16px
  );
  opacity: 0.55;
  animation: flow 1.2s linear infinite;
}

@keyframes flow {
  to { background-position: 16px 0; }
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
  display: flex;
  flex-direction: column;
  padding: 30px 28px;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #f0e7d8;
  border-top: 4px solid var(--p);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease;
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
  color: var(--zx-amber, #d9930d);
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

.resource-link .el-icon {
  transition: none;
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
  font-family: var(--zx-display);
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 1px;
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

.about-visual {
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.about-card {
  padding: 28px;
  background: var(--zx-paper);
  border-radius: 18px;
  border: 1px solid #f0e7d8;
  border-left: 4px solid var(--zx-brand);
  box-shadow: 0 10px 24px rgba(23, 32, 47, 0.05);
}

.about-card:last-child {
  border-left-color: var(--zx-iris);
}

.about-card-title {
  font-family: var(--zx-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--zx-brand-ink);
  margin-bottom: 8px;
}

.about-card p {
  font-size: 15px;
  line-height: 1.75;
  color: #374151;
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
  padding: 88px 48px;
  background:
    radial-gradient(600px 300px at 50% 120%, rgba(242, 100, 30, 0.16), transparent 70%),
    var(--zx-night);
  text-align: center;
}

.cta-eyebrow {
  font-family: var(--zx-mono);
  font-size: 11px;
  letter-spacing: 3px;
  color: #ffb48a;
  margin-bottom: 16px;
}

.cta h2 {
  font-family: var(--zx-display);
  font-size: 38px;
  font-weight: 800;
  letter-spacing: 1px;
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
  background: #0c1222;
  color: #d1d5db;
  padding: 64px 48px 24px;
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

  .scanline,
  .mock-arrows span,
  .pipeline-link {
    animation: none;
  }

  .showcase-tab:hover,
  .product-card:hover,
  .solution-card:hover,
  .download-card:hover,
  .case-card:hover,
  .resource-card:hover {
    transform: none;
  }
}

@media (max-width: 1024px) {
  .hero-inner {
    flex-direction: column;
  }

  .hero-content {
    max-width: 100%;
  }

  .showcase {
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

  .about-inner {
    flex-direction: column;
  }

  .about-visual {
    width: 100%;
  }

  .footer-inner {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  section {
    padding: 60px 22px;
  }

  .hero {
    padding: 40px 22px 48px;
  }

  .hero h1 {
    font-size: 38px;
  }

  .hero-actions,
  .cta-actions {
    flex-direction: column;
  }

  .pipeline-track {
    overflow-x: auto;
    padding-bottom: 8px;
  }

  .pipeline-node {
    min-width: 148px;
  }

  .pipeline-link {
    display: none;
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
