# ADR 011: observabilidade e hardening sem nova infraestrutura obrigatória

## Status

Aceita.

## Decisão

Usar Micrometer/Actuator no backend para métricas do domínio e das filas e expor métricas Prometheus nativas no worker. Limitar concorrência global e de uploads com semáforos não bloqueantes, retornando RFC 9457 com `429` e `Retry-After`. Manter Basic Auth apenas para o MVP local, com senha mínima, documentação pública configurável e respostas `401/403` sem detalhes internos.

## Razões

O pipeline já possui persistência de estado, outbox/inbox, retry e DLQ. As lacunas relevantes eram enxergar duração, falhas, renders parciais e backlog, além de impedir que uploads simultâneos esgotassem memória, disco ou threads. Essas medidas entregam proteção imediata sem adicionar Redis, gateway ou outro serviço ao ambiente de uma pessoa.

## Consequências

- Prometheus pode coletar o backend em `/actuator/prometheus` e o worker em `/metrics`.
- Valores `-1` em métricas de fila indicam RabbitMQ indisponível ou fila ainda não declarada.
- O limite é por instância; quando houver múltiplas réplicas, rate limiting distribuído deverá ficar no gateway.
- Em produção, `CLIPADOR_API_DOCS_PUBLIC=false` protege também Swagger e OpenAPI.
- JWT/OAuth2 permanece evolução posterior e não é necessário para o MVP local.
