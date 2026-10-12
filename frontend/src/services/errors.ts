import axios from 'axios'
import type { ApiErrorBody } from '../types/api'

/** Normalised API failure: HTTP status, human message and optional per-field messages. */
export class ApiError extends Error {
  readonly status: number | null
  readonly fieldErrors: Record<string, string>

  constructor(message: string, status: number | null, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    if (!error.response) {
      return new ApiError('Cannot reach the server. Check your connection and try again.', null)
    }
    const body = error.response.data
    return new ApiError(
      body?.message || `Request failed (${error.response.status})`,
      error.response.status,
      body?.fieldErrors ?? {},
    )
  }
  return new ApiError(error instanceof Error ? error.message : 'Something went wrong.', null)
}

export function errorMessage(error: unknown): string {
  return toApiError(error).message
}
