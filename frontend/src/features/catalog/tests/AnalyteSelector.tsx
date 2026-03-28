import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { analytesApi, ANALYTES_KEY } from '../analytes/analytesApi'

// Lightweight option type — only what the selector needs
export interface AnalyteOption {
  id: string
  code: string
  name: string
  defaultUnit: string | null
}

// Hook that encapsulates the data-fetching strategy.
// To move to backend search: replace this hook body only — the component stays the same.
function useAnalyteLookup() {
  const { data, isLoading, isError } = useQuery({
    queryKey: [...ANALYTES_KEY, 'lookup'],
    queryFn: () => analytesApi.list(0, 200),
  })
  const options: AnalyteOption[] = (data?.content ?? [])
    .filter((a) => a.active)
    .map((a) => ({ id: a.id, code: a.code, name: a.name, defaultUnit: a.defaultUnit }))
  return { options, isLoading, isError }
}

export interface AnalyteRow {
  analyteId: string
  analyteCode: string
  analyteName: string
  defaultUnit: string | null
  displayOrder: number
  reportable: boolean
}

interface Props {
  excludeIds: string[]
  nextOrder: number
  onAdd: (row: AnalyteRow) => void
}

export function AnalyteSelector({ excludeIds, nextOrder, onAdd }: Props) {
  const [selectedId, setSelectedId] = useState('')
  const [displayOrder, setDisplayOrder] = useState(nextOrder)
  const [reportable, setReportable] = useState(true)

  const { options, isLoading, isError } = useAnalyteLookup()
  const available = options.filter((a) => !excludeIds.includes(a.id))

  const canAdd = !isLoading && !isError && Boolean(selectedId)

  const handleAdd = () => {
    if (!canAdd) return
    const analyte = available.find((a) => a.id === selectedId)
    if (!analyte) return
    onAdd({
      analyteId: analyte.id,
      analyteCode: analyte.code,
      analyteName: analyte.name,
      defaultUnit: analyte.defaultUnit,
      displayOrder,
      reportable,
    })
    setSelectedId('')
    setDisplayOrder(nextOrder + 1)
    setReportable(true)
  }

  const placeholder = isLoading
    ? 'Cargando...'
    : isError
      ? 'Error al cargar'
      : available.length === 0
        ? 'Sin analitos disponibles'
        : 'Seleccionar analito'

  return (
    <div className="flex flex-wrap items-end gap-2 pt-3 border-t">
      <div className="flex-1 min-w-[200px] space-y-1">
        <Label className="text-xs text-muted-foreground">Analito</Label>
        <Select value={selectedId} onValueChange={setSelectedId} disabled={isLoading || isError}>
          <SelectTrigger>
            <SelectValue placeholder={placeholder} />
          </SelectTrigger>
          <SelectContent>
            {available.map((a) => (
              <SelectItem key={a.id} value={a.id}>
                {a.code} — {a.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="w-20 space-y-1">
        <Label className="text-xs text-muted-foreground">Orden</Label>
        <Input
          type="number"
          min={1}
          value={displayOrder}
          onChange={(e) => setDisplayOrder(Number(e.target.value))}
          disabled={isLoading || isError}
        />
      </div>

      <div className="flex items-center gap-1.5 pb-1">
        <input
          id="rep-new"
          type="checkbox"
          className="h-4 w-4 rounded border-input"
          checked={reportable}
          onChange={(e) => setReportable(e.target.checked)}
          disabled={isLoading || isError}
        />
        <Label htmlFor="rep-new" className="text-xs cursor-pointer text-muted-foreground">
          Reportable
        </Label>
      </div>

      <Button type="button" size="sm" onClick={handleAdd} disabled={!canAdd}>
        <Plus className="h-3.5 w-3.5 mr-1" />
        Agregar
      </Button>
    </div>
  )
}
