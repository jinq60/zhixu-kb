import type { Component } from 'vue'
import type { Router } from 'vue-router'
import { DataLine, Monitor, Notebook, SetUp } from '@element-plus/icons-vue'

/**
 * 官网产品目录（唯一来源）：
 * 后续上架新产品时，只需在这里追加一条并在 HomeView 里补配套展示字段（图片/卖点），
 * 导航 mega 菜单会自动出现，无需改导航栏代码。
 */
export interface SiteProduct {
  /** 稳定键，上架后勿改 */
  key: string
  /** 产品名 */
  label: string
  /** 一句话描述 */
  desc: string
  /** 图标组件 */
  icon: Component
  /** 产品主题色（导航/落地页共用） */
  color: string
  /** 未上线：点击只回落地页 */
  coming?: boolean
  /** 站内路由（与 href 二选一，优先路由） */
  route?: string
  /** 站外链接（新窗口打开） */
  href?: string
}

export const SITE_PRODUCTS: SiteProduct[] = [
  {
    key: 'kb',
    label: '知序智能知识库',
    desc: 'OCR + AI 整理 + 知识图谱',
    icon: Notebook,
    color: '#f2641e',
    route: '/notes'
  },
  {
    key: 'workbench',
    label: '知序 AI 工作台',
    desc: '面向团队的智能协作平台',
    icon: Monitor,
    color: '#7a5af8',
    coming: true
  },
  {
    key: 'ocr',
    label: '知序 OCR 工具箱',
    desc: '本地离线 OCR 识别套件',
    icon: SetUp,
    color: '#0ca789',
    coming: true
  },
  {
    key: 'sync',
    label: '知序数据同步助手',
    desc: '多端知识库同步工具',
    icon: DataLine,
    color: '#d9930d',
    coming: true
  }
]

/** 产品跳转：未上线回落地页；站内走路由；站外新窗口打开 */
export function navigateToProduct(router: Router, p: SiteProduct) {
  if (p.coming) {
    router.push('/home')
    return
  }
  if (p.route) {
    router.push(p.route)
    return
  }
  if (p.href) {
    window.open(p.href, '_blank', 'noopener')
  }
}
