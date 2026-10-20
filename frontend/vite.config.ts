import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import type { Plugin } from 'vite'
import { createHash } from 'node:crypto'

// Cache only the public application shell. No authenticated/API response is cached.
function appShell(): Plugin {
  return {
    name: 'grocery-app-shell', apply: 'build',
    generateBundle(_, bundle) {
      const assets = ['/', '/index.html', '/manifest.webmanifest', '/favicon.svg', '/icon-192.png', '/icon-512.png', '/apple-touch-icon.png', ...Object.keys(bundle).filter(p => /\.(js|css|woff2?)$/.test(p)).map(p => `/${p}`)]
      const version = createHash('sha256').update(JSON.stringify(assets)).digest('hex').slice(0, 12)
      this.emitFile({ type: 'asset', fileName: 'sw.js', source: `
const CACHE='gip-shell-${version}';
const ASSETS=${JSON.stringify(assets)};
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('gip-shell-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',event=>{
 const request=event.request,url=new URL(request.url);
 if(request.method!=='GET'||url.origin!==self.location.origin||request.headers.has('Authorization')||url.pathname==='/api'||url.pathname.startsWith('/api/'))return;
 if(request.mode==='navigate'){event.respondWith(fetch(request).catch(()=>caches.open(CACHE).then(cache=>cache.match('/index.html', {ignoreVary:true}))));return;}
 if(ASSETS.includes(url.pathname))event.respondWith(caches.open(CACHE).then(cache=>cache.match(url.pathname,{ignoreVary:true})).then(cached=>cached||fetch(request)));
});
` })
    },
  }
}
const proxy = { '/api': { target: process.env.COUPON_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true, rewrite: (path: string) => path.replace(/^\/api/, '') } }
export default defineConfig({
  plugins: [react(), appShell()],
  server: { host: '127.0.0.1', proxy },
  preview: { host: '127.0.0.1', proxy },
  test: { environment: 'jsdom', setupFiles: './src/test-setup.ts', include: ['src/**/*.test.{ts,tsx}'], restoreMocks: true },
})
