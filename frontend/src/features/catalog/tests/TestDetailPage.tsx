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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { toast } from '@/hooks/use-toast'
import { normalizeApiError } from '@/api/client'
import { specimenTypesApi, SPECIMEN_TYPES_KEY } from '../specimen-types/specimenTypesApi'
import { testsApi, TESTS_KEY } from './testsApi'
import { AnalyteSelector } from './AnalyteSelector'
import { TechniqueSelector } from './TechniqueSelector'
import { CollectionContainerSelector } from './CollectionContainerSelector'

// ── Schema ──────────────────────────────────────────────────────────────────

const testSchema = z.object({
  code: z.string().min(1, 'Requerido').max(50, 'Máximo 50 caracteres'),
  name: z.string().min(1, 'Requerido').max(255, 'Máximo 255 caracteres'),
  specimenTypeId: z.string().min(1, 'Requerido'),
  turnaroundTimeHours: z.coerce
    .number({ invalid_type_error: 'Requerido' })
    .int('Debe ser entero')
    .min(1, 'Mínimo 1 hora'),
  price: z.coerce
    .number({ invalid_type_error: 'Requerido' })
    .min(0, 'Precio inválido'),
  active: z.boolean(),
  analytes: z
    .array(
      z.object({
        analyteId: z.string(),
        analyteCode: z.string(),
        analyteName: z.string(),
        defaultUnit: z.string().nullable(),
        displayOrder: z.coerce.number().int().min(1),
        reportable: z.boolean(),
      }),
    )
    .min(1, 'Se requiere al menos un analito'),
  techniques: z.array(
    z.object({
      techniqueId: z.string(),
      techniqueCode: z.string(),
      techniqueName: z.string(),
    }),
  ),
  containers: z
    .array(
      z.object({
        collectionContainerId: z.string(),
        containerCode: z.string(),
        containerName: z.string(),
        required: z.boolean(),
      }),
    )
    .min(1, 'Se requiere al menos un contenedor'),
})

type FormValues = z.infer<typeof testSchema>

// ── Component ────────────────────────────────────────────────────────────────

export function TestDetailPage() {
  // Route-based mode: /catalog/tests/new has no :id param → create mode
  //                   /catalog/tests/:id has param → edit mode
  const { id } = useParams<{ id: string }>()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // Load test (edit mode only)
  const { data: test, isLoading: testLoading } = useQuery({
    queryKey: [...TESTS_KEY, id],
    queryFn: () => testsApi.getById(id!),
    enabled: isEdit,
  })

  // Load specimen types for the select
  const { data: specimenTypesPage, isLoading: stLoading } = useQuery({
    queryKey: [...SPECIMEN_TYPES_KEY, 'lookup'],
    queryFn: () => specimenTypesApi.list(0, 100),
  })
  const specimenTypes = (specimenTypesPage?.content ?? []).filter((s) => s.active)

  // ── Form ────────────────────────────────────────────────────────────────

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(testSchema),
    defaultValues: {
      code: '',
      name: '',
      specimenTypeId: '',
      turnaroundTimeHours: 24,
      price: 0,
      active: true,
      analytes: [],
      techniques: [],
      containers: [],
    },
  })

  const specimenTypeId = watch('specimenTypeId')

  // Field arrays for relationships
  const {
    fields: analyteFields,
    append: appendAnalyte,
    remove: removeAnalyte,
  } = useFieldArray({ control, name: 'analytes' })

  const {
    fields: techniqueFields,
    append: appendTechnique,
    remove: removeTechnique,
  } = useFieldArray({ control, name: 'techniques' })

  const {
    fields: containerFields,
    append: appendContainer,
    remove: removeContainer,
  } = useFieldArray({ control, name: 'containers' })

  // Populate form when test data loads
  useEffect(() => {
    if (test) {
      reset({
        code: test.code,
        name: test.name,
        specimenTypeId: test.specimenTypeId,
        turnaroundTimeHours: test.turnaroundTimeHours,
        price: test.price,
        active: test.active,
        analytes: test.analytes.map((a) => ({
          analyteId: a.analyteId,
          analyteCode: a.analyteCode,
          analyteName: a.analyteName,
          defaultUnit: a.defaultUnit,
          displayOrder: a.displayOrder,
          reportable: a.reportable,
        })),
        techniques: test.techniques.map((t) => ({
          techniqueId: t.techniqueId,
          techniqueCode: t.techniqueCode,
          techniqueName: t.techniqueName,
        })),
        containers: test.containers.map((c) => ({
          collectionContainerId: c.collectionContainerId,
          containerCode: c.containerCode,
          containerName: c.containerName,
          required: c.required,
        })),
      })
    }
  }, [test, reset])

  // ── Mutations ───────────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: (values: FormValues) =>
      testsApi.create({
        code: values.code,
        name: values.name,
        specimenTypeId: values.specimenTypeId,
        turnaroundTimeHours: values.turnaroundTimeHours,
        price: values.price,
        analytes: values.analytes.map((a) => ({
          analyteId: a.analyteId,
          displayOrder: a.displayOrder,
          reportable: a.reportable,
        })),
        techniqueIds: values.techniques.map((t) => t.techniqueId),
        containers: values.containers.map((c) => ({
          collectionContainerId: c.collectionContainerId,
          required: c.required,
        })),
      }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: TESTS_KEY })
      toast({ title: 'Prueba creada' })
      navigate(`/catalog/tests/${created.id}`, { replace: true })
    },
    onError: handleMutationError,
  })

  const updateMutation = useMutation({
    mutationFn: (values: FormValues) =>
      testsApi.update(id!, {
        code: values.code,
        name: values.name,
        specimenTypeId: values.specimenTypeId,
        turnaroundTimeHours: values.turnaroundTimeHours,
        price: values.price,
        active: values.active,
        analytes: values.analytes.map((a) => ({
          analyteId: a.analyteId,
          displayOrder: a.displayOrder,
          reportable: a.reportable,
        })),
        techniqueIds: values.techniques.map((t) => t.techniqueId),
        containers: values.containers.map((c) => ({
          collectionContainerId: c.collectionContainerId,
          required: c.required,
        })),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: TESTS_KEY })
      toast({ title: 'Prueba actualizada' })
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

  // Loading state (edit mode: wait for test data)
  if (isEdit && testLoading) {
    return (
      <div className="p-6 text-sm text-muted-foreground">Cargando prueba...</div>
    )
  }

  // ── Current IDs for duplicate prevention ────────────────────────────────

  const selectedAnalyteIds = analyteFields.map((f) => f.analyteId)
  const selectedTechniqueIds = techniqueFields.map((f) => f.techniqueId)
  const selectedContainerIds = containerFields.map((f) => f.collectionContainerId)

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="p-6 max-w-4xl space-y-6">

      {/* Header */}
      <div className="flex items-center gap-3">
        <Link
          to="/catalog/tests"
          className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Pruebas
        </Link>
        <span className="text-muted-foreground">/</span>
        <h1 className="text-xl font-semibold">
          {isEdit ? (test ? `${test.code} — ${test.name}` : '…') : 'Nueva prueba'}
        </h1>
        {isEdit && test && (
          <Badge variant={test.active ? 'default' : 'outline'} className="ml-auto">
            {test.active ? 'Activo' : 'Inactivo'}
          </Badge>
        )}
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>

        {/* ── General Information ────────────────────────────────────────── */}
        <Section title="Información general">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="code">Código</Label>
              <Input id="code" {...register('code')} placeholder="BHC" />
              {errors.code && <p className="text-xs text-destructive">{errors.code.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="name">Nombre</Label>
              <Input id="name" {...register('name')} placeholder="Biometría hemática completa" />
              {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="specimenTypeId">Tipo de muestra</Label>
              <Select
                value={specimenTypeId}
                onValueChange={(v) => setValue('specimenTypeId', v, { shouldValidate: true })}
                disabled={stLoading}
              >
                <SelectTrigger>
                  <SelectValue placeholder={stLoading ? 'Cargando...' : 'Seleccionar tipo de muestra'} />
                </SelectTrigger>
                <SelectContent>
                  {specimenTypes.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {errors.specimenTypeId && (
                <p className="text-xs text-destructive">{errors.specimenTypeId.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="turnaroundTimeHours">TAT (horas)</Label>
              <Input
                id="turnaroundTimeHours"
                type="number"
                min={1}
                {...register('turnaroundTimeHours')}
                placeholder="24"
              />
              {errors.turnaroundTimeHours && (
                <p className="text-xs text-destructive">{errors.turnaroundTimeHours.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="price">Precio</Label>
              <Input
                id="price"
                type="number"
                min={0}
                step="0.01"
                {...register('price')}
                placeholder="0.00"
              />
              {errors.price && <p className="text-xs text-destructive">{errors.price.message}</p>}
            </div>
          </div>

          {isEdit && (
            <div className="flex items-center gap-2 pt-2">
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
        </Section>

        {/* ── Analytes ───────────────────────────────────────────────────── */}
        <Section title="Analitos" error={errors.analytes?.root?.message ?? (errors.analytes as { message?: string } | undefined)?.message}>
          <div className="rounded-md border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Código</th>
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Nombre</th>
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Unidad</th>
                  <th className="px-3 py-2 text-center font-medium text-muted-foreground w-20">Orden</th>
                  <th className="px-3 py-2 text-center font-medium text-muted-foreground">Reportable</th>
                  <th className="px-3 py-2 w-10" />
                </tr>
              </thead>
              <tbody>
                {analyteFields.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-3 py-6 text-center text-muted-foreground text-xs">
                      Sin analitos. Agrega al menos uno.
                    </td>
                  </tr>
                )}
                {analyteFields.map((field, index) => (
                  <tr key={field.id} className="border-b last:border-0">
                    <td className="px-3 py-2 font-mono text-xs">{field.analyteCode}</td>
                    <td className="px-3 py-2">{field.analyteName}</td>
                    <td className="px-3 py-2 text-muted-foreground text-xs">
                      {field.defaultUnit ?? '—'}
                    </td>
                    <td className="px-3 py-2">
                      <Input
                        type="number"
                        min={1}
                        className="h-7 w-16 text-center text-xs"
                        {...register(`analytes.${index}.displayOrder`, { valueAsNumber: true })}
                      />
                    </td>
                    <td className="px-3 py-2 text-center">
                      <input
                        type="checkbox"
                        className="h-4 w-4 rounded border-input"
                        {...register(`analytes.${index}.reportable`)}
                      />
                    </td>
                    <td className="px-3 py-2">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                        onClick={() => removeAnalyte(index)}
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

          <AnalyteSelector
            excludeIds={selectedAnalyteIds}
            nextOrder={analyteFields.length + 1}
            onAdd={(row) => appendAnalyte(row)}
          />
        </Section>

        {/* ── Techniques ─────────────────────────────────────────────────── */}
        <Section title="Técnicas">
          <div className="rounded-md border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Código</th>
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Nombre</th>
                  <th className="px-3 py-2 w-10" />
                </tr>
              </thead>
              <tbody>
                {techniqueFields.length === 0 && (
                  <tr>
                    <td colSpan={3} className="px-3 py-6 text-center text-muted-foreground text-xs">
                      Sin técnicas asignadas.
                    </td>
                  </tr>
                )}
                {techniqueFields.map((field, index) => (
                  <tr key={field.id} className="border-b last:border-0">
                    <td className="px-3 py-2 font-mono text-xs">{field.techniqueCode}</td>
                    <td className="px-3 py-2">{field.techniqueName}</td>
                    <td className="px-3 py-2">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                        onClick={() => removeTechnique(index)}
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

          <TechniqueSelector
            excludeIds={selectedTechniqueIds}
            onAdd={(row) => appendTechnique(row)}
          />
        </Section>

        {/* ── Collection Containers ──────────────────────────────────────── */}
        <Section title="Contenedores de recolección" error={(errors.containers as { message?: string } | undefined)?.message}>
          <div className="rounded-md border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Código</th>
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Nombre</th>
                  <th className="px-3 py-2 text-center font-medium text-muted-foreground">Requerido</th>
                  <th className="px-3 py-2 w-10" />
                </tr>
              </thead>
              <tbody>
                {containerFields.length === 0 && (
                  <tr>
                    <td colSpan={4} className="px-3 py-6 text-center text-muted-foreground text-xs">
                      Sin contenedores. Agrega al menos uno.
                    </td>
                  </tr>
                )}
                {containerFields.map((field, index) => (
                  <tr key={field.id} className="border-b last:border-0">
                    <td className="px-3 py-2 font-mono text-xs">{field.containerCode}</td>
                    <td className="px-3 py-2">{field.containerName}</td>
                    <td className="px-3 py-2 text-center">
                      <input
                        type="checkbox"
                        className="h-4 w-4 rounded border-input"
                        {...register(`containers.${index}.required`)}
                      />
                    </td>
                    <td className="px-3 py-2">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                        onClick={() => removeContainer(index)}
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

          <CollectionContainerSelector
            excludeIds={selectedContainerIds}
            onAdd={(row) => appendContainer(row)}
          />
        </Section>

        {/* ── Actions ────────────────────────────────────────────────────── */}
        <div className="flex justify-end gap-2 pt-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => navigate('/catalog/tests')}
            disabled={isPending}
          >
            Cancelar
          </Button>
          <Button type="submit" disabled={isPending}>
            {isPending ? 'Guardando...' : isEdit ? 'Guardar cambios' : 'Crear prueba'}
          </Button>
        </div>
      </form>
    </div>
  )
}

// ── Section layout helper ─────────────────────────────────────────────────────

function Section({
  title,
  error,
  children,
}: {
  title: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="rounded-md border">
      <div className="px-4 py-3 border-b bg-muted/50 flex items-center justify-between">
        <h3 className="text-sm font-semibold">{title}</h3>
        {error && <p className="text-xs text-destructive">{error}</p>}
      </div>
      <div className="p-4 space-y-4">{children}</div>
    </div>
  )
}
