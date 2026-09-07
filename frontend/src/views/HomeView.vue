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
          <h1 class="hero-title">
            <span class="t-serif">让知识</span>
            <span class="t-break">从堆积</span>
            <span class="t-serif accent">变成秩序</span>
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
            <button class="btn-primary" @click="goProduct(activeConfig!)">
              {{ activeConfig?.status==='live' ? '立即体验' : '查看详情' }} <el-icon><ArrowRight /></el-icon>
            </button>
            <button class="btn-ghost" @click="goDownload"><el-icon><Download /></el-icon> 下载客户端</button>
          </div>
          <div class="hero-meta">
            <span><i class="dot" /> 私有化部署</span>
            <span><i class="dot" /> 本地优先</span>
            <span><i class="dot" /> 4 款产品可组合</span>
          </div>
        </div>

        <!-- 真实产品界面 -->
        <div class="hero-visual" data-reveal>
          <div class="product-window" :key="activeProduct">
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

    <!-- TRUST -->
    <section class="trust" data-reveal>
      <div class="trust-inner">
        <div class="trust-item"><strong>Private</strong><span>数据存本机 · 可离线</span></div>
        <div class="trust-sep" />
        <div class="trust-item"><strong>AI Native</strong><span>OCR · RAG · 图谱原生</span></div>
        <div class="trust-sep" />
        <div class="trust-item"><strong>Secure</strong><span>私有化部署 · 权限可控</span></div>
      </div>
    </section>

    <!-- PRODUCTS -->
    <section id="products" class="products" data-reveal>
      <div class="section-head">
        <span class="section-label">产品矩阵</span>
        <h2>覆盖知识管理全场景</h2>
        <p>从个人学习到企业协作，知序提供端到端的工具链</p>
      </div>
      <div class="product-grid">
        <div v-for="p in products" :key="p.id" :class="['product-card', {live: p.status==='live'}]" @click="goProduct(p)">
          <div class="product-icon" :style="{ background: p.gradient, color:'#fff' }"><el-icon><component :is="resolveIcon(p.icon)" /></el-icon></div>
          <div class="product-top">
            <h3>{{p.name}}</h3>
            <span class="product-status" :style="{ background: p.status==='live' ? '#DCFCE7' : '#FEF3C7', color: p.status==='live' ? '#166534' : '#92400E' }">{{ p.status==='live' ? '已上线' : '即将' }}</span>
          </div>
          <p class="product-desc">{{p.description}}</p>
          <div class="product-tags"><el-tag v-for="tag in p.tags" :key="tag" size="small" type="info" effect="plain">{{tag}}</el-tag></div>
          <div class="product-foot">
            <span class="product-cta" :style="{ color: p.color }">{{ p.status==='live' ? '立即体验' : '查看详情' }} <el-icon><ArrowRight /></el-icon></span>
          </div>
        </div>
      </div>
    </section>

    <!-- SOLUTIONS -->
    <section id="solutions" class="solutions" data-reveal>
      <div class="section-head light">
        <span class="section-label">解决方案</span>
        <h2>为不同场景量身打造</h2>
      </div>
      <div class="solution-grid">
        <div v-for="s in solutions" :key="s.title" class="solution-card">
          <div class="solution-icon"><el-icon><component :is="s.icon" /></el-icon></div>
          <h3>{{s.title}}</h3>
          <p>{{s.desc}}</p>
        </div>
      </div>
    </section>

    <!-- DOWNLOAD -->
    <section id="download" class="download" data-reveal>
      <div class="section-head">
        <span class="section-label">下载中心</span>
        <h2>多平台客户端与部署方案</h2>
        <p>Windows / macOS / Linux 客户端与 Docker 一键部署</p>
      </div>
      <div class="download-grid">
        <div v-for="d in downloads" :key="d.platform" class="download-card">
          <div class="download-platform">{{d.platform}}</div>
          <div class="download-version">{{d.version}}</div>
          <div class="download-meta"><span>{{d.size}}</span><span class="download-note">{{d.note}}</span></div>
          <el-button type="primary" plain class="download-btn" @click="onDownload(d)"><el-icon><Download /></el-icon>{{d.url?'立即下载':d.platform.startsWith('Docker')?'查看方案':'敬请期待'}}</el-button>
        </div>
      </div>
    </section>

    <!-- CASES & RESOURCES (compact) -->
    <section id="cases" class="cases" data-reveal>
      <div class="section-head"><span class="section-label">客户案例</span><h2>已经被不同场景验证</h2></div>
      <div class="case-grid">
        <div v-for="c in cases" :key="c.org" class="case-card">
          <div class="case-org">{{c.org}}</div><div class="case-role">{{c.role}}</div>
          <p class="case-result">{{c.result}}</p>
          <div class="case-tags"><el-tag v-for="tag in c.tags" :key="tag" size="small" type="primary" effect="light">{{tag}}</el-tag></div>
        </div>
      </div>
    </section>
    <section id="docs" class="resources" data-reveal>
      <div class="section-head"><span class="section-label">开发者与文档</span><h2>快速接入与二次开发</h2></div>
      <div class="resource-grid">
        <div v-for="r in resources" :key="r.title" class="resource-card">
          <div class="resource-icon"><el-icon><component :is="r.icon" /></el-icon></div>
          <h3>{{r.title}}</h3><p>{{r.desc}}</p>
          <a class="resource-link" @click="router.push('/home')">查看文档 <el-icon><ArrowRight /></el-icon></a>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta" data-reveal>
      <div class="cta-inner">
        <h2>开启你的知识管理升级</h2>
        <p>注册账号后即可免费体验知序智能知识库 Web 版。</p>
        <div class="cta-actions">
          <button class="btn-primary" @click="enterWeb">Web 体验 <el-icon><ArrowRight /></el-icon></button>
          <button class="btn-ghost" @click="router.push('/home#contact')">联系我们</button>
        </div>
      </div>
    </section>

    <footer id="contact" class="landing-footer">
      <div class="footer-inner">
        <div class="footer-brand"><div class="brand-logo">知序</div><div><div class="footer-brand-name">知序 ZhiXu Tech</div><div class="footer-brand-desc">让知识从堆积，变成秩序。</div></div></div>
        <div class="footer-col"><h4>产品</h4><a @click="router.push('/notes')">智能知识库</a><a @click="router.push('/home#products')">AI 工作台</a></div>
        <div class="footer-col"><h4>资源</h4><a @click="router.push('/home#docs')">快速开始</a><a @click="router.push('/home#docs')">API 文档</a></div>
        <div class="footer-col contact"><h4>联系我们</h4><span><el-icon><Message /></el-icon> contact@zhixu.tech</span><span><el-icon><Location /></el-icon> 中国 · 杭州</span></div>
      </div>
      <div class="footer-bottom"><span>© 2026 知序 ZhiXu Tech. All rights reserved.</span><div class="footer-bottom-links"><a>隐私政策</a><a>服务条款</a></div></div>
    </footer>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@500;700;800&family=Noto+Serif+SC:wght@800&family=JetBrains+Mono:wght@500&display=swap');
.zx-landing{--ink:#0B1220;--graphite:#475569;--paper:#fff;--cloud:#F8FAFC;--hair:#E2E8F0;--brand:#2563EB;--brand-deep:#1D4ED8;--glow:#60A5FA;--radius:18px;background:var(--paper);color:var(--ink);font-family:'Inter','Helvetica Neue',Helvetica,'PingFang SC','Microsoft YaHei',sans-serif}
.eyebrow{font:500 12px/1 'JetBrains Mono',monospace;letter-spacing:.12em;text-transform:uppercase;color:var(--brand);display:inline-flex;gap:8px;align-items:center}
.eyebrow-dot{width:7px;height:7px;border-radius:50%;background:var(--brand);box-shadow:0 0 0 6px rgba(37,99,235,.12)}
.t-serif{font-family:'Noto Serif SC',serif;font-weight:800;letter-spacing:-.02em}
.section-head{text-align:center;margin-bottom:56px}
.section-head h2{font-size:44px;font-weight:800;letter-spacing:-.03em;line-height:1.1}
.section-head p{color:var(--graphite);margin-top:12px}
.section-label{display:inline-block;padding:6px 14px;border-radius:999px;background:rgba(37,99,235,.08);color:var(--brand);font:700 13px/1 Inter}

/* hero */
.hero{position:relative;overflow:hidden;padding:120px 48px 60px;background:linear-gradient(180deg,#F8FAFC 0%,#fff 55%,#fff 100%)}
.hero-grid{position:absolute;inset:0;background-image:linear-gradient(to right, rgba(15,23,42,.04) 1px, transparent 1px),linear-gradient(to bottom, rgba(15,23,42,.04) 1px, transparent 1px);background-size:32px 32px;mask-image:radial-gradient(60% 50% at 50% 0%, #000 60%, transparent 100%)}
.grid-fade{position:absolute;inset:0;background:radial-gradient(600px 400px at 75% 10%, rgba(37,99,235,.10), transparent 70%)}
.hero-halo{position:absolute;top:-120px;right:-120px;width:720px;height:720px;border-radius:50%;background:radial-gradient(circle, rgba(37,99,235,.10) 0%, transparent 70%);pointer-events:none}
.hero-inner{position:relative;max-width:1280px;margin:0 auto;display:flex;gap:48px;align-items:center}
.hero-left{flex:1;max-width:560px}
.hero-title{font-size:76px;line-height:.95;letter-spacing:-.04em;margin:18px 0 16px}
.hero-title .t-break{display:block;font-weight:800}
.hero-title .accent{color:var(--brand)}
.hero-sub{font-size:17px;line-height:1.8;color:var(--graphite)}
.hero-sub b{color:var(--ink);font-weight:700}
.hero-actions{margin-top:28px;display:flex;gap:14px}
.btn-primary{background:var(--brand);color:#fff;border:none;padding:14px 22px;border-radius:999px;font-weight:700;display:inline-flex;gap:8px;align-items:center;cursor:pointer;box-shadow:0 10px 24px rgba(37,99,235,.18);transition:transform .15s, background .15s}
.btn-primary:hover{background:var(--brand-deep);transform:translateY(-1px)}
.btn-ghost{background:#fff;border:1px solid var(--hair);padding:14px 22px;border-radius:999px;font-weight:700;display:inline-flex;gap:8px;align-items:center;cursor:pointer}
.btn-ghost:hover{border-color:var(--brand);color:var(--brand)}
.hero-meta{margin-top:18px;display:flex;gap:18px;font:500 13px/1 Inter;color:var(--graphite)}
.hero-meta .dot{width:6px;height:6px;border-radius:50%;background:var(--brand);display:inline-block;margin-right:6px}

/* product window */
.hero-visual{position:relative;width:620px;flex-shrink:0}
.product-window{background:#fff;border-radius:20px;overflow:hidden;border:1px solid var(--hair);box-shadow:0 24px 60px rgba(15,23,42,.12), 0 2px 0 rgba(15,23,42,.04)}
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

/* trust */
.trust{padding:22px 48px;background:#0B1220;color:#fff}
.trust-inner{max-width:980px;margin:0 auto;display:flex;justify-content:space-between;align-items:center}
.trust-item{display:flex;flex-direction:column;gap:4px;text-align:center;flex:1}
.trust-item strong{font:800 14px/1 Inter;letter-spacing:.08em}
.trust-item span{font:500 12px/1 Inter;color:#94A3B8}
.trust-sep{width:1px;height:36px;background:rgba(255,255,255,.10)}

/* feature */
.feature{padding:96px 48px;background:#fff}
.feature.alt{background:var(--cloud)}
.feature-inner{max-width:1180px;margin:0 auto;display:flex;gap:56px;align-items:center}
.feature-inner.reverse{flex-direction:row-reverse}
.feature-text{flex:1;min-width:0}
.feature-num{font:800 72px/1 'Noto Serif SC',serif;color:#EEF2FF;letter-spacing:-.04em;margin-bottom:-8px}
.eyebrow{margin-bottom:10px}
.feature-text h2{font-size:48px;line-height:1.05;letter-spacing:-.03em;font-weight:800}
.feature-text h2 .t-serif{color:var(--brand)}
.feature-text p{margin-top:14px;font-size:17px;line-height:1.8;color:var(--graphite)}
.feature-list{margin-top:18px;display:grid;gap:10px}
.feature-list li{list-style:none;display:flex;gap:10px;align-items:center;font:600 14px/1 Inter;color:var(--ink)}
.feature-list li .el-icon{color:var(--brand)}
.feature-visual{flex:1;display:flex;flex-direction:column;gap:16px}
.doc-stack{position:relative;background:#fff;border:1px solid var(--hair);border-radius:16px;padding:16px;overflow:hidden}
.doc{display:flex;align-items:center;gap:10px;padding:12px 14px;border-radius:12px;background:#F8FAFC;border:1px solid var(--hair);font:600 13px/1 Inter;animation:slideIn .5s both}
.doc.d2{animation-delay:.12s}.doc.d3{animation-delay:.24s}
.doc .status{margin-left:auto;font:700 11px/1 Inter;padding:5px 8px;border-radius:999px;background:#FEF3C7;color:#92400E}
.doc .status.done{background:#DCFCE7;color:#166534}
.doc-beam{position:absolute;right:22px;top:16px;bottom:16px;width:2px;background:linear-gradient(180deg, var(--brand), transparent);opacity:.5}
.doc-result{background:#0B1220;color:#E2E8F0;border-radius:14px;padding:16px}
.r-head{font:700 11px/1 'JetBrains Mono',monospace;letter-spacing:.08em;color:#94A3B8;margin-bottom:10px}
.r-item{font:500 13px/1.6 Inter;display:flex;gap:8px}
.r-dot{width:6px;height:6px;border-radius:50%;background:var(--brand);margin-top:7px}
.r-item.muted{color:#94A3B8}
.graph-stage{background:#fff;border:1px solid var(--hair);border-radius:16px;padding:14px}
.graph-svg{width:100%;height:220px}
.graph-svg .node circle{fill:#fff;stroke:var(--hair);stroke-width:1.2}
.graph-svg .node text{font:700 11px/1 Inter;fill:var(--ink);text-anchor:middle}
.graph-svg .node.small circle{fill:#EEF2FF}
.graph-svg .edges path{stroke:#CBD5E1;stroke-width:1.2;fill:none}
.graph-caption{text-align:center;font:600 12px/1 Inter;color:var(--graphite);margin-top:8px}
.ask-window{background:#0B1220;border-radius:16px;padding:16px;color:#E2E8F0}
.ask-head{font:700 11px/1 'JetBrains Mono',monospace;color:#94A3B8;display:flex;gap:6px;align-items:center}
.ask-qa{margin-top:12px;background:#111B2E;border-radius:12px;padding:14px}
.qa-q{font:600 13px/1.5 Inter;color:#E2E8F0}
.qa-a{margin-top:8px;font:500 13px/1.7 Inter;color:#CBD5E1}
.typewriter{border-right:2px solid var(--brand);animation:typing 3.2s steps(32,end) infinite, blink .9s step-end infinite}
.ask-sources{margin-top:12px;display:flex;gap:8px;flex-wrap:wrap}
.ask-sources span{font:700 10px/1 Inter;letter-spacing:.08em;color:#64748B}
.ask-sources span:not(:first-child){background:#1E293B;padding:5px 8px;border-radius:999px;color:#CBD5E1}

/* products etc keep previous but tighter */
.products{background:var(--cloud);padding:80px 48px}
.product-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:20px;max-width:1180px;margin:0 auto}
.product-card{background:#fff;border:1px solid var(--hair);border-radius:16px;padding:24px;display:flex;flex-direction:column}
.product-card.primary{border-color:var(--brand);box-shadow:0 12px 30px rgba(37,99,235,.12)}
.product-icon{width:44px;height:44px;border-radius:12px;background:#EEF2FF;color:var(--brand);display:flex;align-items:center;justify-content:center;font-size:20px;margin-bottom:16px}
.cases,.solutions,.resources,.download{padding:80px 48px}
.solution-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px;max-width:1100px;margin:0 auto}
.solution-card{background:#0B1220;color:#fff;border-radius:16px;padding:24px;border:1px solid rgba(255,255,255,.08)}
.download-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px;max-width:1100px;margin:0 auto 24px;align-items:stretch}.download-card{display:flex;flex-direction:column;background:#fff;border:1px solid var(--hair);border-radius:16px;padding:24px;text-align:center;height:100%;transition:all .2s ease}.download-card:hover{border-color:var(--brand);box-shadow:0 12px 30px rgba(15,23,42,.06)}.download-platform{font-size:16px;font-weight:800;color:var(--ink)}.download-version{font-size:13px;color:var(--brand);font-weight:700;margin-top:4px}.download-meta{flex:1;display:flex;flex-direction:column;gap:4px;font-size:13px;color:#6b7280;margin:12px 0 16px;min-height:48px}.download-meta .download-note{color:#9ca3af;font-size:12px;line-height:1.5;word-break:break-word}.download-btn{margin-top:auto;width:100%}
.case-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;max-width:1100px;margin:0 auto}
.resource-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px;max-width:1100px;margin:0 auto}
.cta{padding:72px 48px;background:linear-gradient(135deg,#EFF6FF 0%,#fff 100%);text-align:center}
.cta-inner h2{font-size:36px;font-weight:800}
.cta-actions{margin-top:18px;display:flex;gap:12px;justify-content:center}
.landing-footer{background:#0B0F19;color:#9CA3AF;padding:48px 48px 24px}
.footer-inner{max-width:1180px;margin:0 auto;display:grid;grid-template-columns:2fr 1fr 1fr 1.5fr;gap:32px}
.footer-bottom{max-width:1180px;margin:24px auto 0;padding-top:18px;border-top:1px solid #1F2937;display:flex;justify-content:space-between}

/* reveal */
[data-reveal]{opacity:0;transform:translateY(14px);transition:opacity .6s ease, transform .6s ease}
[data-reveal].is-in{opacity:1;transform:none}
@media (prefers-reduced-motion:reduce){[data-reveal]{opacity:1;transform:none}.float-card,.flow-item,.doc,.typewriter{animation:none}}

/* keyframes */
@keyframes flowIn{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:none}}
@keyframes floatY{0%,100%{transform:translateY(0)}50%{transform:translateY(-6px)}}
@keyframes slideIn{from{opacity:0;transform:translateX(-8px)}to{opacity:1;transform:none}}
@keyframes dash{to{stroke-dashoffset:-80}}
@keyframes typing{from{width:0}to{width:100%}}
@keyframes blink{50%{border-color:transparent}}

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
@media (max-width:1100px){.hero-inner{flex-direction:column}.hero-visual{width:100%;max-width:620px}.feature-inner,.feature-inner.reverse{flex-direction:column}.product-grid,.solution-grid,.download-grid,.resource-grid{grid-template-columns:repeat(2,1fr)}.case-grid{grid-template-columns:1fr}}
@media (max-width:720px){.hero{padding:96px 20px 40px}.hero-title{font-size:44px}.products,.cases,.solutions,.resources,.download{padding:56px 20px}.product-grid,.solution-grid,.download-grid,.case-grid,.resource-grid{grid-template-columns:1fr}.float-card{display:none}.trust-inner{flex-direction:column;gap:16px}.trust-sep{width:60px;height:1px}}
</style>
