import { client } from '@/api/client'
import type { UserInfo } from '@/api/types'

export interface LoginCredentials {
  email: string
  password: string
  tenantSlug?: string
}

interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

interface RefreshResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export const authApi = {
  login: (credentials: LoginCredentials) =>
    client.post<LoginResponse>('/api/v1/auth/login', {
      email: credentials.email,
      password: credentials.password,
      tenantSlug: credentials.tenantSlug || null,
    }).then((r) => r.data),

  refresh: () =>
    client.post<RefreshResponse>('/api/v1/auth/refresh').then((r) => r.data),

  logout: () =>
    client.post('/api/v1/auth/logout').then(() => undefined),
}
