const CACHE_NAME='toolverse-shell-v2';
const SHELL=['./','./index.html','./assets/styles.css','./assets/app.js','./assets/tools.js','./assets/icon.svg','./privacy.html','./manifest.webmanifest'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE_NAME).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE_NAME).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',e=>{const request=e.request,url=new URL(request.url);if(request.method!=='GET'||url.origin!==self.location.origin)return;
e.respondWith(fetch(request).then(response=>{if(response.ok){const cached=response.clone();caches.open(CACHE_NAME).then(c=>c.put(request,cached));}return response;}).catch(()=>caches.match(request).then(hit=>hit||(request.mode==='navigate'?caches.match('./index.html'):Response.error()))));});