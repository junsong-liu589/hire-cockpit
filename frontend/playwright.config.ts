import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  reporter: 'list',
  use: { baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:4173', browserName: 'chromium' },
  webServer: { command: 'npm run build && ./node_modules/.bin/vite preview --host 127.0.0.1', url: 'http://127.0.0.1:4173', reuseExistingServer: !process.env.CI, timeout: 60_000 },
  timeout: 30_000,
  expect: { timeout: 7_000 }
})
