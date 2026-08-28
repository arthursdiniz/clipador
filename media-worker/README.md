# Clipador media-worker

Worker especializado de mídia. Consome tarefas RabbitMQ para validar o original, gerar áudio PCM mono 16 kHz, transcrever, analisar e renderizar cortes. OpenCV detecta rostos, pessoas e movimento em frames amostrados; o FFmpeg aplica crop interpolado e suavizado. A renderização produz MP4 H.264/AAC, SRT/VTT/ASS e JPEG por formato, com escrita atômica, fallback para fundo desfocado e falha isolada. A inbox SQLite torna redelivery idempotente.

O modelo padrão é `small` em CPU `int8`. O primeiro processamento baixa o modelo para o cache configurado.

```powershell
python -m pip install -e ".[dev]"
python -m pytest
python -m uvicorn clipador_worker.app:app --host 127.0.0.1 --port 8090
```

Para executar o sistema completo nativamente no Windows, prefira `infra/local/Start-MediaWorker.ps1`, pois ele compartilha o mesmo storage absoluto usado pelo backend.
