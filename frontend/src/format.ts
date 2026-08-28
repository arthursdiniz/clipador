import type { JobStatus } from './types'

export const statusLabels: Record<JobStatus, string> = {
  RECEIVED: 'Recebido', DOWNLOADING: 'Baixando vídeo', DOWNLOADED: 'Download concluído',
  EXTRACTING_AUDIO: 'Preparando áudio', TRANSCRIBING: 'Transcrevendo', TRANSCRIBED: 'Transcrição pronta',
  ANALYZING: 'Analisando momentos', ANALYZED: 'Análise pronta', SELECTING_CLIPS: 'Selecionando clipes',
  GENERATING_CLIPS: 'Gerando clipes', GENERATING_SUBTITLES: 'Gerando legendas', RENDERING: 'Renderizando',
  COMPLETED: 'Concluído', FAILED: 'Falhou', CANCELLED: 'Cancelado',
}

export function formatDuration(seconds: number | null) {
  if (seconds == null || !Number.isFinite(Number(seconds))) return 'Duração pendente'
  const value = Math.max(0, Math.round(Number(seconds)))
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  const secs = value % 60
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
    : `${minutes}:${String(secs).padStart(2, '0')}`
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

export function videoName(title: string | null, originalFilename: string | null) {
  return title || originalFilename || 'Vídeo sem título'
}

export function formatLabel(format: string) {
  if (format === 'VERTICAL_9_16') return '9:16 vertical'
  if (format === 'SQUARE_1_1') return '1:1 quadrado'
  return '16:9 horizontal'
}
