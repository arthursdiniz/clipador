# ADR 012 — Cliente web React local

- Status: aceito
- Data: 2026-08-27

## Contexto

A Fase 9 precisa oferecer upload/URL, acompanhamento do pipeline, consulta de resultados, reprodução e download sem mover regras do domínio para o navegador. O projeto é executado nativamente por uma pessoa e o backend Spring Boot já expõe a API necessária com Basic Auth.

## Decisão

Usar React 19 com TypeScript e Vite em `frontend/`, como SPA sem roteamento obrigatório. O cliente mantém credenciais somente em memória, usa polling de 2,5 segundos apenas enquanto o job está ativo e acessa clipes autenticados sob demanda por Blob. Durante o desenvolvimento, o proxy do Vite encaminha `/api` para `127.0.0.1:8080`; o backend não precisa liberar CORS.

O frontend não interpreta nem avança a state machine. Ele apenas apresenta estado persistido e invoca os comandos públicos de criar, cancelar e repetir. A API continua sendo a fonte da verdade.

## Alternativas consideradas

- Templates server-side: simples para telas estáticas, mas menos adequados para upload com progresso, polling concorrente e preview autenticado.
- Next.js: adicionaria runtime e renderização no servidor sem benefício para uma aplicação local autenticada.
- WebSocket/SSE: dispensado neste estágio; polling limitado é suficiente para a frequência das transições e reduz complexidade operacional.

## Consequências

- React/Vite roda como processo local separado em desenvolvimento.
- O build estático fica em `frontend/dist` e pode ser publicado ou incorporado ao backend futuramente.
- Basic Auth continua apropriado somente para o MVP local; uma implantação remota deverá adotar sessão segura ou OAuth2/OIDC e TLS.
