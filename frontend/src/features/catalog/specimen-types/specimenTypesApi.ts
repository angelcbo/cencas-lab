import { client } from '@/api/client'
import type { PageResponse } from '@/api/types'

export interface SpecimenType {
  id: string
  tenantId: string
  code: string
  name: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateSpecimenTypeRequest {
  code: string
  name: string
  description?: string
}

export interface UpdateSpecimenTypeRequest {
  code: string
  name: string
  description?: string
  active: boolean
}

export const SPECIMEN_TYPES_KEY = ['catalog', 'specimen-types'] as const

export const specimenTypesApi = {
  list: (page: number, size = 20, search?: string) =>
    client
      .get<PageResponse<SpecimenType>>('/api/v1/catalog/specimen-types', {
        params: {
          page,
          size,
          ...(search ? { search } : {}),
        },
      })
      .then((r) => r.data),

  create: (data: CreateSpecimenTypeRequest) =>
    client.post<SpecimenType>('/api/v1/catalog/specimen-types', data).then((r) => r.data),

  update: (id: string, data: UpdateSpecimenTypeRequest) =>
    client.put<SpecimenType>(`/api/v1/catalog/specimen-types/${id}`, data).then((r) => r.data),
}
