import type {
  ApiError,
  EmployeePayload,
  EmployeeResponse,
  TokenResponse,
  TransactionResponse,
} from './types'

const API = '/api/v1'

export class ApiClientError extends Error {
  readonly status: number
  readonly code?: string
  readonly details?: Record<string, string>
  readonly body: unknown

  constructor(status: number, body: unknown) {
    const apiError = body as ApiError
    super(apiError?.message ?? `Request failed (${status})`)
    this.name = 'ApiClientError'
    this.status = status
    this.code = apiError?.code
    this.details = apiError?.details
    this.body = body
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API}${path}`, { ...options, headers })
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiClientError(response.status, body)
  }
  return body as T
}

export function fetchToken(clientId: string, clientSecret: string) {
  return request<TokenResponse>('/auth/token', {
    method: 'POST',
    body: JSON.stringify({ client_id: clientId, client_secret: clientSecret }),
  })
}

export function initTransaction(token: string) {
  return request<TransactionResponse>('/transactions/init', { method: 'POST' }, token)
}

export function submitEmployee(token: string, transactionId: string, employee: EmployeePayload) {
  return request<TransactionResponse>(
    `/transactions/${transactionId}/employees`,
    { method: 'POST', body: JSON.stringify(employee) },
    token,
  )
}

export function getStatus(token: string, transactionId: string) {
  return request<TransactionResponse>(`/transactions/${transactionId}`, {}, token)
}

export function getEmployee(token: string, transactionId: string) {
  return request<EmployeeResponse>(`/transactions/${transactionId}/employees`, {}, token)
}

export async function pollStatus(
  token: string,
  transactionId: string,
  intervalMs = 500,
  timeoutMs = 15_000,
): Promise<TransactionResponse> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const status = await getStatus(token, transactionId)
    if (status.status === 'COMPLETED' || status.status === 'FAILED') {
      return status
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
  throw new Error('Timed out waiting for the transaction to finish')
}
