# ADR 010 — Smart reframing local e degradável

## Status

Aceita em 2026-08-27.

## Contexto

Um crop central perde o interlocutor quando a origem horizontal é convertida para 9:16. Analisar todos os frames com um detector neural grande elevaria tempo, memória, custo de distribuição e dependência de hardware. O MVP precisa rodar localmente em CPU e preservar o clipe quando a visão computacional não encontrar um sujeito.

## Decisão

- O backend publica `RENDER_CLIPS` v2 com opções limitadas e validadas; v1 permanece consumível para redeliveries antigos.
- OpenCV headless amostra frames configuravelmente, sem carregar o vídeo inteiro em memória.
- Cascades locais detectam primeiro rostos e depois parte superior do corpo. Quando não há pessoa, diferença temporal fornece uma região de movimento; sem sinal suficiente, usa-se o centro.
- `AUTO` agrupa pessoas apenas quando a união cabe no crop; caso contrário preserva o sujeito dominante considerando tamanho, centro e continuidade. `FOCUS`, `GROUP` e `BLURRED_BACKGROUND` permitem política explícita.
- A trajetória usa retenção temporal, limite máximo de pan e reset em mudança brusca. Keyframes redundantes são removidos e a quantidade é limitada.
- O FFmpeg recebe uma expressão de crop interpolada como argumento, sem shell nem parâmetros livres do usuário.
- Falha do planejador degrada para vídeo integral sobre fundo desfocado e fica registrada no manifesto. Falha de um formato continua isolada.

## Consequências

O modo local é barato, determinístico e adequado a podcasts e entrevistas com enquadramento razoável. Cascades são menos precisas que detectores neurais modernos em perfis, oclusões e planos abertos; a interface `SubjectDetector` permite trocar o detector por ONNX sem alterar o renderer ou o contrato. Active speaker por diarização e composição split-screen são evoluções possíveis, não requisitos da primeira implementação de reenquadramento.
