import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
      '/v3': 'http://localhost:8080',
      '/swagger-ui': 'http://localhost:8080',
    },
  },
  test: { include: ['src/**/*.test.ts'], environment: 'node' },
});
