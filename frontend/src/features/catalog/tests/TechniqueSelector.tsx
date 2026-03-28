import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { techniquesApi, TECHNIQUES_KEY } from '../techniques/techniquesApi'

export interface TechniqueOption {
  id: string
  code: string
  name: string
}

// Hook encapsulating the data-fetching strategy.
// To move to backend search: replace this hook body only.
function useTechniqueLookup() {
  const { data, isLoading, isError } = useQuery({
    queryKey: [...TECHNIQUES_KEY, 'lookup'],
    queryFn: () => techniquesApi.list(0, 200),
  })
  const options: TechniqueOption[] = (data?.content ?? [])
    .filter((t) => t.active)
    .map((t) => ({ id: t.id, code: t.code, name: t.name }))
  return { options, isLoading, isError }
}

export interface TechniqueRow {
  techniqueId: string
  techniqueCode: string
  techniqueName: string
}

interface Props {
  excludeIds: string[]
  onAdd: (row: TechniqueRow) => void
}

export function TechniqueSelector({ excludeIds, onAdd }: Props) {
  const [selectedId, setSelectedId] = useState('')

  const { options, isLoading, isError } = useTechniqueLookup()
  const available = options.filter((t) => !excludeIds.includes(t.id))

  const canAdd = !isLoading && !isError && Boolean(selectedId)

  const handleAdd = () => {
    if (!canAdd) return
    const technique = available.find((t) => t.id === selectedId)
    if (!technique) return
    onAdd({
      techniqueId: technique.id,
      techniqueCode: technique.code,
      techniqueName: technique.name,
    })
    setSelectedId('')
  }

  const placeholder = isLoading
    ? 'Cargando...'
    : isError
      ? 'Error al cargar'
      : available.length === 0
        ? 'Sin técnicas disponibles'
        : 'Seleccionar técnica'

  return (
    <div className="flex flex-wrap items-end gap-2 pt-3 border-t">
      <div className="flex-1 min-w-[200px] space-y-1">
        <Label className="text-xs text-muted-foreground">Técnica</Label>
        <Select value={selectedId} onValueChange={setSelectedId} disabled={isLoading || isError}>
          <SelectTrigger>
            <SelectValue placeholder={placeholder} />
          </SelectTrigger>
          <SelectContent>
            {available.map((t) => (
              <SelectItem key={t.id} value={t.id}>
                {t.code} — {t.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Button type="button" size="sm" onClick={handleAdd} disabled={!canAdd}>
        <Plus className="h-3.5 w-3.5 mr-1" />
        Agregar
      </Button>
    </div>
  )
}
