import { driver, type Driver, type DriveStep } from 'driver.js'
import 'driver.js/dist/driver.css'

/**
 * 新手引导（网页端与桌面版共用）：
 * 首次进入工作台（/notes）时自动播放；可从设置页/AppHeader 重新触发。
 * 完成状态存 localStorage，桌面版与网页版在本机各自记录。
 */

const DONE_KEY = 'zhixu_onboarding_v1'

export function shouldRunTour(): boolean {
  try {
    return localStorage.getItem(DONE_KEY) !== '1'
  } catch {
    return false
  }
}

export function markTourDone(): void {
  try {
    localStorage.setItem(DONE_KEY, '1')
  } catch {
    // 忽略存储失败（隐私模式等）
  }
}

export function resetTour(): void {
  try {
    localStorage.removeItem(DONE_KEY)
  } catch {
    // ignore
  }
}

let activeDriver: Driver | null = null

/** 工作台分步引导（请在 /notes 页且元素渲染完成后调用） */
export function runWorkspaceTour(onDone?: () => void): void {
  if (activeDriver) {
    try {
      activeDriver.destroy()
    } catch {
      // ignore
    }
  }

  const steps: DriveStep[] = [
    {
      popover: {
        title: '欢迎使用知序知识库 👋',
        description:
          '用 30 秒了解核心功能：把照片、文档、碎片想法变成可检索、可对话的个人知识体系。随时可按 Esc 跳过。'
      }
    },
    {
      element: '#tour-create-note',
      popover: {
        title: '从这里开始创作',
        description: '点击「新建笔记」记录想法；也可以直接上传图片/PDF/Word，系统会自动识别与整理。',
        side: 'bottom',
        align: 'start'
      }
    },
    {
      element: '.sidebar-nav',
      popover: {
        title: '五大功能模块',
        description:
          '知识笔记：写作与沉淀；知识问答：像聊天一样检索你自己的知识库；知识图谱：自动抽取实体关系；分类管理：组织你的知识体系。',
        side: 'right',
        align: 'start'
      }
    },
    {
      element: '#tour-task-center',
      popover: {
        title: '任务中心',
        description: '上传解析、AI 整理、图谱构建都在后台异步执行，这里实时查看进度，失败可一键重试。',
        side: 'bottom',
        align: 'end'
      }
    },
    {
      element: '#nav-ai-settings',
      popover: {
        title: '配置你自己的 AI',
        description:
          '默认使用平台内置模型；在 AI 设置里填入自己的 API Key（DeepSeek/OpenAI/Kimi/智谱等 8 家），额度更充足、响应更稳定。',
        side: 'right',
        align: 'start'
      }
    },
    {
      popover: {
        title: '一切就绪 🎉',
        description: '建议从上传一份文档开始体验完整流程：上传 → 自动识别 → AI 整理 → 问答。祝你知识管理愉快！'
      }
    }
  ]

  activeDriver = driver({
    steps,
    showProgress: true,
    progressText: '{{current}} / {{total}}',
    nextBtnText: '下一步',
    prevBtnText: '上一步',
    doneBtnText: '完成',
    allowClose: true,
    smoothScroll: true,
    onDestroyed: () => {
      markTourDone()
      activeDriver = null
      onDone?.()
    }
  })

  activeDriver.drive()
}
