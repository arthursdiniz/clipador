# ADR 008: análise multimodal local como provider padrão

- **Status:** aceito
- **Data:** 2026-08-26

## Decisão

Usar `LocalMultimodalClipAnalyzer`, atrás do contrato `ClipAnalysisProvider`, como provider padrão da Fase 5. Ele combina fronteiras linguísticas da transcrição, semântica explicável em português/inglês, energia do WAV e variação visual amostrada pelo FFmpeg. `OllamaClipAnalysisProvider` é uma opção local que reavalia candidatos via `/api/chat` com JSON Schema e temperatura zero. O backend Java importa scores individuais e executa a seleção final por sobreposição temporal e similaridade textual.

## Razão

O modo local precisa ser funcional sem chave externa, servidor de modelo adicional ou GPU. A amostragem visual em escala de cinza é barata e suficiente para sinalizar movimento e trocas; detecção de pessoas, active speaker e tracking pertencem ao reenquadramento da Fase 7. Manter diversidade e estados no Java preserva as regras de produto no núcleo.

## Consequências

O modo heurístico é determinístico, barato e explicável, mas sua compreensão semântica é inferior à do modo Ollama. Providers OpenAI ou Gemini poderão gerar o mesmo artefato versionado; qualquer saída continua sujeita à validação, aos pesos e à seleção do backend.
