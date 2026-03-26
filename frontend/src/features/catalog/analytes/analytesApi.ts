import { client } from '@/api/client'
import type { PageResponse } from '@/api/types'

export interface Analyte {
  id: string
  tenantId: string
  code: string
  name: string
  defaultUnit: string | null
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE'
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateAnalyteRequest {
  code: string
  name: string
  defaultUnit?: string
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE'
}

export interface UpdateAnalyteRequest {
  code: string
  name: string
  defaultUnit?: string
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE'
  active: boolean
}

export const ANALYTES_KEY = ['catalog', 'analytes'] as const

export const analytesApi = {
  list: (page: number, size = 20, search?: string) =>
    client
      .get<PageResponse<Analyte>>('/api/v1/catalog/analytes', {
        params: {
          page,
          size,
          ...(search ? { search } : {}),
        },
      })
      .then((r) => r.data),

  create: (data: CreateAnalyteRequest) =>
    client.post<Analyte>('/api/v1/catalog/analytes', data).then((r) => r.data),

  update: (id: string, data: UpdateAnalyteRequest) =>
    client.put<Analyte>(`/api/v1/catalog/analytes/${id}`, data).then((r) => r.data),
}
