# Execução local sem Docker

Docker não faz parte do runtime do Clipador. Para desenvolvimento nativo, cada dependência roda como processo/serviço local e o backend e o worker compartilham o mesmo diretório absoluto de storage.

## Dependências

- JDK 25 e Maven 3.9+;
- PostgreSQL 18;
- RabbitMQ 4.3.5 e Erlang/OTP 27.3;
- Python 3.12 ou 3.13;
- FFmpeg/ffprobe com libx264 e libass;
- yt-dlp;
- Node.js 24 LTS ou mais recente e npm.

Confira o que já está disponível:

```powershell
.\infra\local\Test-Prerequisites.ps1
```

## Instalação portátil e serviços

O instalador baixa yt-dlp, Erlang e RabbitMQ para `tools/`, verifica SHA-256 e não altera instalações globais:

```powershell
& .\media-worker\.venv\Scripts\python.exe .\infra\local\download_portable_tools.py
.\infra\local\Initialize-LocalEnvironment.ps1
```

O segundo comando cria `.env.local` com segredos aleatórios, inicializa um cluster PostgreSQL exclusivo em `data/postgres` na porta `55432`, cria o banco/usuário `clipador` e configura RabbitMQ somente em loopback (`5672`; painel `15672`). A instalação PostgreSQL 18 existente fornece apenas os binários; o banco global na porta `5432` não é modificado.

Os downloads são idempotentes. O estado de PostgreSQL e RabbitMQ e todos os segredos ficam em caminhos ignorados pelo Git.

## Configuração

O bootstrap cria `.env.local` automaticamente. `.env.local.example` existe apenas para configuração manual; substitua todos os valores de exemplo caso opte por copiá-lo. Os scripts definem caminhos absolutos comuns para storage, temporários, inbox SQLite e cache do Whisper.

Os inicializadores procuram primeiro executáveis no `PATH`, depois em `tools/ffmpeg` dentro do projeto e, por compatibilidade, no Shotcut. A build do Shotcut encontrada nesta máquina não possui libass e não serve para burn-in; use uma build completa indicada na [página oficial de downloads do FFmpeg](https://ffmpeg.org/download.html#build-windows). Também é possível definir `CLIPADOR_FFMPEG_EXECUTABLE` e `CLIPADOR_FFPROBE_EXECUTABLE` explicitamente.

## Worker Python

Na raiz do projeto:

```powershell
python -m venv media-worker/.venv
.\media-worker\.venv\Scripts\python.exe -m pip install -e ".\media-worker[dev]"
```

O primeiro processamento baixa o modelo Whisper selecionado e pode demorar. Não há cobrança de API no provider local.

OpenCV headless é instalado com o worker. O smart reframing não baixa modelos: os detectores de rosto e parte superior do corpo são empacotados com a biblioteca e o fallback por movimento também é local.

### LLM local opcional

O modo `local` não exige outro modelo. Para uma avaliação semântica mais profunda, instale/inicie Ollama, baixe um modelo compatível com structured output e altere no `.env.local`:

```dotenv
CLIPADOR_ANALYSIS_PROVIDER=ollama
CLIPADOR_OLLAMA_MODEL=qwen3:4b
```

O endpoint Ollama é limitado a loopback por segurança. Se o serviço ou modelo não estiver disponível, a etapa usa retry limitado e falha de forma observável; não há fallback silencioso que altere a qualidade do job.

## Inicialização

### Inicialização automática recomendada

Depois que o ambiente já foi configurado uma vez, dê dois cliques em `INICIAR-CLIPADOR.cmd` na raiz do projeto. O atalho inicia PostgreSQL, RabbitMQ, backend, media worker e frontend, aguarda os health checks e abre a interface. A janela pode ser fechada depois da mensagem de sucesso; os aplicativos continuam rodando ocultos.

O mesmo fluxo pelo PowerShell é:

```powershell
.\infra\local\Start-Clipador.ps1 -OpenBrowser
```

Logs separados ficam em `data/logs/apps` e os identificadores dos processos iniciados ficam em `data/run`, ambos ignorados pelo Git. Se uma das portas 5173, 8080 ou 8090 já estiver ocupada sem que todo o Clipador esteja saudável, o script interrompe a inicialização em vez de encerrar um processo desconhecido.

Para desligar tudo, dê dois cliques em `PARAR-CLIPADOR.cmd` ou execute:

```powershell
.\infra\local\Stop-Clipador.ps1
```

O encerramento confere o PID e o instante de criação antes de terminar cada árvore de processos, evitando atingir um processo que reutilizou o mesmo identificador.
Prefira aguardar o job atual terminar antes de desligar; se o worker for encerrado durante uma etapa, a mensageria persistente permite recuperação, mas a etapa poderá precisar ser repetida na próxima inicialização.

### Inicialização manual para depuração

Inicie ou confira as dependências:

```powershell
.\infra\local\Start-LocalDependencies.ps1
```

Abra três terminais PowerShell na raiz. No primeiro:

```powershell
.\infra\local\Start-Backend.ps1
```

No segundo:

```powershell
.\infra\local\Start-MediaWorker.ps1
```

No terceiro (na primeira vez, execute `npm install` dentro de `frontend/`):

```powershell
.\infra\local\Start-Frontend.ps1
```

Interface web: `http://localhost:5173`. O usuário é o valor de `CLIPADOR_SECURITY_USERNAME` (por padrão, `admin`) e a senha foi gerada em `.env.local`. A interface guarda essas credenciais somente em memória, portanto recarregar ou sair exige um novo login.

API/Swagger: `http://localhost:8080/swagger-ui.html`. Health do worker: `http://localhost:8090/health`.

Métricas:

- backend: `http://localhost:8080/actuator/prometheus` (Basic Auth);
- worker: `http://localhost:8090/metrics` (somente loopback no script local).

O Swagger fica público no desenvolvimento para facilitar o primeiro uso. Para uma execução exposta fora da máquina, defina `CLIPADOR_API_DOCS_PUBLIC=false`, use uma senha longa e mantenha backend, worker, PostgreSQL e RabbitMQ atrás de firewall ou gateway TLS.

## Smoke de carga

Com backend e worker ativos, execute da raiz:

```powershell
.\media-worker\.venv\Scripts\python.exe .\infra\local\api_load_smoke.py `
  --requests 100 --concurrency 16 --max-p95-ms 2000
```

O teste realiza somente consultas paginadas, lê as credenciais de `.env.local` sem exibi-las e informa falhas, média e p95. Ele é um gate local reproduzível, não substitui um ensaio de capacidade no hardware e com vídeos representativos do ambiente final.

Flyway cria e valida o schema na inicialização do backend. Se os aplicativos foram iniciados manualmente, encerre-os com `Ctrl+C` e então pare PostgreSQL e RabbitMQ:

```powershell
.\infra\local\Stop-LocalDependencies.ps1
```
