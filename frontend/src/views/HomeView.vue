<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { products } from '../config/products'
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
  FolderOpened,
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
  Link,
  UploadFilled,
  Files,
  Histogram
} from '@element-plus/icons-vue'

const router = useRouter()
const auth = useAuthStore()
const activeProduct = ref(products[0]?.id ?? 'kb')
const activeConfig = computed(() => products.find((p) => p.id === activeProduct.value) ?? products[0])

const enterWeb = () => {
  if (auth.isLoggedIn) router.push('/notes')
  else auth.openLoginModal()
}
const goDownload = () => {
  if (auth.isLoggedIn) router.push('/home#download')
  else auth.openLoginModal()
}
const goProduct = (p: typeof products[number]) => {
  if (p.status === 'live') {
    // 统一契约：live 产品跳各自 ctaPath（kb 为 /notes）
    if (auth.isLoggedIn) router.push(p.ctaPath)
    else auth.openLoginModal()
  } else {
    router.push(`/products/${p.slug}`)
  }
}
const iconMap: Record<string, any> = { Notebook, Monitor, SetUp, DataLine, Share, ChatDotRound, MagicStick, FolderOpened, EditPen, Document }
const resolveIcon = (name: string) => iconMap[name] || Notebook
const solutions = [
  { icon: School, title: '高校教研', desc: '论文资料整理、课程讲义沉淀、科研成果可视化与智能答疑。' },
  { icon: OfficeBuilding, title: '企业知识管理', desc: '内部文档统一归档、新员工快速检索、项目经验沉淀与共享。' },
  { icon: EditPen, title: '内容创作', desc: '素材收集、灵感整理、文章大纲生成与多平台内容分发。' },
  { icon: User, title: '个人学习', desc: '读书笔记、网课截图、技术碎片统一整理，构建第二大脑。' }
]
const cases = [
  { org: '某 985 高校计算机学院', role: '科研团队', result: '将 3 年内 2000+ 篇论文资料结构化，知识问答准确率提升 40%。', tags: ['知识图谱', 'RAG 问答'] },
  { org: '某智能制造企业', role: '技术中台', result: '建立设备运维知识库，售后问题平均处理时间从 2h 降至 20min。', tags: ['企业知识管理', 'OCR'] },
  { org: '独立技术博主', role: '内容创作者', result: '累计沉淀 1500+ 技术笔记，通过公开分享获得 50 万+ 阅读。', tags: ['个人知识库', '公开分享'] }
]
const resources = [
  { title: '快速开始', desc: '5 分钟部署本地知识库', icon: Document },
  { title: 'API 文档', desc: '开放的 RESTful 接口说明', icon: DocumentCopy },
  { title: '部署指南', desc: 'Docker 与离线安装教程', icon: Connection },
  { title: '更新日志', desc: '版本迭代与功能路线图', icon: Star }
]
const DESKTOP_DOWNLOAD_URL = '/downloads/ZhixuKB-1.0.0.exe'
const downloads = [
  { platform: 'Windows', version: 'v1.0.0', size: '约 259 MB', note: 'exe 安装包 · 内置本地 OCR · 数据存本机', url: DESKTOP_DOWNLOAD_URL },
  { platform: 'macOS', version: '计划中', size: '-', note: 'dmg 安装包', url: '' },
  { platform: 'Linux', version: '计划中', size: '-', note: 'AppImage', url: '' },
  { platform: 'Docker 自托管', version: 'latest', size: '一键部署', note: 'compose 模板（见仓库 README）', url: '' }
]
const onDownload = (d: { platform: string; url: string }) => {
  if (d.url) { window.open(d.url, '_blank', 'noopener'); return }
  if (d.platform.startsWith('Docker')) { ElMessage.info('Docker 自托管：克隆仓库后执行 docker compose up -d --build 即可'); return }
  ElMessage.info(`${d.platform} 版本即将上线，敬请期待`)
}
const faqs = [
  { q: '知序的产品是否需要联网使用？', a: '核心功能支持本地离线运行。OCR、AI 整理与知识图谱均可在本地 Docker 或客户端中完成，数据完全由你掌控。' },
  { q: 'Windows 客户端何时上线？', a: '知序 OCR 工具箱与数据同步助手的 Windows 客户端正在内测中，预计下个季度发布。你可以在下载中心预约。' },
  { q: '是否支持私有化部署？', a: '支持。我们提供 Docker Compose 一键部署方案，团队版用户可申请企业级私有化部署与定制开发。' },
  { q: '免费版与专业版有什么区别？', a: '免费版已包含 OCR 识别、AI 整理、知识图谱与 RAG 问答等核心能力，适合个人学习。专业版与团队版提供更高并发、导出能力与协作空间。' }
]

// Scroll reveal（带清理：断开 observer、清除 typing 定时器，避免切路由后泄漏）
let revealObserver: IntersectionObserver | null = null
let typingTimer: number | null = null
onMounted(()=>{
  const els = document.querySelectorAll('[data-reveal]')
  revealObserver = new IntersectionObserver((entries)=>{
    entries.forEach(e=>{
      if(e.isIntersecting) {
        (e.target as HTMLElement).classList.add('is-in')
        revealObserver?.unobserve(e.target)
      }
    })
  },{threshold:0.18})
  els.forEach(el=>revealObserver?.observe(el))
  // typing loop
  const t = document.querySelector('.ai-typing') as HTMLElement
  if(t) typingTimer = window.setInterval(()=>{ t.classList.remove('typing'); void t.offsetWidth; t.classList.add('typing') }, 5200)
})
onUnmounted(()=>{
  revealObserver?.disconnect()
  revealObserver = null
  if (typingTimer !== null) { clearInterval(typingTimer); typingTimer = null }
})
</script>

<template>
  <div class="zx-landing">
    <!-- ================= HERO ================= -->
    <section class="hero">
      <div class="hero-grid" aria-hidden="true"><div class="grid-fade" /></div>
      <div class="hero-halo" />
      <div class="hero-inner">
        <div class="hero-left" data-reveal>
          <div class="eyebrow"><span class="eyebrow-dot" /> ZhiXu Tech · AI Knowledge Family · 4 Products</div>
          <button class="hero-badge" @click="goDownload"><span class="badge-spark">✦</span> 知序 V1.0 正式发布 · Web 版免费体验 <el-icon><ArrowRight /></el-icon></button>
          <h1 class="hero-title">
            <span class="t-serif">让知识</span>
            <span class="t-break">从堆积</span>
            <span class="t-serif grad">变成秩序</span>
          </h1>
          <p class="hero-sub">
            知序是一套可组合的 <b>AI 知识基建</b>：从 <b>知识库 · OCR 工具箱 · AI 工作台 · 同步助手</b>
            按需切入，覆盖 <b>采集 → 识别 → 整理 → 图谱 → 问答</b> 全链路，支持私有化部署。
          </p>
          <div class="hero-family">
            <button
              v-for="p in products"
              :key="p.id"
              :class="['family-tab', { active: activeProduct === p.id }]"
              :style="activeProduct === p.id ? { background: p.color, color: '#fff', borderColor: p.color } : {}"
              @click="activeProduct = p.id"
            >
              <el-icon><component :is="resolveIcon(p.icon)" /></el-icon>
              {{ p.shortName }}
              <span v-if="p.status !== 'live'" class="family-coming">即将</span>
            </button>
          </div>
          <div class="hero-actions">
            <button class="btn-primary btn-shine" @click="goProduct(activeConfig!)">
              {{ activeConfig?.status==='live' ? '免费体验' : '查看详情' }} <el-icon><ArrowRight /></el-icon>
            </button>
            <button class="btn-ghost" @click="goDownload"><el-icon><Download /></el-icon> 下载客户端</button>
          </div>
          <div class="hero-proof">
            <div class="avatar-stack"><span class="av a1">研</span><span class="av a2">企</span><span class="av a3">创</span><span class="av a4">学</span></div>
            <div class="proof-text">
              <div class="proof-stars"><el-icon><StarFilled /></el-icon><el-icon><StarFilled /></el-icon><el-icon><StarFilled /></el-icon><el-icon><StarFilled /></el-icon><el-icon><StarFilled /></el-icon><span>4.9</span></div>
              <div class="proof-sub">深受学生 · 研究者 · 团队喜爱</div>
            </div>
          </div>
          <div class="hero-meta">
            <span><i class="dot" /> 私有化部署</span>
            <span><i class="dot" /> 本地优先</span>
            <span><i class="dot" /> 4 款产品可组合</span>
          </div>
        </div>

        <!-- 真实产品界面 -->
        <div class="hero-visual" data-reveal>
          <div class="shot-glow" :style="{ background: activeConfig?.gradient }" />
          <div class="product-window shot" :key="activeProduct">
            <div class="win-bar">
              <div class="traffic"><i/><i/><i/></div>
              <div class="win-title">
                <el-icon><component :is="resolveIcon(activeConfig?.icon || 'Notebook')" /></el-icon>
                知序 · {{ activeConfig?.shortName }} — {{ activeProduct==='kb' ? 'Spring Boot 事务机制' : activeProduct==='ocr' ? '批量识别队列' : activeProduct==='workspace' ? '团队空间 · 项目资料' : '同步状态' }}
              </div>
              <div class="win-actions"><span class="chip" :style="{ background: activeConfig?.color }">{{ activeConfig?.status==='live' ? 'AI 整理中' : '预览' }}</span></div>
            </div>
            <div class="win-body">
              <!-- KB -->
              <template v-if="activeProduct==='kb'">
              <aside class="win-nav">
                <div class="nav-group">我的知识库</div>
                <div class="nav-item active"><el-icon><FolderOpened /></el-icon> 技术文档 <span class="count">128</span></div>
                <div class="nav-item"><el-icon><Document /></el-icon> 项目资料</div>
                <div class="nav-item"><el-icon><EditPen /></el-icon> 产品设计</div>
                <div class="nav-item"><el-icon><Notebook /></el-icon> 学习笔记</div>
                <div class="nav-divider" />
                <div class="nav-item small"><span class="kb-dot" /> 知识图谱</div>
                <div class="nav-item small"><span class="kb-dot alt" /> 问答历史</div>
              </aside>
              <article class="win-article">
                <div class="article-head">
                  <h3>Spring Boot 事务失效的 6 种常见原因</h3>
                  <div class="article-meta">2 天前 · 来自《Spring 实战》P.142 · 已 AI 整理</div>
                </div>
                <div class="article-toc">
                  <span class="toc-item">1. 方法非 public</span>
                  <span class="toc-item active">2. 内部调用</span>
                  <span class="toc-item">3. 异常被捕获</span>
                </div>
                <div class="article-body">
                  <p>当 <code>@Transactional</code> 标注在 <code>private</code> 方法上，或在同一个类内部 <code>this.save()</code> 调用时，Spring AOP 无法织入代理，事务将直接失效。</p>
                  <div class="ref-card">
                    <div class="ref-head"><el-icon><Link /></el-icon> 引用来源</div>
                    <div class="ref-item"><span class="ref-dot" /> Spring 官方文档 · Transactions</div>
                    <div class="ref-item"><span class="ref-dot" /> 笔记：事务传播机制 · 2024-03</div>
                  </div>
                </div>
                <div class="flow-strip" aria-hidden="true">
                  <div class="flow-item f1"><el-icon><Document /></el-icon> 论文.pdf</div>
                  <div class="flow-arrow">→</div>
                  <div class="flow-item f2"><el-icon><MagicStick /></el-icon> AI 理解</div>
                  <div class="flow-arrow">→</div>
                  <div class="flow-item f3"><el-icon><Share /></el-icon> 知识节点</div>
                </div>
              </article>
              <aside class="win-chat">
                <div class="chat-head"><span class="live-dot" /> ZhiXu AI</div>
                <div class="chat-bubble q">总结一下 Spring Boot 事务失效的原因</div>
                <div class="chat-bubble a ai-typing">
                  <div class="a-head">基于 3 篇文档总结：</div>
                  <div class="a-line"><span class="n">1.</span> 非 public 方法不走代理</div>
                  <div class="a-line"><span class="n">2.</span> 同类内部调用绕过代理</div>
                  <div class="a-line"><span class="n">3.</span> 异常被 catch 未抛出</div>
                  <div class="chat-sources"><span>Sources</span><i>Spring 官方文档</i><i>事务笔记</i><i>实战案例</i></div>
                </div>
                <div class="chat-input"><span>继续追问…</span><el-icon><ArrowRight /></el-icon></div>
              </aside>
              </template>
              <!-- OCR -->
              <template v-else-if="activeProduct==='ocr'">
                <div class="ocr-hero">
                  <div class="ocr-drop"><el-icon><UploadFilled /></el-icon><strong>拖入文件批量识别</strong><span>支持 JPG / PNG / PDF 批量 · 本地离线</span></div>
                  <div class="ocr-queue">
                    <div class="ocr-q-item"><span>发票_001.jpg</span><em class="done">完成 99.2%</em></div>
                    <div class="ocr-q-item"><span>合同.pdf · 12 页</span><em>95%</em></div>
                    <div class="ocr-q-item"><span>笔记_2024.jpg</span><em>OCR</em></div>
                  </div>
                </div>
              </template>
              <!-- Workspace -->
              <template v-else-if="activeProduct==='workspace'">
                <div class="ws-hero">
                  <div class="ws-members">
                    <div class="ws-av" style="background:#7C3AED">A</div><div class="ws-av" style="background:#0EA5E9">B</div><div class="ws-av" style="background:#059669">C</div>
                    <span>团队 12 人 · 3 个项目</span>
                  </div>
                  <div class="ws-files">
                    <div class="ws-file"><el-icon><FolderOpened /></el-icon> 项目资料 <span>128 项 · 2 人协作中</span></div>
                    <div class="ws-file"><el-icon><Document /></el-icon> 技术方案.docx <span>历史 5 版 · 可对比</span></div>
                    <div class="ws-file"><el-icon><Files /></el-icon> 会议纪要 <span>自动归档</span></div>
                  </div>
                </div>
              </template>
              <!-- Sync -->
              <template v-else-if="activeProduct==='sync'">
                <div class="sync-hero">
                  <div class="sync-row"><span>本地文件夹</span><div class="sync-bar"><i style="width:72%"/></div><span>72% 已同步</span></div>
                  <div class="sync-row"><span>云端备份</span><div class="sync-bar"><i style="width:100%;background:#059669"/></div><span>已完成</span></div>
                  <div class="sync-row"><span>NAS</span><div class="sync-bar"><i style="width:45%"/></div><span>同步中</span></div>
                </div>
              </template>
              <template v-else>
                <div class="sync-hero"><span>未知产品，请返回官网首页重新选择</span></div>
              </template>
            </div>
          </div>
          <!-- 浮动 Feature Tags（重做） -->
          <div class="float-card fc-ask">
            <div class="fc-head"><el-icon><ChatDotRound /></el-icon> AI 问答</div>
            <div class="fc-mini-chat"><span>“这个项目为什么用 Redis？”</span><em>根据 12 篇文档回答…</em></div>
          </div>
          <div class="float-card fc-graph">
            <div class="fc-head"><el-icon><Share /></el-icon> 知识图谱</div>
            <svg viewBox="0 0 100 40" class="mini-graph"><circle cx="20" cy="20" r="6"/><circle cx="50" cy="12" r="5"/><circle cx="80" cy="20" r="6"/><circle cx="50" cy="30" r="4"/><path d="M26 20 L45 14 M55 15 L75 19 M26 22 L46 28"/></svg>
          </div>
          <div class="float-card fc-organize">
            <div class="fc-head"><el-icon><Histogram /></el-icon> 智能整理</div>
            <div class="fc-steps"><span>Raw</span><span class="arrow">→</span><span class="strong">Structured</span></div>
          </div>
        </div>
      </div>
    </section>

    <!-- 能力跑马灯 -->
    <div class="marquee" aria-hidden="true">
      <div class="marquee-track">
        <span v-for="n in 6" :key="n" class="marquee-set">
          <span>OCR 识别</span><i>✦</i><span>AI 自动整理</span><i>✦</i><span>知识图谱</span><i>✦</i><span>RAG 问答</span><i>✦</i><span>多格式解析</span><i>✦</i><span>引用溯源</span><i>✦</i><span>私有化部署</span><i>✦</i><span>流式回答</span><i>✦</i>
        </span>
      </div>
    </div>

    <!-- METRICS 深色数据带 -->
    <section class="metrics" data-reveal>
      <div class="metrics-inner">
        <div class="metric"><strong class="grad-num">10,000+</strong><span>知识笔记已沉淀</span></div>
        <div class="metric-sep" />
        <div class="metric"><strong class="grad-num">99.2%</strong><span>OCR 识别准确率</span></div>
        <div class="metric-sep" />
        <div class="metric"><strong class="grad-num">500ms</strong><span>AI 平均响应</span></div>
        <div class="metric-sep" />
        <div class="metric"><strong class="grad-num">100%</strong><span>数据自主可控</span></div>
      </div>
    </section>

    <!-- PRODUCTS 交错式产品 showcase -->
    <section id="products" class="showcase" data-reveal>
      <div class="section-head">
        <span class="section-label">产品矩阵</span>
        <h2>一个家族，<span class="t-serif grad">覆盖知识全链路</span></h2>
        <p>从个人学习到企业协作，按需切入、自由组合</p>
      </div>
      <div
        v-for="(p, i) in products"
        :key="p.id"
        :class="['show-row', { reverse: i % 2 === 1 }]"
        :data-reveal="''"
      >
        <div class="show-copy">
          <div class="show-brand">
            <span class="show-logo" :style="{ background: p.gradient }"><el-icon><component :is="resolveIcon(p.icon)" /></el-icon></span>
            <span class="show-short">{{ p.shortName }}</span>
            <span :class="['show-status', p.status]">{{ p.status==='live' ? '已上线' : '即将上线' }}</span>
          </div>
          <h3>{{ p.name }}</h3>
          <p class="show-tagline t-serif">{{ p.tagline }}</p>
          <p class="show-desc">{{ p.longDesc }}</p>
          <ul class="show-checks">
            <li v-for="b in (p.features[0]?.bullets ?? []).slice(0, 3)" :key="b">
              <span class="check" :style="{ background: p.color }"><el-icon><Check /></el-icon></span>{{ b }}
            </li>
          </ul>
          <div class="show-cta-row">
            <button class="btn-primary btn-shine" :style="{ background: p.gradient }" @click="goProduct(p)">
              {{ p.status==='live' ? '免费体验' : p.ctaLabel }} <el-icon><ArrowRight /></el-icon>
            </button>
            <a class="show-link" :style="{ color: p.color }" @click="router.push(`/products/${p.slug}`)">了解更多 <el-icon><ArrowRight /></el-icon></a>
          </div>
        </div>
        <div class="show-visual" @click="goProduct(p)">
          <div class="show-blob" :style="{ background: p.gradient }" />
          <div class="show-mock">
            <div class="show-mock-bar"><span class="traffic"><i/><i/><i/></span><span class="show-mock-title">{{ p.shortName }}</span></div>
            <div class="show-mock-body">
              <div class="show-mock-icon" :style="{ background: p.gradient }"><el-icon><component :is="resolveIcon(p.icon)" /></el-icon></div>
              <div class="show-mock-name">{{ p.name }}</div>
              <div class="show-mock-tag">{{ p.tagline }}</div>
              <div class="show-mock-chips">
                <span v-for="f in p.features.slice(0, 3)" :key="f.num">{{ f.eyebrow.split('·')[0].trim() }}</span>
              </div>
              <div v-if="p.stats?.length" class="show-mock-stats">
                <div v-for="s in p.stats" :key="s.label"><strong>{{ s.value }}</strong><span>{{ s.label }}</span></div>
              </div>
            </div>
          </div>
          <div class="show-float-chip" :style="{ borderColor: p.color }"><span class="live-dot" /> {{ p.status==='live' ? '开箱即用' : '敬请期待' }}</div>
        </div>
      </div>
    </section>

    <!-- SOLUTIONS 场景图卡 -->
    <section id="solutions" class="scenarios" data-reveal>
      <div class="section-head">
        <span class="section-label">解决方案</span>
        <h2>为每一种<span class="t-serif grad">热爱</span>而造</h2>
        <p>无论你是学生、教师、创作者还是企业团队，都能找到落地方式</p>
      </div>
      <div class="scenario-grid">
        <div v-for="(s, i) in solutions" :key="s.title" :class="['scenario-card', `g${i % 4}`]">
          <div class="scenario-art"><el-icon><component :is="s.icon" /></el-icon><span class="scenario-num">0{{ i + 1 }}</span></div>
          <div class="scenario-body">
            <h3>{{s.title}}</h3>
            <p>{{s.desc}}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- DOWNLOAD -->
    <section id="download" class="download" data-reveal>
      <div class="section-head">
        <span class="section-label">下载中心</span>
        <h2>一次下载，<span class="t-serif grad">全端可用</span></h2>
        <p>Windows / macOS / Linux 客户端与 Docker 一键部署，数据永远在你手中</p>
      </div>
      <div class="download-grid">
        <div v-for="d in downloads" :key="d.platform" :class="['download-card', { hot: !!d.url }]">
          <div v-if="d.url" class="dl-ribbon">推荐</div>
          <div class="dl-icon"><el-icon><Monitor v-if="d.platform==='Windows'" /><Connection v-else-if="d.platform.startsWith('Docker')" /><Download v-else /></el-icon></div>
          <div class="download-platform">{{d.platform}}</div>
          <div class="download-version">{{d.version}}</div>
          <div class="download-meta"><span>{{d.size}}</span><span class="download-note">{{d.note}}</span></div>
          <el-button type="primary" :plain="!d.url" class="download-btn" @click="onDownload(d)"><el-icon><Download /></el-icon>{{d.url?'立即下载':d.platform.startsWith('Docker')?'查看方案':'敬请期待'}}</el-button>
        </div>
      </div>
    </section>

    <!-- TESTIMONIALS 用户评价 -->
    <section id="cases" class="testimonials" data-reveal>
      <div class="section-head">
        <span class="section-label">用户评价</span>
        <h2>他们已经<span class="t-serif grad">爱上知序</span></h2>
        <p>看看知序如何帮助用户沉淀与复用知识资产</p>
      </div>
      <div class="testi-grid">
        <div v-for="(c, i) in cases" :key="c.org" class="testi-card">
          <div class="testi-quote">“</div>
          <div class="testi-stars"><el-icon v-for="n in 5" :key="n"><StarFilled /></el-icon></div>
          <p class="testi-text">{{c.result}}</p>
          <div class="testi-tags"><el-tag v-for="tag in c.tags" :key="tag" size="small" type="primary" effect="light">{{tag}}</el-tag></div>
          <div class="testi-user"><span :class="['testi-av', `a${i % 4}`]">{{ c.org.slice(1, 2) }}</span><div><div class="testi-org">{{c.org}}</div><div class="testi-role">{{c.role}}</div></div></div>
        </div>
      </div>
    </section>
    <section id="docs" class="resources" data-reveal>
      <div class="section-head"><span class="section-label">开发者与文档</span><h2>快速接入与<span class="t-serif grad">二次开发</span></h2><p>完善的文档、开放 API 与 Docker 模板，降低使用与集成门槛</p></div>
      <div class="resource-grid">
        <div v-for="r in resources" :key="r.title" class="resource-card">
          <div class="resource-icon"><el-icon><component :is="r.icon" /></el-icon></div>
          <h3>{{r.title}}</h3><p>{{r.desc}}</p>
          <a class="resource-link" @click="router.push('/home#docs')">查看文档 <el-icon><ArrowRight /></el-icon></a>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section class="faq" data-reveal>
      <div class="section-head"><span class="section-label">常见问题</span><h2>你想知道的<span class="t-serif grad">都在这里</span></h2></div>
      <div class="faq-list">
        <el-collapse accordion>
          <el-collapse-item v-for="(f, i) in faqs" :key="i" :name="i">
            <template #title><span class="faq-q">{{ f.q }}</span></template>
            <div class="faq-a">{{ f.a }}</div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </section>

    <!-- ABOUT 理念带 -->
    <section id="about" class="about-strip" data-reveal>
      <div class="about-inner">
        <div class="about-item"><strong>本地优先</strong><span>数据自主可控，可离线运行</span></div>
        <div class="about-sep" />
        <div class="about-item"><strong>开放集成</strong><span>RESTful API 与插件扩展</span></div>
        <div class="about-sep" />
        <div class="about-item"><strong>持续进化</strong><span>紧跟大模型能力迭代</span></div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta" data-reveal>
      <div class="cta-pattern" />
      <div class="cta-inner">
        <div class="cta-badge">✦ 现在加入 · 完全免费起步</div>
        <h2>让知识从今天开始<br />为你工作</h2>
        <p>注册账号后即可免费体验知序智能知识库 Web 版，支持私有化部署。</p>
        <div class="cta-actions">
          <button class="btn-white" @click="enterWeb">免费体验 <el-icon><ArrowRight /></el-icon></button>
          <button class="btn-outline" @click="router.push('/home#contact')">联系我们</button>
        </div>
      </div>
    </section>

    <footer id="contact" class="landing-footer">
      <div class="footer-inner">
        <div class="footer-brand"><div class="brand-logo">知序</div><div><div class="footer-brand-name">知序 ZhiXu Tech</div><div class="footer-brand-desc">让知识从堆积，变成秩序。</div></div>
          <div class="footer-proof"><span class="proof-stars sm"><el-icon v-for="n in 5" :key="n"><StarFilled /></el-icon></span><span>4.9 · 用户喜爱</span></div>
        </div>
        <div class="footer-col"><h4>产品家族</h4><a v-for="p in products" :key="p.id" @click="goProduct(p)">{{ p.name }}</a></div>
        <div class="footer-col"><h4>资源</h4><a @click="router.push('/home#docs')">快速开始</a><a @click="router.push('/home#docs')">API 文档</a><a @click="router.push('/home#download')">下载中心</a></div>
        <div class="footer-col contact"><h4>联系我们</h4><span><el-icon><Message /></el-icon> contact@zhixu.tech</span><span><el-icon><Location /></el-icon> 中国 · 杭州</span></div>
      </div>
      <div class="footer-bottom"><span>© 2026 知序 ZhiXu Tech. All rights reserved.</span><div class="footer-bottom-links"><a>隐私政策</a><a>服务条款</a></div></div>
    </footer>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@500;700;800&family=Noto+Serif+SC:wght@800&family=JetBrains+Mono:wght@500&display=swap');
.zx-landing{--ink:#0B1220;--graphite:#475569;--muted:#94A3B8;--paper:#fff;--cloud:#F6F8FC;--hair:#E2E8F0;--brand:#2563EB;--brand-deep:#1E40AF;--violet:#7C3AED;--cyan:#06B6D4;--green:#059669;--amber:#F59E0B;--radius:20px;--shadow-lg:0 24px 60px rgba(15,23,42,.12);--shadow-md:0 12px 32px rgba(15,23,42,.08);background:var(--paper);color:var(--ink);font-family:'Inter','Helvetica Neue',Helvetica,'PingFang SC','Microsoft YaHei',sans-serif;-webkit-font-smoothing:antialiased}
.grad{background:linear-gradient(92deg,var(--brand) 10%,var(--violet) 55%,var(--cyan) 100%);-webkit-background-clip:text;background-clip:text;color:transparent}
.grad-num{background:linear-gradient(92deg,#93C5FD,#C4B5FD);-webkit-background-clip:text;background-clip:text;color:transparent}
.eyebrow{font:500 12px/1 'JetBrains Mono',monospace;letter-spacing:.12em;text-transform:uppercase;color:var(--brand);display:inline-flex;gap:8px;align-items:center}
.eyebrow-dot{width:7px;height:7px;border-radius:50%;background:var(--brand);box-shadow:0 0 0 6px rgba(37,99,235,.12)}
.t-serif{font-family:'Noto Serif SC',serif;font-weight:800;letter-spacing:-.02em}
.section-head{text-align:center;margin-bottom:60px;max-width:720px;margin-left:auto;margin-right:auto}
.section-head h2{font-size:46px;font-weight:800;letter-spacing:-.03em;line-height:1.12}
.section-head p{color:var(--graphite);margin-top:12px}
.section-label{display:inline-block;padding:6px 14px;border-radius:999px;background:rgba(37,99,235,.08);color:var(--brand);font:700 13px/1 Inter}

/* hero */
.hero{position:relative;overflow:hidden;padding:132px 48px 72px;background:linear-gradient(180deg,#F4F7FE 0%,#fff 60%,#fff 100%)}
.hero-grid{position:absolute;inset:0;background-image:linear-gradient(to right, rgba(15,23,42,.05) 1px, transparent 1px),linear-gradient(to bottom, rgba(15,23,42,.05) 1px, transparent 1px);background-size:34px 34px;mask-image:radial-gradient(65% 55% at 50% 0%, #000 55%, transparent 100%)}
.grid-fade{position:absolute;inset:0;background:radial-gradient(640px 420px at 78% 8%, rgba(37,99,235,.12), transparent 70%),radial-gradient(520px 360px at 8% 20%, rgba(124,58,237,.08), transparent 70%)}
.hero-halo{position:absolute;top:-160px;right:-140px;width:760px;height:760px;border-radius:50%;background:radial-gradient(circle, rgba(37,99,235,.12) 0%, transparent 70%);pointer-events:none;filter:blur(10px)}
.hero-inner{position:relative;max-width:1280px;margin:0 auto;display:flex;gap:56px;align-items:center}
.hero-left{flex:1;max-width:580px}
.hero-badge{display:inline-flex;align-items:center;gap:8px;margin:14px 0 4px;padding:9px 16px;border-radius:999px;border:1px solid rgba(37,99,235,.25);background:rgba(255,255,255,.8);backdrop-filter:blur(6px);color:var(--brand-deep);font:700 13px/1 Inter;cursor:pointer;box-shadow:0 4px 14px rgba(37,99,235,.10);transition:all .2s}
.hero-badge:hover{box-shadow:0 8px 22px rgba(37,99,235,.18);transform:translateY(-1px)}
.badge-spark{color:var(--amber);font-size:15px}
.hero-title{font-size:78px;line-height:.96;letter-spacing:-.045em;margin:14px 0 18px;font-weight:800}
.hero-title .t-break{display:block;font-weight:800}
.hero-sub{font-size:17px;line-height:1.85;color:var(--graphite)}
.hero-sub b{color:var(--ink);font-weight:700}
.hero-actions{margin-top:30px;display:flex;gap:14px;flex-wrap:wrap}
.btn-primary{position:relative;overflow:hidden;background:linear-gradient(135deg,var(--brand),var(--brand-deep));color:#fff;border:none;padding:15px 28px;border-radius:999px;font-size:15px;font-weight:700;display:inline-flex;gap:8px;align-items:center;cursor:pointer;box-shadow:0 12px 28px rgba(37,99,235,.28);transition:transform .18s, box-shadow .18s}
.btn-primary:hover{transform:translateY(-2px);box-shadow:0 18px 36px rgba(37,99,235,.34)}
.btn-shine::after{content:'';position:absolute;top:0;left:-80%;width:50%;height:100%;background:linear-gradient(100deg,transparent,rgba(255,255,255,.45),transparent);transform:skewX(-20deg);transition:left .5s}
.btn-shine:hover::after{left:130%}
.btn-ghost{background:rgba(255,255,255,.9);border:1px solid var(--hair);padding:15px 26px;border-radius:999px;font-size:15px;font-weight:700;display:inline-flex;gap:8px;align-items:center;cursor:pointer;transition:all .18s;box-shadow:0 4px 12px rgba(15,23,42,.05)}
.btn-ghost:hover{border-color:var(--brand);color:var(--brand);transform:translateY(-2px);box-shadow:0 10px 22px rgba(15,23,42,.10)}
.hero-proof{margin-top:26px;display:flex;align-items:center;gap:14px}
.avatar-stack{display:flex}
.avatar-stack .av{width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:800;border:2.5px solid #fff;box-shadow:0 4px 10px rgba(15,23,42,.15);margin-left:-10px}
.avatar-stack .av:first-child{margin-left:0}
.av.a1{background:linear-gradient(135deg,#2563EB,#60A5FA)}.av.a2{background:linear-gradient(135deg,#7C3AED,#A78BFA)}.av.a3{background:linear-gradient(135deg,#059669,#34D399)}.av.a4{background:linear-gradient(135deg,#EA580C,#FBBF24)}
.proof-stars{display:flex;gap:2px;color:var(--amber);font-size:15px;align-items:center}
.proof-stars span{color:var(--ink);font-weight:800;margin-left:6px;font-size:14px}
.proof-sub{font:500 13px/1.5 Inter;color:var(--graphite);margin-top:2px}
.hero-meta{margin-top:16px;display:flex;gap:18px;flex-wrap:wrap;font:500 13px/1 Inter;color:var(--graphite)}
.hero-meta .dot{width:6px;height:6px;border-radius:50%;background:var(--brand);display:inline-block;margin-right:6px}

/* product window */
.hero-visual{position:relative;width:620px;flex-shrink:0}
.shot-glow{position:absolute;inset:8% -6% -8% -6%;border-radius:32px;opacity:.28;filter:blur(46px);pointer-events:none;transition:background .4s}
.product-window{position:relative;background:#fff;border-radius:20px;overflow:hidden;border:1px solid var(--hair);box-shadow:0 24px 60px rgba(15,23,42,.12), 0 2px 0 rgba(15,23,42,.04)}
.product-window.shot{border-color:rgba(37,99,235,.18);box-shadow:0 32px 80px rgba(37,99,235,.16), 0 4px 16px rgba(15,23,42,.08)}
.win-bar{display:flex;align-items:center;gap:12px;padding:12px 16px;background:#F8FAFC;border-bottom:1px solid var(--hair)}
.traffic{display:flex;gap:6px}
.traffic i{width:10px;height:10px;border-radius:50%;background:#E2E8F0;display:block}
.win-title{margin-left:8px;font:600 12px/1 Inter;color:var(--graphite);display:flex;gap:6px;align-items:center}
.win-actions .chip{font:700 11px/1 Inter;background:#EEF2FF;color:var(--brand);padding:6px 10px;border-radius:999px}
.win-body{display:flex;min-height:360px}
.win-nav{width:150px;background:#F8FAFC;border-right:1px solid var(--hair);padding:14px 10px}
.nav-group{font:700 11px/1 Inter;letter-spacing:.08em;color:var(--graphite);margin:4px 0 10px}
.nav-item{font:500 13px/1 Inter;padding:9px 10px;border-radius:10px;display:flex;gap:8px;align-items:center;color:var(--graphite);cursor:default}
.nav-item.active{background:#fff;border:1px solid var(--hair);color:var(--ink);font-weight:700}
.nav-item.small{font-size:12px;opacity:.9}
.nav-item .count{margin-left:auto;background:#EEF2FF;color:var(--brand);padding:2px 6px;border-radius:999px;font:700 11px/1 Inter}
.kb-dot{width:8px;height:8px;border-radius:50%;background:var(--brand);display:inline-block}
.kb-dot.alt{background:#06B6D4}
.nav-divider{height:1px;background:var(--hair);margin:12px 0}
.win-article{flex:1;padding:18px 18px 14px;min-width:0}
.article-head h3{font-size:15px;font-weight:800;line-height:1.4}
.article-meta{font:500 11px/1 Inter;color:#94A3B8;margin-top:6px}
.article-toc{margin-top:12px;display:flex;gap:8px}
.toc-item{font:500 12px/1 Inter;padding:6px 8px;border-radius:999px;background:#F1F5F9;color:var(--graphite)}
.toc-item.active{background:var(--ink);color:#fff}
.article-body{margin-top:14px;font-size:13px;line-height:1.7;color:#334155}
.article-body code{background:#F1F5F9;padding:1px 5px;border-radius:6px;font-family:'JetBrains Mono',monospace;font-size:12px}
.ref-card{margin-top:12px;background:#F8FAFC;border:1px solid var(--hair);border-radius:12px;padding:10px 12px}
.ref-head{font:700 11px/1 Inter;color:var(--graphite);display:flex;gap:6px;align-items:center;margin-bottom:6px}
.ref-item{font:500 12px/1.6 Inter;color:#334155;display:flex;gap:6px}
.ref-dot{width:6px;height:6px;border-radius:50%;background:var(--brand);display:inline-block;margin-top:6px}
.flow-strip{margin-top:12px;display:flex;gap:8px;align-items:center}
.flow-item{font:600 11px/1 Inter;padding:7px 10px;border-radius:999px;background:#fff;border:1px solid var(--hair);display:flex;gap:6px;align-items:center;animation:flowIn .6s both}
.flow-item.f1{animation-delay:.2s}.flow-item.f2{animation-delay:.5s;background:var(--brand);color:#fff;border-color:var(--brand)}.flow-item.f3{animation-delay:.8s}
.flow-arrow{color:#94A3B8;font-weight:700}
.win-chat{width:190px;background:#0B1220;color:#E2E8F0;padding:14px 12px;display:flex;flex-direction:column;gap:10px}
.chat-head{font:700 11px/1 'JetBrains Mono',monospace;letter-spacing:.08em;color:#94A3B8;display:flex;gap:6px;align-items:center}
.live-dot{width:7px;height:7px;border-radius:50%;background:#22C55E;box-shadow:0 0 0 6px rgba(34,197,94,.15);display:inline-block}
.chat-bubble{border-radius:14px;padding:10px 12px;font:500 12.5px/1.5 Inter}
.chat-bubble.q{background:#1E293B;color:#E2E8F0}
.chat-bubble.a{background:#fff;color:var(--ink);border:1px solid #E2E8F0}
.a-head{font:700 11px/1 Inter;color:var(--brand);margin-bottom:6px}
.a-line{font-size:12px;line-height:1.6}
.a-line .n{color:var(--brand);font-weight:800;margin-right:4px}
.chat-sources{margin-top:8px;display:flex;gap:6px;flex-wrap:wrap}
.chat-sources span{font:700 10px/1 Inter;letter-spacing:.08em;color:#64748B}
.chat-sources i{font:500 10px/1 Inter;background:#F1F5F9;padding:4px 6px;border-radius:999px;color:#334155;font-style:normal}
.chat-input{margin-top:auto;background:#1E293B;border-radius:999px;padding:8px 12px;display:flex;justify-content:space-between;align-items:center;color:#94A3B8;font:500 12px/1 Inter}
.float-card{position:absolute;background:#fff;border:1px solid var(--hair);border-radius:14px;padding:10px 12px;box-shadow:0 12px 30px rgba(15,23,42,.10);animation:floatY 4.6s ease-in-out infinite}
.float-card .fc-head{font:700 12px/1 Inter;display:flex;gap:6px;align-items:center}
.fc-ask{left:-18px;top:-14px;transform:rotate(-1deg)}
.fc-graph{right:-18px;top:78px;animation-delay:.6s}
.fc-organize{left:8px;bottom:18px;animation-delay:1.1s}
.fc-mini-chat{margin-top:6px;font:500 12px/1.4 Inter;color:#334155}
.fc-mini-chat em{font-style:normal;color:var(--brand);font-weight:700}
.fc-steps{margin-top:6px;font:700 12px/1 'JetBrains Mono',monospace;display:flex;gap:6px;align-items:center}
.fc-steps .arrow{color:var(--brand)}
.fc-steps .strong{color:var(--brand)}
.mini-graph{width:100px;height:40px;margin-top:6px}
.mini-graph circle{fill:#EEF2FF;stroke:var(--brand);stroke-width:1.2}
.mini-graph path{stroke:var(--brand);stroke-width:1;fill:none;stroke-dasharray:40;animation:dash 2s linear infinite}

/* marquee */
.marquee{overflow:hidden;border-top:1px solid var(--hair);border-bottom:1px solid var(--hair);background:#fff;padding:16px 0}
.marquee-track{display:flex;width:max-content;animation:marquee 38s linear infinite}
.marquee:hover .marquee-track{animation-play-state:paused}
.marquee-set{display:flex;align-items:center;gap:28px;padding-right:28px;font:700 14px/1 Inter;letter-spacing:.06em;color:#334155;white-space:nowrap}
.marquee-set i{color:var(--brand);font-style:normal;font-size:12px}

/* metrics */
.metrics{padding:64px 48px;background:radial-gradient(120% 160% at 50% 0%,#16213B 0%,#0B1220 55%) no-repeat,#0B1220;color:#fff;position:relative;overflow:hidden}
.metrics::before{content:'';position:absolute;inset:0;background-image:linear-gradient(to right, rgba(255,255,255,.045) 1px, transparent 1px),linear-gradient(to bottom, rgba(255,255,255,.045) 1px, transparent 1px);background-size:36px 36px;mask-image:radial-gradient(60% 100% at 50% 0%, #000 40%, transparent 100%)}
.metrics-inner{position:relative;max-width:1080px;margin:0 auto;display:flex;justify-content:space-between;align-items:center;gap:24px}
.metric{display:flex;flex-direction:column;gap:8px;text-align:center;flex:1}
.metric strong{font-size:46px;font-weight:800;letter-spacing:-.03em;line-height:1}
.metric span{font:500 13px/1.5 Inter;color:#94A3B8}
.metric-sep{width:1px;height:56px;background:rgba(255,255,255,.12)}

/* showcase 交错式产品陈列 */
.showcase{background:var(--cloud);padding:96px 48px;position:relative;overflow:hidden}
.show-row{max-width:1180px;margin:0 auto 72px;display:flex;gap:64px;align-items:center}
.show-row:last-child{margin-bottom:0}
.show-row.reverse{flex-direction:row-reverse}
.show-copy{flex:1;min-width:0}
.show-brand{display:flex;align-items:center;gap:10px;margin-bottom:18px}
.show-logo{width:46px;height:46px;border-radius:14px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:22px;box-shadow:0 10px 22px rgba(15,23,42,.18)}
.show-short{font:700 13px/1 'JetBrains Mono',monospace;letter-spacing:.1em;color:var(--graphite)}
.show-status{font:700 11px/1 Inter;padding:5px 10px;border-radius:999px}
.show-status.live{background:#DCFCE7;color:#166534}
.show-status.coming{background:#FEF3C7;color:#92400E}
.show-copy h3{font-size:42px;font-weight:800;letter-spacing:-.03em;line-height:1.08}
.show-tagline{font-size:22px;margin-top:8px;color:var(--brand-deep)}
.show-desc{margin-top:14px;font-size:16px;line-height:1.8;color:var(--graphite)}
.show-checks{margin-top:20px;display:grid;gap:12px;list-style:none}
.show-checks li{display:flex;gap:12px;align-items:center;font:600 14px/1.5 Inter;color:var(--ink)}
.show-checks .check{width:22px;height:22px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:12px;flex-shrink:0}
.show-cta-row{margin-top:26px;display:flex;gap:18px;align-items:center;flex-wrap:wrap}
.show-link{display:inline-flex;gap:6px;align-items:center;font:700 14px/1 Inter;cursor:pointer}
.show-link:hover{text-decoration:underline}
.show-visual{flex:1;position:relative;cursor:pointer;min-width:0}
.show-blob{position:absolute;inset:12% 6%;border-radius:36px;opacity:.22;filter:blur(52px);transition:opacity .3s}
.show-visual:hover .show-blob{opacity:.34}
.show-mock{position:relative;background:#fff;border:1px solid var(--hair);border-radius:22px;overflow:hidden;box-shadow:var(--shadow-lg);transition:transform .25s}
.show-visual:hover .show-mock{transform:translateY(-4px) rotate(-.3deg)}
.show-mock-bar{display:flex;align-items:center;gap:10px;padding:13px 18px;background:#F8FAFC;border-bottom:1px solid var(--hair);font:600 12px/1 Inter;color:var(--graphite)}
.show-mock-title{margin-left:6px}
.show-mock-body{padding:30px 26px;text-align:center}
.show-mock-icon{width:72px;height:72px;border-radius:22px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:34px;margin:0 auto 16px;box-shadow:0 14px 30px rgba(15,23,42,.20)}
.show-mock-name{font-size:20px;font-weight:800}
.show-mock-tag{font-size:14px;color:var(--graphite);margin-top:6px}
.show-mock-chips{margin-top:18px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap}
.show-mock-chips span{font:600 12px/1 Inter;padding:8px 14px;border-radius:999px;background:#F1F5F9;color:#334155;border:1px solid var(--hair)}
.show-mock-stats{margin-top:20px;display:flex;gap:20px;justify-content:center}
.show-mock-stats div{display:flex;flex-direction:column;gap:4px}
.show-mock-stats strong{font-size:22px;font-weight:800}
.show-mock-stats span{font:500 11px/1 Inter;color:var(--muted)}
.show-float-chip{position:absolute;right:18px;bottom:-14px;display:flex;gap:8px;align-items:center;background:#fff;border:1.5px solid var(--brand);border-radius:999px;padding:9px 16px;font:700 12px/1 Inter;box-shadow:var(--shadow-md);animation:floatY 4s ease-in-out infinite}
.cases,.solutions,.resources,.download{padding:88px 48px}
.scenarios{background:#fff;padding:96px 48px}
.scenario-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:20px;max-width:1180px;margin:0 auto}
.scenario-card{border-radius:22px;overflow:hidden;border:1px solid var(--hair);background:#fff;box-shadow:0 6px 18px rgba(15,23,42,.05);transition:transform .25s, box-shadow .25s;cursor:default}
.scenario-card:hover{transform:translateY(-6px);box-shadow:var(--shadow-lg)}
.scenario-art{position:relative;height:170px;display:flex;align-items:center;justify-content:center;overflow:hidden}
.scenario-art .el-icon{font-size:64px;color:rgba(255,255,255,.92);filter:drop-shadow(0 8px 18px rgba(0,0,0,.25));transition:transform .3s}
.scenario-card:hover .scenario-art .el-icon{transform:scale(1.12) rotate(-3deg)}
.scenario-art::after{content:'';position:absolute;inset:0;background-image:radial-gradient(rgba(255,255,255,.22) 1.2px, transparent 1.2px);background-size:18px 18px}
.scenario-num{position:absolute;right:16px;bottom:10px;font:800 44px/1 Inter;color:rgba(255,255,255,.35);letter-spacing:-.02em}
.scenario-card.g0 .scenario-art{background:linear-gradient(135deg,#2563EB,#60A5FA)}
.scenario-card.g1 .scenario-art{background:linear-gradient(135deg,#7C3AED,#A78BFA)}
.scenario-card.g2 .scenario-art{background:linear-gradient(135deg,#059669,#34D399)}
.scenario-card.g3 .scenario-art{background:linear-gradient(135deg,#EA580C,#FBBF24)}
.scenario-body{padding:22px}
.scenario-body h3{font-size:18px;font-weight:800;margin-bottom:8px}
.scenario-body p{font-size:14px;line-height:1.75;color:var(--graphite)}
.download-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px;max-width:1100px;margin:0 auto 24px;align-items:stretch}.download-card{display:flex;flex-direction:column;background:#fff;border:1px solid var(--hair);border-radius:16px;padding:24px;text-align:center;height:100%;transition:all .2s ease}.download-card:hover{border-color:var(--brand);box-shadow:0 12px 30px rgba(15,23,42,.06)}.download-platform{font-size:16px;font-weight:800;color:var(--ink)}.download-version{font-size:13px;color:var(--brand);font-weight:700;margin-top:4px}.download-meta{flex:1;display:flex;flex-direction:column;gap:4px;font-size:13px;color:#6b7280;margin:12px 0 16px;min-height:48px}.download-meta .download-note{color:#9ca3af;font-size:12px;line-height:1.5;word-break:break-word}.download-btn{margin-top:auto;width:100%}
.testimonials{background:var(--cloud);padding:96px 48px}
.testi-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;max-width:1180px;margin:0 auto}
.testi-card{position:relative;background:#fff;border:1px solid var(--hair);border-radius:22px;padding:28px;display:flex;flex-direction:column;box-shadow:0 6px 18px rgba(15,23,42,.05);transition:transform .25s, box-shadow .25s}
.testi-card:hover{transform:translateY(-5px);box-shadow:var(--shadow-lg)}
.testi-quote{font:800 56px/0.6 'Noto Serif SC',serif;color:#DBEAFE;height:26px}
.testi-stars{display:flex;gap:2px;color:var(--amber);font-size:14px;margin:6px 0 12px}
.testi-text{flex:1;font-size:14.5px;line-height:1.8;color:#334155}
.testi-tags{display:flex;flex-wrap:wrap;gap:8px;margin:16px 0}
.testi-user{display:flex;gap:12px;align-items:center;padding-top:16px;border-top:1px solid #F1F5F9}
.testi-av{width:42px;height:42px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:16px;font-weight:800;flex-shrink:0}
.testi-av.a0{background:linear-gradient(135deg,#2563EB,#60A5FA)}.testi-av.a1{background:linear-gradient(135deg,#059669,#34D399)}.testi-av.a2{background:linear-gradient(135deg,#7C3AED,#A78BFA)}
.testi-org{font-size:14px;font-weight:800}
.testi-role{font-size:12px;color:var(--muted);margin-top:2px}
.resource-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px;max-width:1100px;margin:0 auto}
/* faq */
.faq{padding:88px 48px;background:#fff}
.faq-list{max-width:780px;margin:0 auto}
.faq-list :deep(.el-collapse){border:none;display:grid;gap:12px}
.faq-list :deep(.el-collapse-item){border:1px solid var(--hair);border-radius:16px;overflow:hidden;background:#fff;transition:border-color .2s, box-shadow .2s}
.faq-list :deep(.el-collapse-item.is-active){border-color:rgba(37,99,235,.4);box-shadow:0 10px 26px rgba(37,99,235,.10)}
.faq-list :deep(.el-collapse-item__header){padding:18px 22px;font-size:15px;font-weight:700;border:none;height:auto;line-height:1.5}
.faq-list :deep(.el-collapse-item__wrap){border:none}
.faq-list :deep(.el-collapse-item__content){padding:0 22px 20px;font-size:14px;line-height:1.8;color:var(--graphite)}
.faq-q{font-weight:700}
.faq-a{line-height:1.8}
/* about */
.about-strip{padding:26px 48px;background:#fff;border-top:1px solid var(--hair)}
.about-inner{max-width:1080px;margin:0 auto;display:flex;justify-content:space-between;align-items:center;gap:24px}
.about-item{display:flex;flex-direction:column;gap:6px;text-align:center;flex:1}
.about-item strong{font-size:16px;font-weight:800}
.about-item span{font-size:13px;color:var(--graphite)}
.about-sep{width:1px;height:40px;background:var(--hair)}
/* cta */
.cta{position:relative;overflow:hidden;padding:96px 48px;background:linear-gradient(120deg,#1D4ED8 0%,#2563EB 45%,#7C3AED 100%);text-align:center;color:#fff}
.cta-pattern{position:absolute;inset:0;background-image:radial-gradient(rgba(255,255,255,.16) 1.4px, transparent 1.4px);background-size:22px 22px;mask-image:radial-gradient(60% 90% at 50% 50%, #000 30%, transparent 100%)}
.cta-inner{position:relative;max-width:720px;margin:0 auto}
.cta-badge{display:inline-block;padding:8px 18px;border-radius:999px;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.3);font:700 13px/1 Inter;margin-bottom:20px}
.cta-inner h2{font-size:46px;font-weight:800;letter-spacing:-.03em;line-height:1.15}
.cta-inner p{margin-top:14px;font-size:16px;color:rgba(255,255,255,.82)}
.cta-actions{margin-top:28px;display:flex;gap:14px;justify-content:center;flex-wrap:wrap}
.btn-white{background:#fff;color:var(--brand-deep);border:none;padding:15px 30px;border-radius:999px;font-size:15px;font-weight:800;display:inline-flex;gap:8px;align-items:center;cursor:pointer;box-shadow:0 14px 30px rgba(0,0,0,.22);transition:transform .18s}
.btn-white:hover{transform:translateY(-2px)}
.btn-outline{background:transparent;color:#fff;border:1.5px solid rgba(255,255,255,.55);padding:14px 28px;border-radius:999px;font-size:15px;font-weight:700;cursor:pointer;transition:all .18s}
.btn-outline:hover{background:rgba(255,255,255,.12);border-color:#fff}
.landing-footer{background:#0B0F19;color:#9CA3AF;padding:56px 48px 24px}
.footer-inner{max-width:1180px;margin:0 auto;display:grid;grid-template-columns:2fr 1fr 1fr 1.5fr;gap:32px}
.footer-bottom{max-width:1180px;margin:24px auto 0;padding-top:18px;border-top:1px solid #1F2937;display:flex;justify-content:space-between}
.footer-proof{margin-top:14px;display:flex;gap:8px;align-items:center;font-size:12px;color:#9CA3AF}
.proof-stars.sm{font-size:12px}

/* reveal */
[data-reveal]{opacity:0;transform:translateY(14px);transition:opacity .6s ease, transform .6s ease}
[data-reveal].is-in{opacity:1;transform:none}
@media (prefers-reduced-motion:reduce){[data-reveal]{opacity:1;transform:none}.float-card,.flow-item,.doc,.typewriter{animation:none}}

.download-card{position:relative}
.download-card.hot{border-color:rgba(37,99,235,.45);box-shadow:0 14px 34px rgba(37,99,235,.12)}
.dl-ribbon{position:absolute;top:-12px;left:50%;transform:translateX(-50%);background:linear-gradient(135deg,var(--brand),var(--violet));color:#fff;font:800 11px/1 Inter;padding:6px 14px;border-radius:999px;box-shadow:0 6px 14px rgba(37,99,235,.3);white-space:nowrap}
.dl-icon{width:52px;height:52px;border-radius:16px;background:linear-gradient(135deg,#EEF2FF,#DBEAFE);color:var(--brand);display:flex;align-items:center;justify-content:center;font-size:24px;margin:6px auto 14px}

/* keyframes */
@keyframes flowIn{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:none}}
@keyframes floatY{0%,100%{transform:translateY(0)}50%{transform:translateY(-6px)}}
@keyframes slideIn{from{opacity:0;transform:translateX(-8px)}to{opacity:1;transform:none}}
@keyframes dash{to{stroke-dashoffset:-80}}
@keyframes typing{from{width:0}to{width:100%}}
@keyframes blink{50%{border-color:transparent}}
@keyframes marquee{to{transform:translateX(-50%)}}

 .hero-family{display:flex;gap:8px;margin:18px 0 8px;flex-wrap:wrap}
.family-tab{display:inline-flex;align-items:center;gap:6px;padding:8px 14px;border-radius:999px;border:1px solid var(--hair);background:#fff;font:600 13px/1 Inter;cursor:pointer;transition:all .2s}
.family-tab.active{color:#fff;box-shadow:0 4px 12px rgba(37,99,235,.2)}
.family-coming{font:700 10px/1 Inter;background:rgba(255,255,255,.9);color:inherit;padding:2px 6px;border-radius:999px;margin-left:4px}
.ocr-hero,.ws-hero,.sync-hero{padding:24px;display:flex;flex-direction:column;gap:16px;min-height:260px;justify-content:center}
.ocr-drop{border:1px dashed #CBD5E1;border-radius:12px;padding:20px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:6px}
.ocr-queue{display:flex;flex-direction:column;gap:8px}
.ocr-q-item{display:flex;justify-content:space-between;padding:10px 12px;background:#F8FAFC;border-radius:10px;font:600 13px/1 Inter}
.ws-members{display:flex;align-items:center;gap:10px;font:600 13px/1 Inter}
.ws-av{width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700}
.ws-files{display:flex;flex-direction:column;gap:8px}
.ws-file{display:flex;align-items:center;gap:10px;padding:12px;background:#F8FAFC;border-radius:10px;font:600 13px/1 Inter}
.ws-file span{margin-left:auto;font:500 12px/1 Inter;color:#64748B}
.sync-row{display:flex;align-items:center;gap:12px;font:600 13px/1 Inter}
.sync-bar{flex:1;height:8px;background:#F1F5F9;border-radius:999px;overflow:hidden}
.sync-bar i{display:block;height:100%;background:var(--brand);border-radius:999px}
.product-top{display:flex;align-items:center;gap:8px;margin-bottom:4px}
.product-top h3{flex:1;margin:0}
.product-status{font:700 11px/1 Inter;padding:4px 8px;border-radius:999px}
.product-desc{flex:1}
.product-foot{margin-top:16px}
.product-cta{font:700 13px/1 Inter;display:inline-flex;gap:4px;align-items:center}
.product-card{cursor:pointer}
.product-card.live{border-color:var(--brand)}
@media (prefers-reduced-motion:reduce){.marquee-track{animation:none}}
@media (max-width:1100px){.hero-inner{flex-direction:column}.hero-visual{width:100%;max-width:620px}.show-row,.show-row.reverse{flex-direction:column}.scenario-grid,.download-grid,.resource-grid{grid-template-columns:repeat(2,1fr)}.testi-grid{grid-template-columns:1fr}.metrics-inner{flex-wrap:wrap}.metric{flex:1 1 40%}.metric-sep{display:none}}
@media (max-width:720px){.hero{padding:100px 20px 44px}.hero-title{font-size:46px}.section-head h2{font-size:32px}.showcase,.scenarios,.testimonials,.cases,.solutions,.resources,.download,.faq{padding:60px 20px}.show-copy h3{font-size:30px}.scenario-grid,.download-grid,.testi-grid,.resource-grid{grid-template-columns:1fr}.float-card{display:none}.metrics{padding:44px 20px}.metric strong{font-size:32px}.about-inner{flex-direction:column;gap:16px}.about-sep{width:60px;height:1px}.cta{padding:64px 20px}.cta-inner h2{font-size:32px}.footer-inner{grid-template-columns:1fr}.footer-bottom{flex-direction:column;gap:12px;text-align:center}}
</style>
