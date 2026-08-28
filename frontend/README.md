# Clipador Web

Interface local da Fase 9, construída em React, TypeScript e Vite. É um cliente fino da API Spring Boot: não duplica regras do pipeline e não armazena senha no navegador.

## Executar

Com o backend ativo em `http://127.0.0.1:8080`:

```powershell
npm install
npm run dev
```

Acesse `http://127.0.0.1:5173`. Em desenvolvimento, o proxy do Vite encaminha `/api` ao backend e evita configuração CORS desnecessária.

## Verificar

```powershell
npm run lint
npm test
npm run build
```

As credenciais Basic Auth permanecem apenas no estado em memória e são descartadas ao sair ou recarregar a página. Clipes também são carregados como Blob somente quando o usuário abre a prévia ou solicita o download.
