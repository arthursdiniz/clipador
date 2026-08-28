# Infraestrutura

`infra/local` contém verificações e inicializadores PowerShell para executar backend e worker sem Docker, compartilhando configuração e storage. O `compose.yaml` permanece na raiz como alternativa reproduzível; não é requisito do runtime. Prometheus/Grafana só serão adicionados quando métricas customizadas justificarem a infraestrutura.
