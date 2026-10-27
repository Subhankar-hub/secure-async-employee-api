import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.DEV_API_PROXY || 'http://localhost:8080'
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': proxyTarget,
      },
    },
  }
})
