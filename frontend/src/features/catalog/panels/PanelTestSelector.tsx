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
import { testsApi, TESTS_KEY } from '../tests/testsApi'

export interface TestOption {
  id: string
  code: string
  name: string
}

// Hook encapsulating the data-fetching strategy.
// To move to backend search: replace this hook body only.
function useTestLookup() {
  const { data, isLoading, isError } = useQuery({
    queryKey: [...TESTS_KEY, 'lookup'],
    queryFn: () => testsApi.list(0, 200),
  })
  const options: TestOption[] = (data?.content ?? [])
    .filter((t) => t.active)
    .map((t) => ({ id: t.id, code: t.code, name: t.name }))
  return { options, isLoading, isError }
}

export interface PanelTestRow {
  testId: string
  testCode: string
  testName: string
  displayOrder: number
}

interface Props {
  excludeIds: string[]
  nextOrder: number
  onAdd: (row: PanelTestRow) => void
}

export function PanelTestSelector({ excludeIds, nextOrder, onAdd }: Props) {
  const [selectedId, setSelectedId] = useState('')
  const [displayOrder, setDisplayOrder] = useState(nextOrder)

  const { options, isLoading, isError } = useTestLookup()
  const available = options.filter((t) => !excludeIds.includes(t.id))

  const canAdd = !isLoading && !isError && Boolean(selectedId)

  const handleAdd = () => {
    if (!canAdd) return
    const test = available.find((t) => t.id === selectedId)
    if (!test) return
    onAdd({
      testId: test.id,
      testCode: test.code,
      testName: test.name,
      displayOrder,
    })
    setSelectedId('')
    setDisplayOrder(nextOrder + 1)
  }

  const placeholder = isLoading
    ? 'Cargando...'
    : isError
      ? 'Error al cargar'
      : available.length === 0
        ? 'Sin pruebas disponibles'
        : 'Seleccionar prueba'

  return (
    <div className="flex flex-wrap items-end gap-2 pt-3 border-t">
      <div className="flex-1 min-w-[200px] space-y-1">
        <Label className="text-xs text-muted-foreground">Prueba</Label>
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

      <Button type="button" size="sm" onClick={handleAdd} disabled={!canAdd}>
        <Plus className="h-3.5 w-3.5 mr-1" />
        Agregar
      </Button>
    </div>
  )
}
