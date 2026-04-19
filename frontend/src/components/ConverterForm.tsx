import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation } from "@tanstack/react-query"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

const youtubeUrlPattern = /^https?:\/\/(www\.)?(youtube\.com\/watch\?v=|youtu\.be\/).+$/

const formSchema = z.object({
  youtubeUrl: z
    .string()
    .min(1, "URL is required")
    .regex(youtubeUrlPattern, "Please enter a valid YouTube URL"),
  outputFormat: z.enum(["MP4", "MP3"]),
})

export type FormValues = z.infer<typeof formSchema>

interface ConverterFormProps {
  initialValues: FormValues
  onSuccess: (result: { jobId: string; request: FormValues }) => void
}

export function ConverterForm({ initialValues, onSuccess }: ConverterFormProps) {
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: initialValues,
  })

  const outputFormat = watch("outputFormat")

  useEffect(() => {
    reset(initialValues)
  }, [initialValues, reset])

  const mutation = useMutation({
    mutationFn: async (data: FormValues) => {
      const response = await fetch("/api/jobs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      })
      if (!response.ok) throw new Error("Failed to create job")
      const json = await response.json()
      return {
        jobId: json.jobId as string,
        request: data,
      }
    },
    onSuccess,
  })

  const onSubmit = (data: FormValues) => mutation.mutate(data)

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4 w-full max-w-md">
      <div className="flex flex-col gap-1">
        <Input
          {...register("youtubeUrl")}
          placeholder="https://www.youtube.com/watch?v=..."
          disabled={mutation.isPending}
          aria-invalid={!!errors.youtubeUrl}
        />
        {errors.youtubeUrl && (
          <p className="text-sm text-destructive">{errors.youtubeUrl.message}</p>
        )}
      </div>

      <Select
        value={outputFormat}
        disabled={mutation.isPending}
        onValueChange={(value) =>
          setValue("outputFormat", value as "MP4" | "MP3", {
            shouldDirty: true,
            shouldValidate: true,
          })
        }
      >
        <SelectTrigger className="w-full">
          <SelectValue placeholder="Select format" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="MP4">MP4 (Video)</SelectItem>
          <SelectItem value="MP3">MP3 (Audio)</SelectItem>
        </SelectContent>
      </Select>

      <Button type="submit" disabled={mutation.isPending} className="w-full">
        {mutation.isPending ? "Converting..." : "Convert"}
      </Button>
    </form>
  )
}
