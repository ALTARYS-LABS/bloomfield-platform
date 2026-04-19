import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Configuration Vitest dédiée aux tests : on isole cette config de vite.config.ts
// pour ne pas charger le plugin Tailwind (inutile en test) ni le proxy dev-server.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
