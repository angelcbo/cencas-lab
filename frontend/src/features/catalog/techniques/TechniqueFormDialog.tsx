import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
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
import { toast } from '@/hooks/use-toast'
import { normalizeApiError } from '@/api/client'
import { techniquesApi, TECHNIQUES_KEY, type Technique } from './techniquesApi'

const techniqueSchema = z.object({
  code: z.string().min(1, 'Requerido').max(50, 'Máximo 50 caracteres'),
  name: z.string().min(1, 'Requerido').max(255, 'Máximo 255 caracteres'),
  description: z.string().max(500, 'Máximo 500 caracteres').optional(),
  active: z.boolean(),
})

type FormValues = z.infer<typeof techniqueSchema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialValues?: Technique
}

export function TechniqueFormDialog({ open, onOpenChange, initialValues }: Props) {
  const isEdit = Boolean(initialValues)
  const queryClient = useQueryClient()

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(techniqueSchema),
    defaultValues: { active: true },
  })

  useEffect(() => {
    if (open) {
      reset(
        initialValues
          ? {
              code: initialValues.code,
              name: initialValues.name,
              description: initialValues.description ?? '',
              active: initialValues.active,
            }
          : { code: '', name: '', description: '', active: true },
      )
    }
  }, [open, initialValues, reset])

  const createMutation = useMutation({
    mutationFn: techniquesApi.create,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: TECHNIQUES_KEY })
      toast({ title: 'Técnica creada' })
      onOpenChange(false)
    },
    onError: handleMutationError,
  })

  const updateMutation = useMutation({
    mutationFn: (data: FormValues) =>
      techniquesApi.update(initialValues!.id, {
        code: data.code,
        name: data.name,
        description: data.description || undefined,
        active: data.active,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: TECHNIQUES_KEY })
      toast({ title: 'Técnica actualizada' })
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
        description: values.description || undefined,
      })
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending || isSubmitting

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Editar técnica' : 'Nueva técnica'}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="code">Código</Label>
            <Input id="code" {...register('code')} placeholder="ESPECT" />
            {errors.code && <p className="text-xs text-destructive">{errors.code.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="name">Nombre</Label>
            <Input id="name" {...register('name')} placeholder="Espectrofotometría" />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
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
              {isPending ? 'Guardando...' : isEdit ? 'Guardar cambios' : 'Crear técnica'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
