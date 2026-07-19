const CACHE_NOME = "biblia-digital-qq-v1";
const ARQUIVOS_APP = [
  "./index.html",
  "./manifest.json",
  "./Biblia_data.js",
  "./icon-192.png",
  "./icon-512.png"
];

self.addEventListener("install", function (evento) {
  evento.waitUntil(
    caches.open(CACHE_NOME).then(function (cache) {
      // addAll falha se QUALQUER arquivo faltar — adiciona um a um pra não travar o install
      // inteiro por causa de um ícone ausente, por exemplo.
      return Promise.all(
        ARQUIVOS_APP.map(function (url) {
          return cache.add(url).catch(function () {});
        })
      );
    })
  );
  self.skipWaiting();
});

self.addEventListener("activate", function (evento) {
  evento.waitUntil(
    caches.keys().then(function (nomes) {
      return Promise.all(
        nomes.filter(function (n) { return n !== CACHE_NOME; })
             .map(function (n) { return caches.delete(n); })
      );
    })
  );
  self.clients.claim();
});

// Estratégia: tenta a rede primeiro (pra pegar atualizações), cai pro cache se offline
self.addEventListener("fetch", function (evento) {
  if (evento.request.method !== "GET") return;

  evento.respondWith(
    fetch(evento.request)
      .then(function (resposta) {
        var copia = resposta.clone();
        caches.open(CACHE_NOME).then(function (cache) { cache.put(evento.request, copia); });
        return resposta;
      })
      .catch(function () {
        return caches.match(evento.request);
      })
  );
});
