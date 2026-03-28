import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Pencil } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/shared/components/PageHeader'
import { testsApi, TESTS_KEY } from './testsApi'

export function TestsPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const pageSize = 20

  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setSearch(searchInput), 400)
    return () => clearTimeout(timer)
  }, [searchInput])

  useEffect(() => {
    setPage(0)
  }, [search])

  const { data, isLoading, isError } = useQuery({
    queryKey: [...TESTS_KEY, page, search],
    queryFn: () => testsApi.list(page, pageSize, search),
  })

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title="Pruebas de Laboratorio"
        action={
          <Button onClick={() => navigate('/catalog/tests/new')} size="sm">
            Nueva prueba
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

      {isLoading && <div className="text-sm text-muted-foreground">Cargando...</div>}
      {isError && <div className="text-sm text-destructive">Error al cargar pruebas.</div>}

      {data && (
        <>
          <div className="rounded-md border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Código</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Nombre</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Tipo de Muestra</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">TAT (h)</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Precio</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Estado</th>
                  <th className="px-4 py-3 text-right font-medium text-muted-foreground">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                      {search ? 'Sin resultados para la búsqueda.' : 'No hay pruebas registradas.'}
                    </td>
                  </tr>
                )}
                {data.content.map((test) => (
                  <tr
                    key={test.id}
                    className="border-b last:border-0 hover:bg-muted/30 transition-colors"
                  >
                    <td className="px-4 py-3 font-mono font-medium">{test.code}</td>
                    <td className="px-4 py-3">{test.name}</td>
                    <td className="px-4 py-3 text-muted-foreground">{test.specimenTypeName}</td>
                    <td className="px-4 py-3 text-muted-foreground">{test.turnaroundTimeHours}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      ${Number(test.price).toFixed(2)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={test.active ? 'default' : 'outline'}>
                        {test.active ? 'Activo' : 'Inactivo'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => navigate(`/catalog/tests/${test.id}`)}
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
    </div>
  )
}
