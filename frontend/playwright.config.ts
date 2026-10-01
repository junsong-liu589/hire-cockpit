import { defineConfig } from '@playwright/test'

const e2ePort = Number(process.env.E2E_PORT || 4174)

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL || `http://127.0.0.1:${e2ePort}`,
    browserName: 'chromium',
    launchOptions: process.env.PLAYWRIGHT_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH } : undefined,
  },
  webServer: { command: `npm run build && node ./node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port ${e2ePort} --strictPort`, url: `http://127.0.0.1:${e2ePort}`, reuseExistingServer: false, timeout: 60_000 },
  timeout: 30_000,
  expect: { timeout: 7_000 }
})
