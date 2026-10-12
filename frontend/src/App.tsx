import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { ToastProvider } from './components/Toast'
import { router } from './router/router'
import { toApiError } from './services/errors'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // retrying a 4xx never helps; one retry covers transient network/5xx blips
      retry: (failureCount, error) => {
        const status = toApiError(error).status
        return failureCount < 1 && (status === null || status >= 500)
      },
      refetchOnWindowFocus: false,
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>
  )
}
