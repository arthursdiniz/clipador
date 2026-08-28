# ADR 006: Aquisição do original no backend

- **Status:** aceito
- **Data:** 2026-08-25

## Decisão

Executar upload, `yt-dlp` e `ffprobe` no backend através de adapters isolados. Downloads YouTube usam um executor assíncrono limitado; a thread HTTP apenas registra a solicitação. A partir de `DOWNLOADED`, o processamento intensivo segue para o media-worker via RabbitMQ na Fase 3.

## Razão

Aquisição é I/O e política de entrada, intimamente ligada a SSRF, idempotência, limites e ownership do original. Manter essa fronteira no backend evita um protocolo prematuro para uploads e deixa o worker concentrado em áudio, IA e rendering.

## Consequências

O container do backend inclui yt-dlp e ffprobe, roda sem privilégios e possui volume próprio. Processos recebem argumentos fixos, timeout e diretório temporário controlado. Escala independente de aquisição poderá ser extraída atrás do mesmo adapter se volume justificar.
