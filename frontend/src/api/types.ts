export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number   // current page (zero-indexed)
  size: number
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  correlationId: string
}

export interface UserInfo {
  id: string
  email: string
  firstName: string
  lastName: string
  role: 'SUPER_ADMIN' | 'LAB_ADMIN' | 'LAB_ANALYST' | 'LAB_RECEPTIONIST' | 'LAB_DOCTOR'
  tenantId: string | null
}
