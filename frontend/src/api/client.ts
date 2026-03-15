import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError } from './types'

// --- Token store (module-level, never persisted to storage) ---

let _accessToken: string | null = null
let _onUnauthorized: (() => void) | null = null

export function setAccessToken(token: string) {
  _accessToken = token
}

export function clearAccessToken() {
  _accessToken = null
}

export function registerUnauthorizedHandler(handler: () => void) {
  _onUnauthorized = handler
}

// --- Axios instance ---

export const client = axios.create({
  baseURL: '/',
  withCredentials: true,   // send httpOnly refresh cookie on every request
})

// Request interceptor: attach Bearer token
client.interceptors.request.use((config) => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`
  }
  return config
})

// Response interceptor: handle 401 with one refresh attempt
interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryConfig | undefined

    if (
      error.response?.status !== 401 ||
      config?._retry ||
      config?.url?.endsWith('/api/v1/auth/refresh')
    ) {
      return Promise.reject(error)
    }

    config._retry = true

    try {
      const { data } = await client.post<{ accessToken: string }>('/api/v1/auth/refresh')
      setAccessToken(data.accessToken)
      if (config.headers) {
        config.headers.Authorization = `Bearer ${data.accessToken}`
      }
      return client(config)
    } catch {
      clearAccessToken()
      _onUnauthorized?.()
      return Promise.reject(error)
    }
  },
)

// --- Error normalization ---

export function normalizeApiError(err: unknown): ApiError {
  if (axios.isAxiosError(err) && err.response?.data) {
    const data = err.response.data as Partial<ApiError>
    return {
      timestamp: data.timestamp ?? new Date().toISOString(),
      status: data.status ?? err.response.status,
      error: data.error ?? err.response.statusText,
      message: data.message ?? 'Error desconocido',
      path: data.path ?? '',
      correlationId: data.correlationId ?? '',
    }
  }
  return {
    timestamp: new Date().toISOString(),
    status: 0,
    error: 'Network Error',
    message: 'Error de conexión',
    path: '',
    correlationId: '',
  }
}
