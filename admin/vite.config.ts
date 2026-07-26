import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Cong 5173 phai khop CORS_ORIGINS trong server/.env
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
});
