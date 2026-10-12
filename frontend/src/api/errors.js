/** Pulls the backend's ApiError message out of an axios error, falling back to a generic one. */
export function errorMessage(error, fallback = 'Something went wrong.') {
  return error?.response?.data?.message || fallback
}
