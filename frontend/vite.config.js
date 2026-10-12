import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: true,
    port: 5173,
    // Mirrors the /api/ proxy in nginx.conf so the default '/api' baseURL
    // works in dev without needing VITE_API_URL.
    proxy: {
      '/api': process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
    },
  },
})
