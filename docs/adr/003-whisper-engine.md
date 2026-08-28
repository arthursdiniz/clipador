# ADR 003: faster-whisper como engine local inicial

- **Status:** aceito
- **Data:** 2026-08-25

## Decisão

Usar faster-whisper 1.2.1 sobre CTranslate2, com Silero VAD e timestamps por palavra. O engine fica atrás de `TranscriptionProvider` no worker; o baseline local é o modelo `small`, CPU `int8`, beam size 5.

## Razão

Entrega boa relação entre qualidade, velocidade, memória e execução CPU/GPU local. O isolamento permite usar uma API externa para cargas específicas sem alterar o pipeline.

## Consequências

Modelos são cache externo ao repositório e baixados no primeiro uso. CPU usa quantização `int8`; CUDA usa precisão configurável. O formato do artefato de transcrição é independente do provider. Diarização permanece opcional porque aumenta custo e dependências.
