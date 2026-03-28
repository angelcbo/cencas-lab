import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
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
import { toast } from '@/hooks/use-toast'
import { normalizeApiError } from '@/api/client'
import { specimenTypesApi, SPECIMEN_TYPES_KEY } from '../specimen-types/specimenTypesApi'
import {
  collectionContainersApi,
  COLLECTION_CONTAINERS_KEY,
  type CollectionContainer,
} from './collectionContainersApi'

const containerSchema = z.object({
  code: z.string().min(1, 'Requerido').max(50, 'Máximo 50 caracteres'),
  name: z.string().min(1, 'Requerido').max(255, 'Máximo 255 caracteres'),
  color: z.string().max(50, 'Máximo 50 caracteres').optional(),
  specimenTypeId: z.string().min(1, 'Requerido'),
  description: z.string().max(500, 'Máximo 500 caracteres').optional(),
  active: z.boolean(),
})

type FormValues = z.infer<typeof containerSchema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialValues?: CollectionContainer
}

export function CollectionContainerFormDialog({ open, onOpenChange, initialValues }: Props) {
  const isEdit = Boolean(initialValues)
  const queryClient = useQueryClient()

  const { data: specimenTypesPage, isLoading: specimenTypesLoading } = useQuery({
    queryKey: [...SPECIMEN_TYPES_KEY, 0, 100],
    queryFn: () => specimenTypesApi.list(0, 100),
    enabled: open,
  })

  const specimenTypes = specimenTypesPage?.content ?? []

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(containerSchema),
    defaultValues: { active: true, specimenTypeId: '' },
  })

  const specimenTypeId = watch('specimenTypeId')

  useEffect(() => {
    if (open) {
      reset(
        initialValues
          ? {
              code: initialValues.code,
              name: initialValues.name,
              color: initialValues.color ?? '',
              specimenTypeId: initialValues.specimenTypeId,
              description: initialValues.description ?? '',
              active: initialValues.active,
            }
          : { code: '', name: '', color: '', specimenTypeId: '', description: '', active: true },
      )
    }
  }, [open, initialValues, reset])

  const createMutation = useMutation({
    mutationFn: collectionContainersApi.create,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: COLLECTION_CONTAINERS_KEY })
      toast({ title: 'Contenedor creado' })
      onOpenChange(false)
    },
    onError: handleMutationError,
  })

  const updateMutation = useMutation({
    mutationFn: (data: FormValues) =>
      collectionContainersApi.update(initialValues!.id, {
        code: data.code,
        name: data.name,
        color: data.color || undefined,
        specimenTypeId: data.specimenTypeId,
        description: data.description || undefined,
        active: data.active,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: COLLECTION_CONTAINERS_KEY })
      toast({ title: 'Contenedor actualizado' })
      onOpenChange(false)
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
      createMutation.mutate({
        code: values.code,
        name: values.name,
        color: values.color || undefined,
        specimenTypeId: values.specimenTypeId,
        description: values.description || undefined,
      })
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending || isSubmitting

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Editar contenedor' : 'Nuevo contenedor'}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="code">Código</Label>
            <Input id="code" {...register('code')} placeholder="TUBO-LILA" />
            {errors.code && <p className="text-xs text-destructive">{errors.code.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="name">Nombre</Label>
            <Input id="name" {...register('name')} placeholder="Tubo tapa lila (EDTA)" />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="specimenTypeId">Tipo de muestra</Label>
            <Select
              value={specimenTypeId}
              onValueChange={(v) => setValue('specimenTypeId', v, { shouldValidate: true })}
              disabled={specimenTypesLoading}
            >
              <SelectTrigger>
                <SelectValue placeholder={specimenTypesLoading ? 'Cargando...' : 'Seleccionar tipo de muestra'} />
              </SelectTrigger>
              <SelectContent>
                {specimenTypes.map((st) => (
                  <SelectItem key={st.id} value={st.id}>
                    {st.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.specimenTypeId && (
              <p className="text-xs text-destructive">{errors.specimenTypeId.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="color">Color</Label>
            <Input id="color" {...register('color')} placeholder="Lila" />
            {errors.color && <p className="text-xs text-destructive">{errors.color.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="description">Descripción</Label>
            <Input id="description" {...register('description')} placeholder="Descripción opcional" />
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

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? 'Guardando...' : isEdit ? 'Guardar cambios' : 'Crear contenedor'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
