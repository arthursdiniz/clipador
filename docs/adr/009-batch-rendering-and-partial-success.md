# ADR 009: renderização em lote com sucesso parcial por formato

- **Status:** aceito
- **Data:** 2026-08-26

## Decisão

Publicar um comando `RENDER_CLIPS` contendo candidatos selecionados e formatos validados. O worker produz cada combinação candidato × formato de maneira isolada, escreve vídeo, SRT, VTT, ASS e thumbnail atomicamente e devolve um manifesto versionado. O backend valida o manifesto, persiste sucessos e falhas individualmente e conclui o job quando pelo menos um render funciona.

## Razão

Uma mensagem por clipe aumentaria coordenação, concorrência e transições antes de existir necessidade operacional. Um único processo worker já serializa mídia pesada localmente; o lote mantém a state machine simples, enquanto o isolamento interno evita perder todo o processamento por um codec ou formato específico.

## Consequências

Retries são idempotentes porque as chaves de artefato são determinísticas e os arquivos só aparecem após rename atômico. Falhas transitórias do FFmpeg repetem o comando; falhas permanentes entram no manifesto. O schema permite vários formatos por candidato e clipes sem artefato quando existe `render_error`.
