import axios from 'axios'

export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1', withCredentials: true })
function csrf() { return document.cookie.split('; ').find(x => x.startsWith('hc_csrf='))?.slice(8) }
api.interceptors.request.use(config => {
  if (config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
    const token = csrf()
    if (token) config.headers.set('X-CSRF-Token', decodeURIComponent(token))
  }
  return config
})
