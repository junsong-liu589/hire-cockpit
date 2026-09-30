import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'

const base = process.env.PWA_BASE_PATH || '/'
const offlineWorker = {
  name: 'hire-cockpit-offline-worker',
  apply: 'build' as const,
  generateBundle(this: { emitFile: (asset: { type: 'asset'; fileName: string; source: string }) => void }, _options: unknown, bundle: Record<string, { type: string; fileName: string }>) {
    const urls = [base, `${base}manifest.webmanifest`, `${base}icons/icon.svg`, `${base}icons/icon-192.png`, `${base}icons/icon-512.png`, ...Object.values(bundle).filter(item => item.type === 'asset' || item.type === 'chunk').map(item => `${base}${item.fileName}`).filter(url => !url.endsWith('/sw.js'))]
    const version = Object.keys(bundle).sort().join('|')
    const template = readFileSync(new URL('./src/sw-template.js', import.meta.url), 'utf8')
    const source = template.replace('__CACHE_NAME__', `hire-cockpit-shell-${version}`).replace('__PRECACHE_URLS__', JSON.stringify([...new Set(urls)])).replace('__APP_ENTRY__', base)
    this.emitFile({ type: 'asset', fileName: 'sw.js', source })
  }
}

export default defineConfig({ base, plugins: [vue(), offlineWorker], server: { port: 5173 }, test: { environment: 'jsdom', include: ['src/**/*.test.ts'] } })
