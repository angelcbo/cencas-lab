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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { toast } from '@/hooks/use-toast'
import { normalizeApiError } from '@/api/client'
import { analytesApi, ANALYTES_KEY, type Analyte } from './analytesApi'

const analyteSchema = z.object({
  code: z.string().min(1, 'Requerido').max(50, 'Máximo 50 caracteres'),
  name: z.string().min(1, 'Requerido').max(255, 'Máximo 255 caracteres'),
  defaultUnit: z.string().max(50).optional(),
  resultType: z.enum(['NUMERIC', 'TEXT', 'QUALITATIVE'], {
    required_error: 'Requerido',
  }),
  active: z.boolean(),
})

type FormValues = z.infer<typeof analyteSchema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialValues?: Analyte
}

const RESULT_TYPE_LABELS: Record<string, string> = {
  NUMERIC: 'Numérico',
  TEXT: 'Texto',
  QUALITATIVE: 'Cualitativo',
}

export function AnalyteFormDialog({ open, onOpenChange, initialValues }: Props) {
  const isEdit = Boolean(initialValues)
  const queryClient = useQueryClient()

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(analyteSchema),
    defaultValues: { active: true, resultType: 'NUMERIC' },
  })

  const resultType = watch('resultType')

  useEffect(() => {
    if (open) {
      reset(
        initialValues
          ? {
              code: initialValues.code,
              name: initialValues.name,
              defaultUnit: initialValues.defaultUnit ?? '',
              resultType: initialValues.resultType,
              active: initialValues.active,
            }
          : { code: '', name: '', defaultUnit: '', resultType: 'NUMERIC', active: true },
      )
    }
  }, [open, initialValues, reset])

  const createMutation = useMutation({
    mutationFn: analytesApi.create,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ANALYTES_KEY })
      toast({ title: 'Analito creado' })
      onOpenChange(false)
    },
    onError: handleMutationError,
  })

  const updateMutation = useMutation({
    mutationFn: (data: FormValues) =>
      analytesApi.update(initialValues!.id, {
        code: data.code,
        name: data.name,
        defaultUnit: data.defaultUnit || undefined,
        resultType: data.resultType,
        active: data.active,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ANALYTES_KEY })
      toast({ title: 'Analito actualizado' })
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
        defaultUnit: values.defaultUnit || undefined,
        resultType: values.resultType,
      })
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending || isSubmitting

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Editar analito' : 'Nuevo analito'}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="code">Código</Label>
            <Input id="code" {...register('code')} placeholder="GLU" />
            {errors.code && <p className="text-xs text-destructive">{errors.code.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="name">Nombre</Label>
            <Input id="name" {...register('name')} placeholder="Glucosa" />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="resultType">Tipo de resultado</Label>
            <Select
              value={resultType}
              onValueChange={(v) => setValue('resultType', v as FormValues['resultType'])}
            >
              <SelectTrigger>
                <SelectValue placeholder="Seleccionar tipo" />
              </SelectTrigger>
              <SelectContent>
                {(['NUMERIC', 'TEXT', 'QUALITATIVE'] as const).map((rt) => (
                  <SelectItem key={rt} value={rt}>
                    {RESULT_TYPE_LABELS[rt]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.resultType && (
              <p className="text-xs text-destructive">{errors.resultType.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="defaultUnit">Unidad por defecto</Label>
            <Input id="defaultUnit" {...register('defaultUnit')} placeholder="mg/dL" />
            {errors.defaultUnit && (
              <p className="text-xs text-destructive">{errors.defaultUnit.message}</p>
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
              {isPending ? 'Guardando...' : isEdit ? 'Guardar cambios' : 'Crear analito'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
