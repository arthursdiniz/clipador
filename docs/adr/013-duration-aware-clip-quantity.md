# ADR 013 — Quantidade de cortes orientada pela duração

- Status: aceito
- Data: 2026-08-27

## Contexto

Uma quantidade fixa desperdiça material em vídeos longos, enquanto pedir muitos cortes de um vídeo curto incentiva repetição e trechos incompletos. O usuário também precisa controlar a quantidade quando possui uma necessidade editorial específica.

## Decisão

Persistir em cada job um modo de quantidade: `AUTO`, `EXTENDED` ou `MANUAL`. O automático calcula `max(5, ceil(duração_em_minutos / 5))`, limitado a 20. O ampliado aplica multiplicador 1,5 e garante pelo menos três opções extras, limitado a 30. O manual aceita de 1 a 30. Todos os parâmetros são configuráveis por ambiente.

A meta só é resolvida quando a duração validada está disponível e fica persistida no job. Ela limita a seleção não redundante, não a geração bruta de candidatos. Por isso é uma meta máxima e não uma promessa de preencher a quantidade com cortes de baixa qualidade.

## Alternativas consideradas

- Quantidade fixa global: simples, mas não acompanha a quantidade de conteúdo disponível.
- Apenas controle manual: transfere ao usuário uma estimativa que o sistema já consegue fazer pela duração.
- Densidade baseada somente no número de palavras: útil futuramente, porém instável entre idiomas, pausas e estilos de vídeo.

## Consequências

- Vídeos longos produzem mais resultados por padrão sem configuração adicional.
- O usuário pode pedir mais opções ou uma quantidade explícita no cadastro por URL e no upload.
- O número final pode ser menor que a meta quando os filtros de qualidade, sobreposição e similaridade eliminam candidatos.
