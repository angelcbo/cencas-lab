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
import { collectionContainersApi, COLLECTION_CONTAINERS_KEY } from '../collection-containers/collectionContainersApi'

export interface ContainerOption {
  id: string
  code: string
  name: string
}

// Hook encapsulating the data-fetching strategy.
// To move to backend search: replace this hook body only.
function useContainerLookup() {
  const { data, isLoading, isError } = useQuery({
    queryKey: [...COLLECTION_CONTAINERS_KEY, 'lookup'],
    queryFn: () => collectionContainersApi.list(0, 200),
  })
  const options: ContainerOption[] = (data?.content ?? [])
    .filter((c) => c.active)
    .map((c) => ({ id: c.id, code: c.code, name: c.name }))
  return { options, isLoading, isError }
}

export interface ContainerRow {
  collectionContainerId: string
  containerCode: string
  containerName: string
  required: boolean
}

interface Props {
  excludeIds: string[]
  onAdd: (row: ContainerRow) => void
}

export function CollectionContainerSelector({ excludeIds, onAdd }: Props) {
  const [selectedId, setSelectedId] = useState('')
  const [required, setRequired] = useState(true)

  const { options, isLoading, isError } = useContainerLookup()
  const available = options.filter((c) => !excludeIds.includes(c.id))

  const canAdd = !isLoading && !isError && Boolean(selectedId)

  const handleAdd = () => {
    if (!canAdd) return
    const container = available.find((c) => c.id === selectedId)
    if (!container) return
    onAdd({
      collectionContainerId: container.id,
      containerCode: container.code,
      containerName: container.name,
      required,
    })
    setSelectedId('')
    setRequired(true)
  }

  const placeholder = isLoading
    ? 'Cargando...'
    : isError
      ? 'Error al cargar'
      : available.length === 0
        ? 'Sin contenedores disponibles'
        : 'Seleccionar contenedor'

  return (
    <div className="flex flex-wrap items-end gap-2 pt-3 border-t">
      <div className="flex-1 min-w-[200px] space-y-1">
        <Label className="text-xs text-muted-foreground">Contenedor</Label>
        <Select value={selectedId} onValueChange={setSelectedId} disabled={isLoading || isError}>
          <SelectTrigger>
            <SelectValue placeholder={placeholder} />
          </SelectTrigger>
          <SelectContent>
            {available.map((c) => (
              <SelectItem key={c.id} value={c.id}>
                {c.code} — {c.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center gap-1.5 pb-1">
        <input
          id="req-new"
          type="checkbox"
          className="h-4 w-4 rounded border-input"
          checked={required}
          onChange={(e) => setRequired(e.target.checked)}
          disabled={isLoading || isError}
        />
        <Label htmlFor="req-new" className="text-xs cursor-pointer text-muted-foreground">
          Requerido
        </Label>
      </div>

      <Button type="button" size="sm" onClick={handleAdd} disabled={!canAdd}>
        <Plus className="h-3.5 w-3.5 mr-1" />
        Agregar
      </Button>
    </div>
  )
}
