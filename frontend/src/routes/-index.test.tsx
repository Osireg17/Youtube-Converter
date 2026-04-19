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

async function selectOutputFormat(
  user: ReturnType<typeof userEvent.setup>,
  formatLabel: "MP4 (Video)" | "MP3 (Audio)"
) {
  await user.click(screen.getByRole("combobox"))
  await user.click(await screen.findByRole("option", { name: formatLabel }))
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

  it("renders the done state with a direct download link and stops polling", async () => {
    const user = userEvent.setup()
    const downloadUrl = "https://example.com/file.mp4"
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "PROCESSING" } })
      )
      .mockImplementationOnce(() =>
        createFetchResponse({
          body: { jobId: JOB_ID, status: "DONE", downloadUrl },
        })
      )

    renderIndexPage()

    await submitJob(user)

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))

    await sleep(4100)
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3))

    await sleep(4500)

    expect(fetch).toHaveBeenCalledTimes(3)
    expect(screen.getByText("Your file is ready.")).toBeInTheDocument()
    expect(screen.getByText(/Download links expire after 1 hour/)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(JOB_ID))).toBeInTheDocument()

    const downloadLink = screen.getByRole("link", { name: "Download MP4" })
    expect(downloadLink).toHaveAttribute("href", downloadUrl)
    expect(downloadLink).toHaveAttribute("download")
  }, 12000)

  it("uses the submitted format in the done state download label", async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() =>
        createFetchResponse({
          body: { jobId: JOB_ID, status: "DONE", downloadUrl: "https://example.com/file.mp3" },
        })
      )

    renderIndexPage()

    await user.type(
      screen.getByPlaceholderText("https://www.youtube.com/watch?v=..."),
      "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    )
    await selectOutputFormat(user, "MP3 (Audio)")
    await user.click(screen.getByRole("button", { name: "Convert" }))

    expect(await screen.findByRole("link", { name: "Download MP3" })).toBeInTheDocument()
  })

  it("renders the failed state with retry and stops polling", async () => {
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
    expect(screen.getByText("Conversion failed.")).toBeInTheDocument()
    expect(screen.getByText(/Retry the job using the same URL and format/)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(JOB_ID))).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument()
  }, 12000)

  it("returns to the form and restores the previous values after retry", async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() =>
        createFetchResponse({ body: { jobId: JOB_ID, status: "FAILED" } })
      )

    renderIndexPage()

    const urlInput = screen.getByPlaceholderText("https://www.youtube.com/watch?v=...")
    await user.type(urlInput, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    await selectOutputFormat(user, "MP3 (Audio)")
    await user.click(screen.getByRole("button", { name: "Convert" }))

    await user.click(await screen.findByRole("button", { name: "Retry" }))

    expect(await screen.findByRole("button", { name: "Convert" })).toBeInTheDocument()
    expect(screen.getByDisplayValue("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).toBeInTheDocument()
    expect(screen.getByRole("combobox")).toHaveTextContent("MP3")
    expect(screen.queryByText("Conversion failed.")).not.toBeInTheDocument()
  })

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

  it("shows a retry path when DONE is missing a download URL", async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockImplementationOnce(() => createFetchResponse({ status: 201, body: { jobId: JOB_ID } }))
      .mockImplementationOnce(() => createFetchResponse({ body: { jobId: JOB_ID, status: "DONE" } }))

    renderIndexPage()

    await submitJob(user)

    expect(await screen.findByText("Download unavailable.")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument()
    expect(screen.queryByRole("link", { name: /Download MP4|Download MP3/ })).not.toBeInTheDocument()
  })

  it("does not start polling before a job is created", () => {
    renderIndexPage()

    expect(fetch).not.toHaveBeenCalled()
  })
})
