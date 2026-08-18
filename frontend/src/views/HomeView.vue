<script setup lang="ts">
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
  QuestionFilled
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

const products = [
  {
    name: '知序智能知识库',
    desc: 'OCR 识别、AI 整理、知识图谱、RAG 问答一站式个人知识管理平台。',
    icon: Notebook,
    tags: ['已上线', '免费版可用'],
    action: 'Web 体验',
    path: '/notes',
    primary: true
  },
  {
    name: '知序 AI 工作台',
    desc: '面向团队的多人协作知识空间，权限管理、版本控制、AI 助手全集成。',
    icon: Monitor,
    tags: ['即将上线'],
    action: '预约体验',
    path: '/home',
    coming: true
  },
  {
    name: '知序 OCR 工具箱',
    desc: '本地离线 OCR 识别套件，支持批量图片、PDF 与截图文字提取。',
    icon: SetUp,
    tags: ['客户端', 'Windows'],
    action: '预约下载',
    path: '/home',
    coming: true
  },
  {
    name: '知序数据同步助手',
    desc: '多端知识库同步工具，本地文件、云端与 NAS 一键同步备份。',
    icon: DataLine,
    tags: ['即将上线'],
    action: '预约体验',
    path: '/home',
    coming: true
  }
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
</script>

<template>
  <div class="landing-page">
    <!-- Hero -->
    <section class="hero">
      <div class="hero-bg" />
      <div class="hero-inner">
        <div class="hero-content">
          <div class="hero-badge">
            <el-icon><Star /></el-icon>
            <span>知序 · ZhiXu Tech</span>
          </div>
          <h1>让知识创造价值</h1>
          <p class="hero-desc">
            知序专注于知识管理与 AI 赋能，提供从个人笔记整理到企业知识中枢的完整产品矩阵。
            用 OCR、大模型与知识图谱，把散落的信息变成可检索、可问答、可传承的组织资产。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" @click="enterWeb">
              Web 体验
              <el-icon class="btn-icon"><ArrowRight /></el-icon>
            </el-button>
            <el-button size="large" plain @click="goDownload">
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
        <div class="hero-showcase">
          <div class="showcase-card main">
            <div class="card-bar">
              <span /><span /><span />
              <div class="card-title">知序智能知识库</div>
            </div>
            <div class="card-body">
              <div class="mock-sidebar">
                <div class="mock-nav active" /><div class="mock-nav" /><div class="mock-nav" />
                <div class="mock-nav" /><div class="mock-nav" />
              </div>
              <div class="mock-main">
                <div class="mock-toolbar"><div /><div /></div>
                <div class="mock-grid">
                  <div v-for="n in 6" :key="n" class="mock-note" />
                </div>
              </div>
            </div>
          </div>
          <div class="showcase-card float top">
            <div class="float-icon"><el-icon><ChatDotRound /></el-icon></div>
            <div class="float-text">AI 问答</div>
          </div>
          <div class="showcase-card float right">
            <div class="float-icon"><el-icon><Share /></el-icon></div>
            <div class="float-text">知识图谱</div>
          </div>
          <div class="showcase-card float bottom">
            <div class="float-icon"><el-icon><MagicStick /></el-icon></div>
            <div class="float-text">智能整理</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats -->
    <section class="stats">
      <div class="stats-inner">
        <div class="stat-item"><strong>10,000+</strong><span>知识库笔记</span></div>
        <div class="stat-item"><strong>99.2%</strong><span>OCR 识别准确率</span></div>
        <div class="stat-item"><strong>500ms</strong><span>AI 平均响应</span></div>
        <div class="stat-item"><strong>4</strong><span>产品矩阵</span></div>
      </div>
    </section>

    <!-- Products -->
    <section id="products" class="products">
      <div class="section-head">
        <span class="section-label">产品矩阵</span>
        <h2>覆盖知识管理全场景</h2>
        <p>从个人学习到企业协作，知序提供端到端的知识管理工具链</p>
      </div>
      <div class="product-grid">
        <div
          v-for="p in products"
          :key="p.name"
          :class="['product-card', { primary: p.primary, coming: p.coming }]"
        >
          <div class="product-icon">
            <el-icon><component :is="p.icon" /></el-icon>
          </div>
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
      <div class="section-head light">
        <span class="section-label">解决方案</span>
        <h2>为不同场景量身打造</h2>
        <p>无论你是学生、教师、创作者还是企业技术团队，都能找到合适的落地方式</p>
      </div>
      <div class="solution-grid">
        <div v-for="s in solutions" :key="s.title" class="solution-card">
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
      <div class="section-head">
        <span class="section-label">下载中心</span>
        <h2>多平台客户端与部署方案</h2>
        <p>Windows / macOS / Linux 客户端与 Docker 一键部署，满足不同环境需求</p>
      </div>
      <div class="download-grid">
        <div v-for="d in downloads" :key="d.platform" class="download-card">
          <div class="download-platform">{{ d.platform }}</div>
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
      <div class="download-enterprise">
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
      <div class="section-head light">
        <span class="section-label">客户案例</span>
        <h2>已经被不同场景验证</h2>
        <p>看看知序如何帮助用户沉淀与复用知识资产</p>
      </div>
      <div class="case-grid">
        <div v-for="c in cases" :key="c.org" class="case-card">
          <div class="case-org">{{ c.org }}</div>
          <div class="case-role">{{ c.role }}</div>
          <p class="case-result">{{ c.result }}</p>
          <div class="case-tags">
            <el-tag v-for="tag in c.tags" :key="tag" size="small" type="primary" effect="light">{{ tag }}</el-tag>
          </div>
        </div>
      </div>
    </section>

    <!-- Resources / Docs -->
    <section id="docs" class="resources">
      <div class="section-head">
        <span class="section-label">开发者与文档</span>
        <h2>快速接入与二次开发</h2>
        <p>完善的文档、API 与部署模板，降低使用与集成门槛</p>
      </div>
      <div class="resource-grid">
        <div v-for="r in resources" :key="r.title" class="resource-card">
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
      <div class="about-inner">
        <div class="about-text">
          <span class="section-label">关于知序</span>
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
            <div class="about-card-title">使命</div>
            <p>让每个人都能拥有属于自己的智能知识库。</p>
          </div>
          <div class="about-card">
            <div class="about-card-title">愿景</div>
            <p>成为个人与组织最信赖的知识管理基础设施。</p>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section class="faq">
      <div class="section-head light">
        <span class="section-label">常见问题</span>
        <h2>你可能想了解的</h2>
      </div>
      <div class="faq-list">
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
      <div class="cta-inner">
        <h2>开启你的知识管理升级</h2>
        <p>注册账号后即可免费体验知序智能知识库 Web 版。</p>
        <div class="cta-actions">
          <el-button type="primary" size="large" @click="enterWeb">
            Web 体验
            <el-icon class="btn-icon"><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" plain @click="router.push('/home#contact')">
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
            <div class="footer-brand-name">知序 ZhiXu Tech</div>
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
  min-height: 100vh;
  background: #ffffff;
  color: #1f2937;
  font-family:
    'Helvetica Neue',
    Helvetica,
    'PingFang SC',
    'Hiragino Sans GB',
    'Microsoft YaHei',
    Arial,
    sans-serif;
}

section {
  padding: 100px 48px;
}

.section-head {
  text-align: center;
  margin-bottom: 64px;
}

.section-head h2 {
  font-size: 36px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 12px;
}

.section-head p {
  font-size: 17px;
  color: #6b7280;
  max-width: 640px;
  margin: 0 auto;
  line-height: 1.7;
}

.section-label {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 16px;
}

.btn-icon {
  margin-left: 6px;
}

/* Hero */
.hero {
  position: relative;
  padding: 120px 48px 80px;
  overflow: hidden;
  background: linear-gradient(135deg, #f5f9ff 0%, #ffffff 50%, #f8fafc 100%);
}

.hero-bg {
  position: absolute;
  top: -20%;
  right: -10%;
  width: 700px;
  height: 700px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.08) 0%, transparent 70%);
  pointer-events: none;
}

.hero-inner {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 64px;
}

.hero-content {
  flex: 1;
  max-width: 560px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 24px;
}

.hero h1 {
  font-size: 56px;
  font-weight: 800;
  color: #111827;
  line-height: 1.1;
  margin-bottom: 24px;
  letter-spacing: -1.5px;
}

.hero-desc {
  font-size: 17px;
  line-height: 1.8;
  color: #4b5563;
  margin-bottom: 32px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 28px;
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
  font-size: 14px;
  color: #374151;
}

.hero-trust .el-icon {
  color: #2563eb;
  font-size: 16px;
}

.hero-showcase {
  position: relative;
  width: 520px;
  flex-shrink: 0;
}

.showcase-card.main {
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
  overflow: hidden;
}

.card-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
}

.card-bar span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d1d5db;
}

.card-title {
  margin-left: auto;
  font-size: 12px;
  color: #6b7280;
}

.card-body {
  display: flex;
  height: 340px;
}

.mock-sidebar {
  width: 72px;
  background: #f9fafb;
  border-right: 1px solid #e5e7eb;
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mock-nav {
  height: 22px;
  border-radius: 6px;
  background: #e5e7eb;
}

.mock-nav.active {
  background: #2563eb;
}

.mock-main {
  flex: 1;
  padding: 16px;
}

.mock-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.mock-toolbar div:first-child {
  width: 140px;
  height: 28px;
  border-radius: 6px;
  background: #e5e7eb;
}

.mock-toolbar div:last-child {
  width: 80px;
  height: 28px;
  border-radius: 6px;
  background: #2563eb;
}

.mock-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.mock-note {
  height: 100px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.showcase-card.float {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  border: 1px solid #eef2f7;
}

.showcase-card.float.top {
  top: -20px;
  left: -30px;
}

.showcase-card.float.right {
  top: 80px;
  right: -30px;
}

.showcase-card.float.bottom {
  bottom: 40px;
  left: -20px;
}

.float-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  font-size: 18px;
}

.float-text {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

/* Stats */
.stats {
  padding: 48px;
  background: #111827;
  color: #ffffff;
}

.stats-inner {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  justify-content: space-around;
  gap: 24px;
}

.stat-item {
  text-align: center;
}

.stat-item strong {
  display: block;
  font-size: 40px;
  font-weight: 800;
  color: #60a5fa;
  margin-bottom: 6px;
}

.stat-item span {
  font-size: 14px;
  color: #9ca3af;
}

/* Products */
.products {
  background: #f8fafc;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.product-card {
  display: flex;
  flex-direction: column;
  padding: 32px;
  background: #ffffff;
  border-radius: 18px;
  border: 1px solid #eef2f7;
  transition: all 0.2s ease;
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.06);
}

.product-card.primary {
  border-color: #2563eb;
}

.product-card.coming {
  opacity: 0.85;
}

.product-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  font-size: 24px;
  margin-bottom: 20px;
}

.product-card h3 {
  font-size: 18px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 10px;
}

.product-card > p {
  flex: 1;
  font-size: 14px;
  line-height: 1.7;
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
}

/* Solutions */
.solutions {
  background: #111827;
  color: #ffffff;
}

.section-head.light h2 {
  color: #ffffff;
}

.section-head.light p {
  color: #9ca3af;
}

.section-head.light .section-label {
  background: rgba(96, 165, 250, 0.15);
  color: #60a5fa;
}

.solution-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.solution-card {
  padding: 32px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  transition: all 0.2s ease;
}

.solution-card:hover {
  background: rgba(255, 255, 255, 0.08);
}

.solution-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(96, 165, 250, 0.15);
  color: #60a5fa;
  font-size: 22px;
  margin-bottom: 18px;
}

.solution-card h3 {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 10px;
}

.solution-card p {
  font-size: 14px;
  line-height: 1.7;
  color: #9ca3af;
}

/* Download */
.download-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  max-width: 1100px;
  margin: 0 auto 32px;
}

.download-card {
  text-align: center;
  padding: 32px;
  background: #ffffff;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  transition: all 0.2s ease;
}

.download-card:hover {
  border-color: #2563eb;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.06);
}

.download-platform {
  font-size: 20px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 8px;
}

.download-version {
  font-size: 14px;
  color: #2563eb;
  font-weight: 700;
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
  color: #9ca3af;
}

.download-btn {
  width: 100%;
}

.download-enterprise {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  background: #f8fafc;
  border-radius: 16px;
  border: 1px solid #eef2f7;
}

.download-enterprise strong {
  font-size: 18px;
  color: #111827;
}

.download-enterprise p {
  font-size: 14px;
  color: #6b7280;
  margin-top: 4px;
}

/* Cases */
.cases {
  background: #f8fafc;
}

.case-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.case-card {
  padding: 32px;
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #eef2f7;
  transition: all 0.2s ease;
}

.case-card:hover {
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.06);
}

.case-org {
  font-size: 18px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 4px;
}

.case-role {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 16px;
}

.case-result {
  font-size: 15px;
  line-height: 1.7;
  color: #4b5563;
  margin-bottom: 20px;
}

.case-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* Resources */
.resources {
  background: #ffffff;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.resource-card {
  padding: 28px;
  background: #f8fafc;
  border-radius: 16px;
  border: 1px solid #eef2f7;
  transition: all 0.2s ease;
}

.resource-card:hover {
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.06);
}

.resource-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  font-size: 20px;
  margin-bottom: 16px;
}

.resource-card h3 {
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 8px;
}

.resource-card p {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 16px;
}

.resource-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #2563eb;
  cursor: pointer;
}

/* About */
.about {
  background: #f8fafc;
}

.about-inner {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 64px;
}

.about-text {
  flex: 1;
}

.about-text h2 {
  font-size: 34px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 20px;
}

.about-text > p {
  font-size: 16px;
  line-height: 1.8;
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
  font-size: 18px;
  color: #111827;
}

.about-values span {
  font-size: 13px;
  color: #6b7280;
}

.about-visual {
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.about-card {
  padding: 28px;
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #eef2f7;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.04);
}

.about-card-title {
  font-size: 14px;
  font-weight: 700;
  color: #2563eb;
  margin-bottom: 8px;
}

.about-card p {
  font-size: 15px;
  line-height: 1.7;
  color: #374151;
}

/* FAQ */
.faq {
  background: #111827;
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
  color: #2563eb;
}

.faq-answer {
  font-size: 14px;
  line-height: 1.8;
  color: #ffffff;
  padding-left: 28px;
}

:deep(.el-collapse-item__header) {
  font-size: 15px;
  font-weight: 600;
}

:deep(.el-collapse) {
  border: none;
}

:deep(.el-collapse-item__header) {
  background: transparent;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  color: #ffffff;
  padding: 16px 0;
}

:deep(.el-collapse-item__wrap) {
  background: transparent;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

:deep(.el-collapse-item__content) {
  color: #9ca3af;
  padding-bottom: 20px;
}

/* CTA */
.cta {
  text-align: center;
  background: linear-gradient(135deg, #eff6ff 0%, #ffffff 100%);
}

.cta h2 {
  font-size: 36px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 12px;
}

.cta > p {
  font-size: 17px;
  color: #4b5563;
  margin-bottom: 28px;
}

.cta-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* Footer */
.landing-footer {
  background: #0b0f19;
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
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 800;
}

.footer-brand-name {
  font-size: 18px;
  font-weight: 800;
  color: #ffffff;
  margin-bottom: 4px;
}

.footer-brand-desc {
  font-size: 13px;
  color: #9ca3af;
}

.footer-col h4 {
  font-size: 14px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 16px;
}

.footer-col a,
.footer-col span {
  display: block;
  font-size: 14px;
  color: #9ca3af;
  margin-bottom: 10px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.footer-col a:hover {
  color: #ffffff;
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
  border-top: 1px solid #1f2937;
  font-size: 13px;
  color: #6b7280;
}

.footer-bottom-links {
  display: flex;
  gap: 24px;
}

.footer-bottom-links a {
  color: #6b7280;
  cursor: pointer;
  transition: color 0.2s ease;
}

.footer-bottom-links a:hover {
  color: #ffffff;
}

@media (max-width: 1024px) {
  .hero-inner {
    flex-direction: column;
  }

  .hero-showcase {
    width: 100%;
    max-width: 520px;
  }

  .product-grid,
  .solution-grid,
  .download-grid,
  .case-grid,
  .resource-grid {
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
    padding: 64px 24px;
  }

  .hero {
    padding-top: 88px;
  }

  .hero h1 {
    font-size: 38px;
  }

  .hero-actions,
  .cta-actions {
    flex-direction: column;
  }

  .showcase-card.float {
    display: none;
  }

  .stats-inner {
    flex-wrap: wrap;
  }

  .product-grid,
  .solution-grid,
  .download-grid,
  .case-grid,
  .resource-grid {
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
