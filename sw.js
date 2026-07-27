const CACHE_NAME = 'jm-pos-v4';
const STATIC = [
  './',
  './index.html',
  './manifest.json',
  './factory-reset.html',
  './html5-qrcode.min.js',
  './logo.png',
  'https://fonts.googleapis.com/css2?family=Exo+2:wght@300;400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500;600;700&display=swap',
  'https://cdn.jsdelivr.net/npm/fuse.js@7.0.0/dist/fuse.min.js',
  'https://cdn.jsdelivr.net/npm/jsbarcode@3.11.5/dist/JsBarcode.all.min.js'
];

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      return cache.addAll(STATIC).catch(err => console.log('Some static assets failed to cache', err));
    })
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => {
      return Promise.all(
        keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
      );
    })
  );
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  const url = event.request.url;
  
  // Never intercept Firebase or Gemini API calls
  if (url.includes('firebasedatabase') || url.includes('googleapis.com/v1beta') || url.includes('generativelanguage')) return;
  
  // Stale-While-Revalidate for navigation (the app HTML) and local assets
  if (event.request.mode === 'navigate' || url.startsWith(self.location.origin)) {
    event.respondWith(
      caches.match(event.request).then(cachedResponse => {
        const fetchPromise = fetch(event.request).then(networkResponse => {
          if (networkResponse && networkResponse.status === 200) {
            const responseToCache = networkResponse.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, responseToCache));
          }
          return networkResponse;
        }).catch(err => {
          // Ignore network errors in background revalidation
          console.log('Background fetch failed for:', url, err);
        });
        
        return cachedResponse || fetchPromise;
      })
    );
    return;
  }
  
  // Cache-first for static CDN assets
  if (url.includes('cdn.jsdelivr.net') || url.includes('fonts.googleapis') || url.includes('fonts.gstatic') || url.includes('unpkg.com')) {
    event.respondWith(
      caches.match(event.request).then(cachedResponse => {
        if (cachedResponse) return cachedResponse;
        return fetch(event.request).then(networkResponse => {
          if (networkResponse && (networkResponse.status === 200 || networkResponse.status === 0)) {
            const responseToCache = networkResponse.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, responseToCache));
          }
          return networkResponse;
        }).catch(() => new Response('', {status: 503}));
      })
    );
    return;
  }
  
  // Generic fallback for any other assets
  event.respondWith(
    caches.match(event.request).then(cachedResponse => {
      if (cachedResponse) return cachedResponse;
      return fetch(event.request).then(networkResponse => {
        if (networkResponse && networkResponse.status === 200) {
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, responseToCache));
        }
        return networkResponse;
      }).catch(() => new Response('', {status: 503}));
    })
  );
});
