/**
 * 知序产品家族配置 - 可扩展数据源
 * 新增产品仅需在此添加一条配置，官网 Hub 与详情页自动渲染
 * 如需 CMS 化，可替换为 API 拉取
 */
export type ProductId = 'kb' | 'workspace' | 'ocr' | 'sync'

/** 产品图标：仅允许 element-plus 已安装图标，拼写错误在编译期暴露 */
export type ProductIcon = 'Notebook' | 'Monitor' | 'SetUp' | 'DataLine'

export interface ProductConfig {
  id: ProductId
  slug: string // 路由 /products/:slug，全局唯一，仅小写字母数字连字符
  name: string
  shortName: string
  tagline: string // 一句话定位
  description: string
  longDesc: string
  icon: ProductIcon
  color: string // 品牌色
  gradient: string
  tags: string[]
  status: 'live' | 'coming'
  ctaLabel: string
  /** live 产品：登录后跳转的最终地址；coming 产品：点击卡片先进入详情页 */
  ctaPath: string
  stats?: { label: string; value: string }[]
  features: {
    num: string
    eyebrow: string
    title: string
    titleAccent: string
    desc: string
    bullets: string[]
  }[]
  heroVisual: ProductId // 对应 ProductDetailView 的视觉模板
}

export const products: ProductConfig[] = [
  {
    id: 'kb',
    slug: 'knowledge-base',
    name: '知序智能知识库',
    shortName: 'ZhiXu KB',
    tagline: '让知识从堆积，变成秩序',
    description: 'OCR 识别、AI 整理、知识图谱、RAG 问答一站式个人知识管理平台。',
    longDesc: '上传 PDF / Word / 图片 / 笔记，自动完成 OCR 与语义整理，形成可搜索、可关联、可问答的第二大脑。',
    icon: 'Notebook',
    color: '#2563EB',
    gradient: 'linear-gradient(135deg, #2563EB 0%, #60A5FA 100%)',
    tags: ['已上线', '免费版可用'],
    status: 'live',
    ctaLabel: 'Web 体验',
    ctaPath: '/notes',
    stats: [
      { label: '知识库笔记', value: '10k+' },
      { label: 'OCR 准确率', value: '99.2%' },
      { label: 'AI 响应', value: '500ms' },
    ],
    heroVisual: 'kb',
    features: [
      {
        num: '01',
        eyebrow: 'AI 理解 · Document Intelligence',
        title: '让每一份文档',
        titleAccent: '真正被理解',
        desc: '拖入 PDF / Word / 图片 / 笔记，自动完成 OCR、版式解析与语义切块。无需手动打标签，知识自己长出结构。',
        bullets: ['多格式解析 · 保留标题层级', '版面还原 · 表格与代码块识别', '自动摘要 · 关键词 · 分类'],
      },
      {
        num: '02',
        eyebrow: 'Knowledge Graph · 知识连接',
        title: '零散信息',
        titleAccent: '连成网络',
        desc: '基于实体抽取自动构建图谱，发现文档之间隐藏的关联。点击任意节点，回溯到原始出处。',
        bullets: ['实体关系抽取', 'Neo4j 图谱存储', '力导图可视化'],
      },
      {
        num: '03',
        eyebrow: 'AI Ask · 你的知识会回答',
        title: '不是搜索',
        titleAccent: '是直接给出答案',
        desc: '基于你自己的笔记回答，每一句都有来源。支持多轮追问与 SSE 流式输出。',
        bullets: ['RAG 检索增强', '引用溯源', '流式回答'],
      },
    ],
  },
  {
    id: 'ocr',
    slug: 'ocr-toolbox',
    name: '知序 OCR 工具箱',
    shortName: 'ZhiXu OCR',
    tagline: '本地离线，批量精准识别',
    description: '本地离线 OCR 识别套件，支持批量图片、PDF 与截图文字提取，数据不出本机。',
    longDesc: '基于 PaddleOCR / RapidOCR 混合引擎，无需联网即可完成高精度文字提取，支持表格与版面还原。',
    icon: 'SetUp',
    color: '#0EA5E9',
    gradient: 'linear-gradient(135deg, #0EA5E9 0%, #06B6D4 100%)',
    tags: ['客户端', 'Windows'],
    status: 'coming',
    ctaLabel: '预约下载',
    ctaPath: '/products/ocr-toolbox',
    heroVisual: 'ocr',
    features: [
      {
        num: '01',
        eyebrow: 'Local First · 本地优先',
        title: '离线可用',
        titleAccent: '数据不出本机',
        desc: '纯本地推理，无需上传云端。批量处理 2000 张扫描件，隐私与速度兼得。',
        bullets: ['RapidOCR ONNX 纯 CPU', 'PaddleOCR 高精度模式', '支持截图一键识别'],
      },
      {
        num: '02',
        eyebrow: 'Batch · 批量处理',
        title: '拖入即得',
        titleAccent: '无需逐张等待',
        desc: '文件夹拖入，自动队列处理，保留原文件名与目录结构，一键导出为 Word / Markdown。',
        bullets: ['批量图片/PDF', '自动透视矫正', '表格还原'],
      },
    ],
  },
  {
    id: 'workspace',
    slug: 'workspace',
    name: '知序 AI 工作台',
    shortName: 'ZhiXu Work',
    tagline: '团队的知识中枢',
    description: '面向团队的多人协作知识空间，权限管理、版本控制、AI 助手全集成。',
    longDesc: '为小团队打造的共享知识库，支持成员权限、协同编辑、版本对比与企业级私有化部署。',
    icon: 'Monitor',
    color: '#7C3AED',
    gradient: 'linear-gradient(135deg, #7C3AED 0%, #A78BFA 100%)',
    tags: ['即将上线'],
    status: 'coming',
    ctaLabel: '预约体验',
    ctaPath: '/products/workspace',
    heroVisual: 'workspace',
    features: [
      {
        num: '01',
        eyebrow: 'Collaboration · 协作',
        title: '团队共享',
        titleAccent: '权限可控',
        desc: '按项目组/角色分配可读、可写、管理员权限，公开分享与内部隔离并存。',
        bullets: ['组织与角色管理', '操作审计日志', '细粒度分享控制'],
      },
      {
        num: '02',
        eyebrow: 'Version · 版本',
        title: '每一次修改',
        titleAccent: '都可回溯',
        desc: '文档级历史快照，支持 Diff 对比与一键还原，告别误删与覆盖。',
        bullets: ['自动快照', '可视化对比', '一键恢复'],
      },
    ],
  },
  {
    id: 'sync',
    slug: 'sync',
    name: '知序数据同步助手',
    shortName: 'ZhiXu Sync',
    tagline: '多端同步，自动备份',
    description: '多端知识库同步工具，本地文件、云端与 NAS 一键同步备份。',
    longDesc: '监控本地文件夹变化，实时同步至云端或 NAS，支持增量备份与冲突合并。',
    icon: 'DataLine',
    color: '#059669',
    gradient: 'linear-gradient(135deg, #059669 0%, #34D399 100%)',
    tags: ['即将上线'],
    status: 'coming',
    ctaLabel: '预约体验',
    ctaPath: '/products/sync',
    heroVisual: 'sync',
    features: [
      {
        num: '01',
        eyebrow: 'Sync · 实时同步',
        title: '文件变动',
        titleAccent: '自动感知',
        desc: '文件系统监听，新增/修改/删除自动触发同步，无需手动上传。',
        bullets: ['监听本地目录', '增量同步', '断点续传'],
      },
    ],
  },
]

// 构建期断言：slug 唯一且格式合法，重复会静默串产品
{
  const slugs = products.map((p) => p.slug)
  if (new Set(slugs).size !== slugs.length) {
    throw new Error('[products] duplicate slug detected')
  }
  const bad = slugs.find((s) => !/^[a-z0-9-]+$/.test(s))
  if (bad) {
    throw new Error(`[products] invalid slug: ${bad}`)
  }
}

export function getProductBySlug(slug: string): ProductConfig | undefined {
  return products.find((p) => p.slug === slug)
}

export function getProductById(id: ProductId): ProductConfig | undefined {
  return products.find((p) => p.id === id)
}
