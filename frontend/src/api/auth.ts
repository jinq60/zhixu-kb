import http from './http'
import type { UserInfo } from '../stores/auth'

export interface LoginForm {
  username: string
  password: string
}

export interface EmailCodeLoginForm {
  email: string
  code: string
}

export interface SmsCodeLoginForm {
  phone: string
  code: string
}

/** 账号密码登录（账号不存在时自动注册） */
export async function login(form: LoginForm): Promise<string> {
  const { data } = await http.post('/api/auth/login', form)
  return data.data.token
}

/** 发送邮箱验证码 */
export async function sendEmailCode(email: string): Promise<void> {
  await http.post('/api/auth/email-code/send', { email })
}

/** 邮箱验证码登录（首次登录自动注册） */
export async function emailCodeLogin(form: EmailCodeLoginForm): Promise<string> {
  const { data } = await http.post('/api/auth/email-code/login', form)
  return data.data.token
}

/** 发送短信验证码 */
export async function sendSmsCode(phone: string): Promise<void> {
  await http.post('/api/auth/sms-code/send', { phone })
}

/** 短信验证码登录（首次登录自动注册） */
export async function smsCodeLogin(form: SmsCodeLoginForm): Promise<string> {
  const { data } = await http.post('/api/auth/sms-code/login', form)
  return data.data.token
}

/** 获取第三方 OAuth 授权地址 */
export function oauthAuthorizeUrl(provider: 'github' | 'google' | 'qq'): string {
  return `${import.meta.env.VITE_API_BASE || ''}/api/auth/oauth/${provider}/authorize`
}

/** OAuth 回调：用一次性 code 换取 JWT */
export async function oauthExchange(code: string): Promise<string> {
  const { data } = await http.post('/api/auth/oauth/exchange', { code })
  return data.data.token
}

export async function fetchUserInfo(): Promise<UserInfo> {
  const { data } = await http.get('/api/auth/info')
  return data.data
}

/** 调用后端撤销当前 Token */
export async function logout(): Promise<void> {
  await http.post('/api/auth/logout')
}

export interface UserProfile {
  id: number
  username: string
  email: string
  avatar?: string
  roles: string[]
  hasPassword: boolean
  bindings: string[]
}

export async function fetchUserProfile(): Promise<UserProfile> {
  const { data } = await http.get('/api/user/profile')
  return data.data
}

export async function updatePassword(oldPassword: string, newPassword: string): Promise<void> {
  await http.post('/api/user/password', { oldPassword, newPassword })
}

export async function sendBindEmailCode(email: string): Promise<void> {
  await http.post('/api/user/bind/email/send', { email })
}

export async function bindEmail(email: string, code: string): Promise<void> {
  await http.post('/api/user/bind/email', { email, code })
}


