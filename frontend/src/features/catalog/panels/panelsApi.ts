import { client } from '@/api/client'
import type { PageResponse } from '@/api/types'

export interface PanelSummary {
  id: string
  tenantId: string
  code: string
  name: string
  description: string | null
  active: boolean
  testCount: number
  createdAt: string
  updatedAt: string
}

export interface PanelTestDetail {
  id: string
  testId: string
  testCode: string
  testName: string
  displayOrder: number
}

export interface Panel {
  id: string
  tenantId: string
  code: string
  name: string
  description: string | null
  active: boolean
  tests: PanelTestDetail[]
  createdAt: string
  updatedAt: string
}

export interface CreatePanelRequest {
  code: string
  name: string
  description?: string
  tests: { testId: string; displayOrder: number }[]
}

export interface UpdatePanelRequest extends CreatePanelRequest {
  active: boolean
}

export const PANELS_KEY = ['catalog', 'panels'] as const

export const panelsApi = {
  list: (page: number, size = 20, search?: string) =>
    client
      .get<PageResponse<PanelSummary>>('/api/v1/catalog/panels', {
        params: { page, size, ...(search ? { search } : {}) },
      })
      .then((r) => r.data),

  getById: (id: string) =>
    client.get<Panel>(`/api/v1/catalog/panels/${id}`).then((r) => r.data),

  create: (data: CreatePanelRequest) =>
    client.post<Panel>('/api/v1/catalog/panels', data).then((r) => r.data),

  update: (id: string, data: UpdatePanelRequest) =>
    client.put<Panel>(`/api/v1/catalog/panels/${id}`, data).then((r) => r.data),
}
