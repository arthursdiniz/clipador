# ADR 007: Entrega at-least-once com outbox e inbox

- **Status:** aceito
- **Data:** 2026-08-25

## Decisão

Adotar entrega `at-least-once`: o backend grava comandos em transactional outbox, o relay exige publisher confirm, consumidores usam ack manual e cada lado mantém uma inbox persistente. Mensagens têm UUID e contrato versionado. Retries passam por filas TTL e falhas esgotadas seguem para DLQ.

## Razão

PostgreSQL e RabbitMQ não participam de uma transação distribuída. A outbox elimina a perda entre commit e publicação; a inbox torna redelivery e republicação seguros. Essa solução é menor e mais operacionalmente previsível que 2PC, exatamente-once aparente ou um orquestrador adicional.

## Consequências

Uma mensagem pode ser publicada mais de uma vez. Handlers precisam permanecer idempotentes e artefatos devem usar chaves determinísticas. A tabela de outbox e as inboxes exigem política futura de retenção. DLQs exigem inspeção e replay consciente, nunca replay automático infinito.
