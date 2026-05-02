import { vi } from "vitest"

type MockResponseInit = {
  ok?: boolean
  status?: number
  body?: unknown
}

export function createFetchResponse({ ok = true, status = 200, body }: MockResponseInit = {}) {
  return Promise.resolve({
    ok,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response)
}
