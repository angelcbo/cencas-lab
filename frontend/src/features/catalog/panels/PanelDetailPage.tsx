import { useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useForm, useFieldArray } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { toast } from '@/hooks/use-toast'
import { normalizeApiError } from '@/api/client'
import { panelsApi, PANELS_KEY } from './panelsApi'
import { PanelTestSelector } from './PanelTestSelector'

// ── Schema ──────────────────────────────────────────────────────────────────

const panelSchema = z.object({
  code: z.string().min(1, 'Requerido').max(50, 'Máximo 50 caracteres'),
  name: z.string().min(1, 'Requerido').max(255, 'Máximo 255 caracteres'),
  description: z.string().max(500, 'Máximo 500 caracteres').optional(),
  active: z.boolean(),
  tests: z
    .array(
      z.object({
        testId: z.string(),
        testCode: z.string(),
        testName: z.string(),
        displayOrder: z.coerce.number().int().min(1),
      }),
    )
    .min(1, 'Se requiere al menos una prueba'),
})

type FormValues = z.infer<typeof panelSchema>

// ── Component ────────────────────────────────────────────────────────────────

export function PanelDetailPage() {
  // Route-based mode: /catalog/panels/new has no :id param → create mode
  //                   /catalog/panels/:id has param → edit mode
  const { id } = useParams<{ id: string }>()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // Load panel (edit mode only)
  const { data: panel, isLoading: panelLoading } = useQuery({
    queryKey: [...PANELS_KEY, id],
    queryFn: () => panelsApi.getById(id!),
    enabled: isEdit,
  })

  // ── Form ────────────────────────────────────────────────────────────────

  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(panelSchema),
    defaultValues: {
      code: '',
      name: '',
      description: '',
      active: true,
      tests: [],
    },
  })

  const {
    fields: testFields,
    append: appendTest,
    remove: removeTest,
  } = useFieldArray({ control, name: 'tests' })

  // Populate form when panel data loads
  useEffect(() => {
    if (panel) {
      reset({
        code: panel.code,
        name: panel.name,
        description: panel.description ?? '',
        active: panel.active,
        tests: panel.tests.map((t) => ({
          testId: t.testId,
          testCode: t.testCode,
          testName: t.testName,
          displayOrder: t.displayOrder,
        })),
      })
    }
  }, [panel, reset])

  // ── Mutations ───────────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: (values: FormValues) =>
      panelsApi.create({
        code: values.code,
        name: values.name,
        description: values.description || undefined,
        tests: values.tests.map((t) => ({
          testId: t.testId,
          displayOrder: t.displayOrder,
        })),
      }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: PANELS_KEY })
      toast({ title: 'Panel creado' })
      navigate(`/catalog/panels/${created.id}`, { replace: true })
    },
    onError: handleMutationError,
  })

  const updateMutation = useMutation({
    mutationFn: (values: FormValues) =>
      panelsApi.update(id!, {
        code: values.code,
        name: values.name,
        description: values.description || undefined,
        active: values.active,
        tests: values.tests.map((t) => ({
          testId: t.testId,
          displayOrder: t.displayOrder,
        })),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PANELS_KEY })
      toast({ title: 'Panel actualizado' })
    },
    onError: handleMutationError,
  })

  function handleMutationError(err: unknown) {
    const apiErr = normalizeApiError(err)
    if (apiErr.status === 409) {
      setError('code', { message: 'Código ya existe en este laboratorio' })
    } else {
      toast({ title: 'Error', description: apiErr.message, variant: 'destructive' })
    }
  }

  const onSubmit = (values: FormValues) => {
    if (isEdit) {
      updateMutation.mutate(values)
    } else {
      createMutation.mutate(values)
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending || isSubmitting

  if (isEdit && panelLoading) {
    return <div className="p-6 text-sm text-muted-foreground">Cargando panel...</div>
  }

  const selectedTestIds = testFields.map((f) => f.testId)

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="p-6 max-w-3xl space-y-6">

      {/* Header */}
      <div className="flex items-center gap-3">
        <Link
          to="/catalog/panels"
          className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Paneles
        </Link>
        <span className="text-muted-foreground">/</span>
        <h1 className="text-xl font-semibold">
          {isEdit ? (panel ? `${panel.code} — ${panel.name}` : '…') : 'Nuevo panel'}
        </h1>
        {isEdit && panel && (
          <Badge variant={panel.active ? 'default' : 'outline'} className="ml-auto">
            {panel.active ? 'Activo' : 'Inactivo'}
          </Badge>
        )}
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>

        {/* ── General Information ────────────────────────────────────────── */}
        <div className="rounded-md border">
          <div className="px-4 py-3 border-b bg-muted/50">
            <h3 className="text-sm font-semibold">Información general</h3>
          </div>
          <div className="p-4 space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="code">Código</Label>
                <Input id="code" {...register('code')} placeholder="PANEL-BH" />
                {errors.code && (
                  <p className="text-xs text-destructive">{errors.code.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="name">Nombre</Label>
                <Input id="name" {...register('name')} placeholder="Panel bioquímico" />
                {errors.name && (
                  <p className="text-xs text-destructive">{errors.name.message}</p>
                )}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="description">Descripción</Label>
              <Input
                id="description"
                {...register('description')}
                placeholder="Descripción opcional"
              />
              {errors.description && (
                <p className="text-xs text-destructive">{errors.description.message}</p>
              )}
            </div>

            {isEdit && (
              <div className="flex items-center gap-2">
                <input
                  id="active"
                  type="checkbox"
                  className="h-4 w-4 rounded border-input"
                  {...register('active')}
                />
                <Label htmlFor="active" className="cursor-pointer">
                  Activo
                </Label>
              </div>
            )}
          </div>
        </div>

        {/* ── Tests ─────────────────────────────────────────────────────── */}
        <div className="rounded-md border">
          <div className="px-4 py-3 border-b bg-muted/50 flex items-center justify-between">
            <h3 className="text-sm font-semibold">Pruebas en el panel</h3>
            {(errors.tests as { message?: string } | undefined)?.message && (
              <p className="text-xs text-destructive">
                {(errors.tests as { message?: string }).message}
              </p>
            )}
          </div>
          <div className="p-4 space-y-4">
            <div className="rounded-md border">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-muted/50">
                    <th className="px-3 py-2 text-center font-medium text-muted-foreground w-20">Orden</th>
                    <th className="px-3 py-2 text-left font-medium text-muted-foreground">Código</th>
                    <th className="px-3 py-2 text-left font-medium text-muted-foreground">Nombre</th>
                    <th className="px-3 py-2 w-10" />
                  </tr>
                </thead>
                <tbody>
                  {testFields.length === 0 && (
                    <tr>
                      <td colSpan={4} className="px-3 py-6 text-center text-muted-foreground text-xs">
                        Sin pruebas. Agrega al menos una.
                      </td>
                    </tr>
                  )}
                  {testFields.map((field, index) => (
                    <tr key={field.id} className="border-b last:border-0">
                      <td className="px-3 py-2">
                        <Input
                          type="number"
                          min={1}
                          className="h-7 w-16 text-center text-xs"
                          {...register(`tests.${index}.displayOrder`, { valueAsNumber: true })}
                        />
                      </td>
                      <td className="px-3 py-2 font-mono text-xs">{field.testCode}</td>
                      <td className="px-3 py-2">{field.testName}</td>
                      <td className="px-3 py-2">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                          onClick={() => removeTest(index)}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          <span className="sr-only">Eliminar</span>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <PanelTestSelector
              excludeIds={selectedTestIds}
              nextOrder={testFields.length + 1}
              onAdd={(row) => appendTest(row)}
            />
          </div>
        </div>

        {/* ── Actions ────────────────────────────────────────────────────── */}
        <div className="flex justify-end gap-2 pt-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => navigate('/catalog/panels')}
            disabled={isPending}
          >
            Cancelar
          </Button>
          <Button type="submit" disabled={isPending}>
            {isPending ? 'Guardando...' : isEdit ? 'Guardar cambios' : 'Crear panel'}
          </Button>
        </div>
      </form>
    </div>
  )
}
