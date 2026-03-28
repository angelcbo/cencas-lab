import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/shared/components/PageHeader'
import {
  collectionContainersApi,
  COLLECTION_CONTAINERS_KEY,
  type CollectionContainer,
} from './collectionContainersApi'
import { CollectionContainerFormDialog } from './CollectionContainerFormDialog'

export function CollectionContainersPage() {
  const [page, setPage] = useState(0)
  const pageSize = 20

  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<CollectionContainer | undefined>()

  useEffect(() => {
    const timer = setTimeout(() => setSearch(searchInput), 400)
    return () => clearTimeout(timer)
  }, [searchInput])

  useEffect(() => {
    setPage(0)
  }, [search])

  const { data, isLoading, isError } = useQuery({
    queryKey: [...COLLECTION_CONTAINERS_KEY, page, search],
    queryFn: () => collectionContainersApi.list(page, pageSize, search),
  })

  const openCreate = () => {
    setEditTarget(undefined)
    setDialogOpen(true)
  }

  const openEdit = (container: CollectionContainer) => {
    setEditTarget(container)
    setDialogOpen(true)
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title="Contenedores de Recolección"
        action={
          <Button onClick={openCreate} size="sm">
            Nuevo contenedor
          </Button>
        }
      />

      <Input
        type="search"
        placeholder="Buscar por código o nombre..."
        value={searchInput}
        onChange={(e) => setSearchInput(e.target.value)}
        className="max-w-sm"
      />

      {isLoading && (
        <div className="text-sm text-muted-foreground">Cargando...</div>
      )}

      {isError && (
        <div className="text-sm text-destructive">Error al cargar contenedores.</div>
      )}

      {data && (
        <>
          <div className="rounded-md border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Código</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Nombre</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Color</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Tipo de Muestra</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Estado</th>
                  <th className="px-4 py-3 text-right font-medium text-muted-foreground">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">
                      {search ? 'Sin resultados para la búsqueda.' : 'No hay contenedores registrados.'}
                    </td>
                  </tr>
                )}
                {data.content.map((container) => (
                  <tr key={container.id} className="border-b last:border-0 hover:bg-muted/30 transition-colors">
                    <td className="px-4 py-3 font-mono font-medium">{container.code}</td>
                    <td className="px-4 py-3">{container.name}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {container.color ?? '—'}
                    </td>
                    <td className="px-4 py-3">{container.specimenTypeName}</td>
                    <td className="px-4 py-3">
                      <Badge variant={container.active ? 'default' : 'outline'}>
                        {container.active ? 'Activo' : 'Inactivo'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => openEdit(container)}
                        className="h-7 w-7 p-0"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                        <span className="sr-only">Editar</span>
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-muted-foreground">
              <span>
                Página {data.number + 1} de {data.totalPages} ({data.totalElements} registros)
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  Anterior
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page >= data.totalPages - 1}
                >
                  Siguiente
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <CollectionContainerFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        initialValues={editTarget}
      />
    </div>
  )
}
