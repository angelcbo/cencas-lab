import { client } from '@/api/client'
import type { PageResponse } from '@/api/types'

export interface LabTestSummary {
  id: string
  tenantId: string
  code: string
  name: string
  specimenTypeId: string
  specimenTypeName: string
  turnaroundTimeHours: number
  price: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface TestAnalyteDetail {
  id: string
  analyteId: string
  analyteCode: string
  analyteName: string
  defaultUnit: string | null
  displayOrder: number
  reportable: boolean
}

export interface TestTechniqueDetail {
  id: string
  techniqueId: string
  techniqueCode: string
  techniqueName: string
}

export interface TestContainerDetail {
  id: string
  collectionContainerId: string
  containerCode: string
  containerName: string
  required: boolean
}

export interface LabTest {
  id: string
  tenantId: string
  code: string
  name: string
  specimenTypeId: string
  specimenTypeName: string
  turnaroundTimeHours: number
  price: number
  active: boolean
  analytes: TestAnalyteDetail[]
  techniques: TestTechniqueDetail[]
  containers: TestContainerDetail[]
  createdAt: string
  updatedAt: string
}

export interface CreateLabTestRequest {
  code: string
  name: string
  specimenTypeId: string
  turnaroundTimeHours: number
  price: number
  analytes: { analyteId: string; displayOrder: number; reportable: boolean }[]
  techniqueIds: string[]
  containers: { collectionContainerId: string; required: boolean }[]
}

export interface UpdateLabTestRequest extends CreateLabTestRequest {
  active: boolean
}

export const TESTS_KEY = ['catalog', 'tests'] as const

export const testsApi = {
  list: (page: number, size = 20, search?: string) =>
    client
      .get<PageResponse<LabTestSummary>>('/api/v1/catalog/tests', {
        params: { page, size, ...(search ? { search } : {}) },
      })
      .then((r) => r.data),

  getById: (id: string) =>
    client.get<LabTest>(`/api/v1/catalog/tests/${id}`).then((r) => r.data),

  create: (data: CreateLabTestRequest) =>
    client.post<LabTest>('/api/v1/catalog/tests', data).then((r) => r.data),

  update: (id: string, data: UpdateLabTestRequest) =>
    client.put<LabTest>(`/api/v1/catalog/tests/${id}`, data).then((r) => r.data),
}
