# ADR 002: Storage por porta com implementação local primeiro

- **Status:** aceito
- **Data:** 2026-08-25

## Decisão

Definir `StorageService` no backend e um contrato equivalente no worker. A implementação atual grava incrementalmente em volume local, usa arquivo parcial + rename atômico e chaves geradas pelo servidor no formato `videos/{videoId}/...`. Uma implementação S3-compatible será adicionada sem alterar o domínio.

## Razão

Arquivos grandes não pertencem ao PostgreSQL. Storage local reduz custo e dependências no MVP; a porta existe porque migração para S3/MinIO/R2 é uma necessidade explícita e concreta.

## Consequências

Nunca são persistidos caminhos fornecidos diretamente pelo usuário. A limpeza automática remove temporários, arquivos parciais e diretórios órfãos sem registro após a retenção; originais associados a um `Video` não são removidos implicitamente.
