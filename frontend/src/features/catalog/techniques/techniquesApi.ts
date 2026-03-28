import { client } from '@/api/client'
import type { PageResponse } from '@/api/types'

export interface Technique {
  id: string
  tenantId: string
  code: string
  name: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateTechniqueRequest {
  code: string
  name: string
  description?: string
}

export interface UpdateTechniqueRequest {
  code: string
  name: string
  description?: string
  active: boolean
}

export const TECHNIQUES_KEY = ['catalog', 'techniques'] as const

export const techniquesApi = {
  list: (page: number, size = 20, search?: string) =>
    client
      .get<PageResponse<Technique>>('/api/v1/catalog/techniques', {
        params: {
          page,
          size,
          ...(search ? { search } : {}),
        },
      })
      .then((r) => r.data),

  create: (data: CreateTechniqueRequest) =>
    client.post<Technique>('/api/v1/catalog/techniques', data).then((r) => r.data),

  update: (id: string, data: UpdateTechniqueRequest) =>
    client.put<Technique>(`/api/v1/catalog/techniques/${id}`, data).then((r) => r.data),
}
