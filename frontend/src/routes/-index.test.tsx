import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { IndexPage } from "./index"

type MockResponseInit = {
  ok?: boolean
  status?: number
  body?: unknown
}

const JOB_ID = "123e4567-e89b-12d3-a456-426614174000"

function createFetchResponse({ ok = true, status = 200, body }: MockResponseInit = {}) {
  return Promise.resolve({
    ok,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response)
}

function renderIndexPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <IndexPage />
    </QueryClientProvider>
  )
}

async function sleep(ms: number) {
  await act(async () => {
    await new Promise((resolve) => window.setTimeout(resolve, ms))
  })
}

async function submitJob(user = userEvent.setup()) {
  await user.type(
    screen.getByPlaceholderText("https://www.youtube.com/watch?v=..."),
    "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
  )
  await user.click(screen.getByRole("button", { name: "Convert" }))
}

describe("IndexPage", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it("renders the form before a job exists", () => {
    renderIndexPage()

    expect(screen.getByPlaceholderText("https://www.youtube.com/watch?v=...")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Convert" })).toBeInTheDocument()
  })

  it("shows the queued card with the created job id after submission", async () => {
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "PENDING" } })
      )

    renderIndexPage()

    await submitJob()

    expect(await screen.findByText(new RegExp(JOB_ID))).toBeInTheDocument()
    expect(screen.getByText(/Job queued/)).toBeInTheDocument()
  })

  it("polls every 4 seconds and switches to processing when the status updates", async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "PENDING" } })
      )
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "PROCESSING" } })
      )

    renderIndexPage()

    await submitJob(user)

    await waitFor(() => {
      expect(fetch).toHaveBeenNthCalledWith(
        2,
        `/api/jobs/${JOB_ID}/status`
      )
    })

    await sleep(4100)

    await waitFor(() => {
      expect(fetch).toHaveBeenNthCalledWith(
        3,
        `/api/jobs/${JOB_ID}/status`
      )
    })

    expect(await screen.findByText(/Processing/)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(JOB_ID))).toBeInTheDocument()
  }, 10000)

  it("stops polling when the job reaches DONE", async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "PROCESSING" } })
      )
      .mockImplementationOnce(() =>
        createFetchResponse({
          body: { jobId: JOB_ID, status: "DONE", downloadUrl: "https://example.com/file.mp4" },
        })
      )

    renderIndexPage()

    await submitJob(user)

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))

    await sleep(4100)
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3))

    await sleep(4500)

    expect(fetch).toHaveBeenCalledTimes(3)
    expect(screen.getByText(/Processing/)).toBeInTheDocument()
  }, 12000)

  it("stops polling when the job reaches FAILED", async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "PROCESSING" } })
      )
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "FAILED" } })
      )

    renderIndexPage()

    await submitJob(user)

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))

    await sleep(4100)
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3))

    await sleep(4500)

    expect(fetch).toHaveBeenCalledTimes(3)
    expect(screen.getByText(/Processing/)).toBeInTheDocument()
  }, 12000)

  it("shows an inline error when status polling fails and keeps the job card mounted", async () => {
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() => createFetchResponse({ ok: false, status: 404 }))

    renderIndexPage()

    await submitJob()

    expect(await screen.findByText(/Unable to refresh job status\./)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(JOB_ID))).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Convert" })).not.toBeInTheDocument()
  })

  it("does not start polling before a job is created", () => {
    renderIndexPage()

    expect(fetch).not.toHaveBeenCalled()
  })
})
