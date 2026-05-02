import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { ConverterForm, type FormValues } from "./ConverterForm"
import { createFetchResponse } from "@/test/utils"

const JOB_ID = "123e4567-e89b-12d3-a456-426614174000"
const VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

function renderConverterForm(overrides?: Partial<{ onSuccess: (result: { jobId: string; request: FormValues }) => void }>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  const onSuccess = vi.fn()

  render(
    <QueryClientProvider client={queryClient}>
      <ConverterForm
        initialValues={{ youtubeUrl: "", outputFormat: "MP4" }}
        onSuccess={overrides?.onSuccess ?? onSuccess}
      />
    </QueryClientProvider>
  )

  return { onSuccess }
}

describe("ConverterForm", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it("renders the form in idle state", () => {
    renderConverterForm()

    expect(screen.getByRole("button", { name: "Convert" })).toBeInTheDocument()
    expect(screen.getByPlaceholderText("https://www.youtube.com/watch?v=...")).not.toBeDisabled()
    expect(screen.getByRole("combobox")).not.toBeDisabled()
  })

  it("disables inputs and shows 'Converting...' while pending", async () => {
    vi.mocked(fetch).mockImplementation(() => new Promise(() => {}))
    renderConverterForm()

    await userEvent.type(screen.getByPlaceholderText("https://www.youtube.com/watch?v=..."), VALID_URL)
    await userEvent.click(screen.getByRole("button", { name: "Convert" }))

    expect(screen.getByRole("button", { name: "Converting..." })).toBeInTheDocument()
    expect(screen.getByPlaceholderText("https://www.youtube.com/watch?v=...")).toBeDisabled()
    expect(screen.getByRole("combobox")).toBeDisabled()
  })

  it("calls POST /api/jobs with the correct method, headers, and body", async () => {
    vi.mocked(fetch).mockImplementationOnce(() =>
      createFetchResponse({ status: 201, body: { jobId: JOB_ID } })
    )

    renderConverterForm()

    await userEvent.type(screen.getByPlaceholderText("https://www.youtube.com/watch?v=..."), VALID_URL)
    await userEvent.click(screen.getByRole("button", { name: "Convert" }))

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/jobs"),
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
        body: JSON.stringify({ youtubeUrl: VALID_URL, outputFormat: "MP4" }),
      })
    )
  })

  it("calls onSuccess with jobId and request after a successful POST", async () => {
    vi.mocked(fetch).mockImplementationOnce(() =>
      createFetchResponse({ status: 201, body: { jobId: JOB_ID } })
    )

    const { onSuccess } = renderConverterForm()

    await userEvent.type(screen.getByPlaceholderText("https://www.youtube.com/watch?v=..."), VALID_URL)
    await userEvent.click(screen.getByRole("button", { name: "Convert" }))

    expect(onSuccess).toHaveBeenCalledTimes(1)
    expect(onSuccess.mock.calls[0][0]).toEqual({
      jobId: JOB_ID,
      request: { youtubeUrl: VALID_URL, outputFormat: "MP4" },
    })
  })

  it("shows a validation error for an empty URL", async () => {
    renderConverterForm()

    await userEvent.click(screen.getByRole("button", { name: "Convert" }))

    expect(screen.getByText("URL is required")).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })

  it("shows a validation error for a non-YouTube URL", async () => {
    renderConverterForm()

    await userEvent.type(screen.getByPlaceholderText("https://www.youtube.com/watch?v=..."), "https://google.com")
    await userEvent.click(screen.getByRole("button", { name: "Convert" }))

    expect(screen.getByText("Please enter a valid YouTube URL")).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })
})
