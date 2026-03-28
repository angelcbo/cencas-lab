import { client } from '@/api/client'
import type { PageResponse } from '@/api/types'

export interface CollectionContainer {
  id: string
  tenantId: string
  code: string
  name: string
  color: string | null
  specimenTypeId: string
  specimenTypeName: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateCollectionContainerRequest {
  code: string
  name: string
  color?: string
  specimenTypeId: string
  description?: string
}

export interface UpdateCollectionContainerRequest {
  code: string
  name: string
  color?: string
  specimenTypeId: string
  description?: string
  active: boolean
}

export const COLLECTION_CONTAINERS_KEY = ['catalog', 'collection-containers'] as const

export const collectionContainersApi = {
  list: (page: number, size = 20, search?: string) =>
    client
      .get<PageResponse<CollectionContainer>>('/api/v1/catalog/collection-containers', {
        params: {
          page,
          size,
          ...(search ? { search } : {}),
        },
      })
      .then((r) => r.data),

  create: (data: CreateCollectionContainerRequest) =>
    client
      .post<CollectionContainer>('/api/v1/catalog/collection-containers', data)
      .then((r) => r.data),

  update: (id: string, data: UpdateCollectionContainerRequest) =>
    client
      .put<CollectionContainer>(`/api/v1/catalog/collection-containers/${id}`, data)
      .then((r) => r.data),
}
