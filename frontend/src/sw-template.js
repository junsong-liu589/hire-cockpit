/* Generated at build time with the complete static asset precache list. */
const CACHE = '__CACHE_NAME__'
const PRECACHE = __PRECACHE_URLS__
const APP_ENTRY = '__APP_ENTRY__'
const APP_ENTRY_URL = new URL(APP_ENTRY, self.location.origin).href
const SCOPE = new URL('./', self.location.href)

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(PRECACHE)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(key => key.startsWith('hire-cockpit-shell-') && key !== CACHE).map(key => caches.delete(key))))
    .then(() => self.clients.claim()))
})

self.addEventListener('fetch', event => {
  const request = event.request
  const url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin || !url.href.startsWith(SCOPE.href)) return

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then(response => response.ok ? response : caches.match(APP_ENTRY_URL)).catch(() => caches.match(APP_ENTRY_URL)))
    return
  }

  event.respondWith(caches.match(request).then(cached => cached || fetch(request).then(response => {
    if (response.ok) caches.open(CACHE).then(cache => cache.put(request, response.clone()))
    return response
  })))
})
