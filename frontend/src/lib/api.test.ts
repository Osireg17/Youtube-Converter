import { afterEach, describe, expect, it, vi } from "vitest"

import { buildApiUrl } from "./api"

describe("buildApiUrl", () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it("falls back to relative api paths when no frontend api env is set", () => {
    expect(buildApiUrl("/jobs")).toBe("/api/jobs")
  })

  it("uses VITE_API_BASE_URL when configured", () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://job-production-ce60.up.railway.app")

    expect(buildApiUrl("/jobs")).toBe("https://job-production-ce60.up.railway.app/api/jobs")
  })

  it("trims a trailing slash from VITE_API_BASE_URL", () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://job-production-ce60.up.railway.app/")

    expect(buildApiUrl("/jobs")).toBe("https://job-production-ce60.up.railway.app/api/jobs")
  })

  it("supports the legacy VITE_JOB_SERVICE_URL fallback", () => {
    vi.stubEnv("VITE_JOB_SERVICE_URL", "https://job-production-ce60.up.railway.app")

    expect(buildApiUrl("/jobs")).toBe("https://job-production-ce60.up.railway.app/api/jobs")
  })
})
