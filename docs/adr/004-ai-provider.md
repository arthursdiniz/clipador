# ADR 004: Providers de análise semântica substituíveis

- **Status:** aceito
- **Data:** 2026-08-25

## Decisão

Criar `ClipAnalysisProvider` na fronteira especializada de análise, com implementação local padrão e futuras implementações OpenAI, Gemini ou modelo local. O contrato exige saída estruturada versionada e validada pelo backend. Pesos, persistência e regras de diversidade permanecem no domínio Java.

## Razão

Sem essa fronteira, prompt, transporte e regra de produto se misturam e criam vendor lock-in. Manter o score final no backend também torna o comportamento testável e explicável.

## Consequências

Sem API key, o modo local usa heurísticas multimodais determinísticas e não exige Ollama. APIs externas serão opt-in, terão timeout, orçamento e custo registrados por job.
