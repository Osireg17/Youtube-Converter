const DEFAULT_API_PREFIX = "/api"

function normalizeBaseUrl(baseUrl: string) {
  return baseUrl.replace(/\/+$/, "")
}

function getConfiguredApiBaseUrl() {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
  const legacyJobServiceUrl = import.meta.env.VITE_JOB_SERVICE_URL?.trim()
  const configuredBaseUrl = apiBaseUrl || legacyJobServiceUrl

  if (!configuredBaseUrl) {
    return DEFAULT_API_PREFIX
  }

  return `${normalizeBaseUrl(configuredBaseUrl)}${DEFAULT_API_PREFIX}`
}

export function buildApiUrl(path: string) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`
  return `${getConfiguredApiBaseUrl()}${normalizedPath}`
}
