# ADR 005: Monólito modular no backend

- **Status:** aceito
- **Data:** 2026-08-25

## Decisão

Manter um único deploy Spring Boot organizado por capacidades. Somente o processamento multimídia/ML é separado no `media-worker` Python.

## Razão

Uma pessoa consegue operar, depurar e evoluir esse desenho. Transações de negócio permanecem locais, enquanto a separação Python resolve uma incompatibilidade real de ecossistema.

## Consequências

Os limites de pacote preservam extração futura, mas não são serviços prematuros. Novos serviços só serão criados por necessidade independente de escala, segurança ou ciclo de deploy.

