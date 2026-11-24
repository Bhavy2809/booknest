import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The React dev server runs on :5173 and forwards every /api/... call to Spring Boot on :8080.
// Because the browser only ever talks to :5173, there are no CORS problems during development.
export default defineConfig({
  plugins: [react()],
  server: {
     port: 5173,
     proxy: {
        '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
     },
  },
});
