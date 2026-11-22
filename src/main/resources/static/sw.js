// 설치 가능하게 하는 최소 서비스 워커. 화면 파일만 캐시하고 /api 는 항상 네트워크로.
// shortcut: 오프라인 입력은 안 됨, 필요해지면 IndexedDB 큐 + 동기화 추가
const CACHE = 'nlr-v3';
const SHELL = ['/', '/index.html', '/app.css', '/app.js', '/manifest.webmanifest', '/icon.svg'];

self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL))));

self.addEventListener('activate', e => e.waitUntil(
  caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
));

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || new URL(e.request.url).pathname.startsWith('/api/')) return;
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
});
