import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { createFileRoute } from "@tanstack/react-router"
import { ConverterForm } from "@/components/ConverterForm"

export const Route = createFileRoute("/")({
  component: IndexPage,
})

type JobStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED"

type JobStatusResponse = {
  jobId: string
  status: JobStatus
  downloadUrl?: string
}

function isTerminalStatus(status: JobStatus) {
  return status === "DONE" || status === "FAILED"
}

export function IndexPage() {
  const [jobId, setJobId] = useState<string | null>(null)
  const [statusLabel, setStatusLabel] = useState("Job queued")

  const statusQuery = useQuery({
    queryKey: ["job-status", jobId],
    enabled: jobId !== null,
    retry: false,
    queryFn: async (): Promise<JobStatusResponse> => {
      const response = await fetch(`/api/jobs/${jobId}/status`)

      if (!response.ok) {
        throw new Error("Failed to fetch job status")
      }

      return response.json() as Promise<JobStatusResponse>
    },
    refetchInterval: (query) => {
      const status = query.state.data?.status

      if (status && isTerminalStatus(status)) {
        return false
      }

      return 4000
    },
  })

  useEffect(() => {
    if (jobId === null) {
      setStatusLabel("Job queued")
      return
    }

    if (statusQuery.data?.status === "PENDING") {
      setStatusLabel("Job queued")
    }

    if (statusQuery.data?.status === "PROCESSING") {
      setStatusLabel("Processing")
    }
  }, [jobId, statusQuery.data?.status])

  return (
    <main className="relative flex min-h-svh flex-col items-center justify-center overflow-hidden px-4">
      {/* Noise texture overlay */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.03]"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E")`,
          backgroundRepeat: "repeat",
          backgroundSize: "128px 128px",
        }}
      />

      {/* Subtle red glow behind the card */}
      <div className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[500px] w-[500px] rounded-full bg-primary/10 blur-[120px]" />

      <div className="relative z-10 flex w-full max-w-lg flex-col gap-10">
        {/* Header */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2.5">
            <span className="inline-block h-2.5 w-2.5 rounded-full bg-primary animate-pulse" />
            <span className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
              YouTube Converter
            </span>
          </div>
          <h1
            className="text-5xl leading-[1.1] text-foreground"
            style={{ fontFamily: "'Instrument Serif', serif" }}
          >
            Convert any video,{" "}
            <span className="italic text-primary">instantly.</span>
          </h1>
          <p className="text-sm text-muted-foreground leading-relaxed">
            Paste a YouTube link, pick your format, and download in seconds.
          </p>
        </div>

        {/* Card */}
        <div className="rounded-xl border border-border bg-card p-6 shadow-2xl">
          {jobId === null ? (
            <ConverterForm onSuccess={setJobId} />
          ) : (
            <div className="flex flex-col items-center gap-3 py-4 text-center">
              <span className="inline-block h-2.5 w-2.5 rounded-full bg-primary animate-pulse" />
              <p className="text-sm text-muted-foreground">
                {statusLabel} — <span className="font-mono text-foreground">{jobId}</span>
              </p>
              {statusQuery.isError && (
                <p className="text-sm text-destructive">Unable to refresh job status.</p>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <p className="text-center text-xs text-muted-foreground/50">
          MP4 · MP3 &nbsp;·&nbsp; No account required
        </p>
      </div>
    </main>
  )
}
