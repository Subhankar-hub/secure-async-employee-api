export type TransactionStatus =
  | 'INITIALIZED'
  | 'ACCEPTED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'

export type Gender = 'MALE' | 'FEMALE' | 'OTHER'

export interface TokenResponse {
  access_token: string
  token_type: string
  expires_in: number
}

export interface TransactionResponse {
  transaction_id: string
  status: TransactionStatus
  message?: string
  error?: {
    code: string
    message: string
  }
}

export interface EmployeePayload {
  name: string
  dob: string
  gender: Gender
  pan: string
  mobile_number: string
}

export interface EmployeeResponse {
  transaction_id: string
  status: TransactionStatus
  employee: EmployeePayload
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  details?: Record<string, string>
}
