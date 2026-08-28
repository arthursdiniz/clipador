# ADR 001: RabbitMQ para orquestração assíncrona

- **Status:** aceito
- **Data:** 2026-08-25

## Decisão

Usar RabbitMQ com exchanges/queues duráveis, manual ack, publisher confirms, retry com TTL e dead-letter exchange.

## Razão

O problema é uma fila de trabalho com roteamento e redelivery, não um log de eventos de altíssimo throughput. RabbitMQ exige menos operação local e oferece as primitivas necessárias. Kafka adicionaria custo operacional sem benefício atual.

## Consequências

PostgreSQL permanece fonte da verdade. Uma transactional outbox evita a janela entre commit e publish. Kafka poderá ser introduzido para analytics/event streaming no futuro sem substituir a fila de trabalho.

