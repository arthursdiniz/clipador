import { AlertTriangle, Captions, Download, ExternalLink, FileText, LoaderCircle, Play, RefreshCw, RotateCcw, Sparkles, Square, StopCircle } from 'lucide-react'
import { formatDate, formatDuration, formatLabel, statusLabels, videoName } from '../format'
import { ACTIVE_STATUSES, type Clip, type Job, type Transcript, type Video } from '../types'

type Props = {
  video: Video
  job?: Job
  clips: Clip[]
  transcript: Transcript | null
  loading: boolean
  transcriptLoading: boolean
  onRefresh: () => void
  onRetry: () => void
  onCancel: () => void
  onPreview: (clip: Clip) => void
  onDownload: (clip: Clip) => void
  onLoadTranscript: () => void
}

const stages = [
  { statuses: ['RECEIVED', 'DOWNLOADING', 'DOWNLOADED'], label: 'Ingestão' },
  { statuses: ['EXTRACTING_AUDIO', 'TRANSCRIBING', 'TRANSCRIBED'], label: 'Transcrição' },
  { statuses: ['ANALYZING', 'ANALYZED', 'SELECTING_CLIPS'], label: 'Inteligência' },
  { statuses: ['GENERATING_CLIPS', 'GENERATING_SUBTITLES', 'RENDERING'], label: 'Renderização' },
  { statuses: ['COMPLETED'], label: 'Concluído' },
]

function stageIndex(job: Job) {
  if (job.status === 'FAILED' || job.status === 'CANCELLED') return Math.max(0, stages.findIndex((stage) => stage.statuses.includes(job.currentStage)))
  return Math.max(0, stages.findIndex((stage) => stage.statuses.includes(job.status)))
}

function clipQuantityLabel(job: Job) {
  const target = job.targetClipCount ?? job.requestedClipCount
  if (!target && ['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status)) return 'Processamento anterior · meta não registrada'
  const suffix = target ? ` · meta de ${target} cortes` : ' · meta calculada após analisar a duração'
  if (job.clipQuantityMode === 'MANUAL') return `Quantidade escolhida${suffix}`
  if (job.clipQuantityMode === 'EXTENDED') return `Mais opções${suffix}`
  return `Automático por duração${suffix}`
}

export function ProjectWorkspace(props: Props) {
  const { video, job, clips, transcript, loading, transcriptLoading } = props
  const active = job && ACTIVE_STATUSES.includes(job.status)
  const currentIndex = job ? stageIndex(job) : 0

  return (
    <section className="workspace-card">
      <header className="project-header">
        <div className="project-title">
          <span className="project-source">{video.sourceType === 'YOUTUBE' ? 'YouTube' : 'Upload local'}</span>
          <h2>{videoName(video.title, video.originalFilename)}</h2>
          <p>{video.channel || 'Canal não informado'} · {formatDuration(video.durationSeconds)} · criado em {formatDate(video.createdAt)}</p>
        </div>
        <div className="project-actions">
          {video.sourceUrl && <a className="icon-text-button" href={video.sourceUrl} target="_blank" rel="noreferrer"><ExternalLink size={16} /> Original</a>}
          <button className="icon-button light" onClick={props.onRefresh} aria-label="Atualizar" disabled={loading}><RefreshCw size={17} className={loading ? 'spin' : ''} /></button>
        </div>
      </header>

      {!job ? <div className="loading-block"><LoaderCircle className="spin" /> Buscando processamento…</div> : <>
        <div className={`job-summary job-${job.status.toLowerCase()}`}>
          <div className="progress-ring" style={{ '--progress': `${job.progress * 3.6}deg` } as React.CSSProperties}><span>{job.progress}<small>%</small></span></div>
          <div className="job-copy">
            <small>Status atual</small>
            <h3>{statusLabels[job.status]}</h3>
            <p>{job.errorMessage || job.currentStage || 'O pipeline está preparando a próxima etapa.'}</p>
            <p className="clip-quantity-summary">{clipQuantityLabel(job)}</p>
          </div>
          <div className="job-controls">
            {active && <button className="danger-quiet" onClick={props.onCancel}><StopCircle size={16} /> Cancelar</button>}
            {(job.status === 'FAILED' || job.status === 'CANCELLED') && <button className="primary-button retry-button" onClick={props.onRetry}><RotateCcw size={16} /> Tentar novamente</button>}
          </div>
        </div>

        <div className="stage-track" aria-label="Etapas do processamento">
          {stages.map((stage, index) => <div key={stage.label} className={`stage ${index < currentIndex || job.status === 'COMPLETED' ? 'done' : ''} ${index === currentIndex && job.status !== 'COMPLETED' ? 'current' : ''}`}><span>{index < currentIndex || job.status === 'COMPLETED' ? '✓' : index + 1}</span><small>{stage.label}</small></div>)}
        </div>

        {job.status === 'FAILED' && <div className="job-alert"><AlertTriangle size={18} /><div><strong>{job.errorCode || 'Falha no processamento'}</strong><p>{job.errorMessage || 'Consulte os logs do backend e tente novamente.'}</p></div></div>}
      </>}

      <div className="results-heading"><div><span className="eyebrow"><Sparkles size={14} /> Resultado</span><h2>Clipes gerados</h2></div><span>{clips.length}{job?.targetClipCount ? ` de até ${job.targetClipCount}` : ''} {clips.length === 1 ? 'clipe' : 'clipes'}</span></div>
      {clips.length === 0 ? (
        <div className="clips-empty"><Sparkles size={24} /><strong>{job?.status === 'COMPLETED' ? 'Nenhum clipe disponível' : 'Os melhores momentos estão a caminho'}</strong><p>{job?.status === 'COMPLETED' ? 'O processamento terminou sem gerar um render utilizável.' : 'Os clipes aparecerão automaticamente quando a renderização avançar.'}</p></div>
      ) : (
        <div className="clips-grid">
          {clips.map((clip, index) => <article className="clip-card" key={clip.id}>
            <button className="clip-visual" onClick={() => !clip.renderError && props.onPreview(clip)} disabled={!!clip.renderError} aria-label={`Assistir clipe ${index + 1}`}>
              <span className="clip-number">#{String(index + 1).padStart(2, '0')}</span>
              {clip.renderError ? <AlertTriangle size={29} /> : <span className="play-button"><Play size={20} fill="currentColor" /></span>}
              <small>{formatDuration(clip.durationSeconds)}</small>
            </button>
            <div className="clip-meta"><div><strong>{clip.title || `Clipe ${index + 1}`}</strong><span><Square size={12} /> {formatLabel(clip.format)}</span></div><button className="icon-button light" onClick={() => props.onDownload(clip)} disabled={!!clip.renderError} aria-label={`Baixar ${clip.title || `clipe ${index + 1}`}`}><Download size={17} /></button></div>
            {clip.subtitlePath && <span className="subtitle-ready"><Captions size={13} /> Legenda incorporada</span>}
            {clip.renderError && <p className="clip-error">{clip.renderError}</p>}
          </article>)}
        </div>
      )}

      {job && ['TRANSCRIBED','ANALYZING','ANALYZED','SELECTING_CLIPS','GENERATING_CLIPS','GENERATING_SUBTITLES','RENDERING','COMPLETED'].includes(job.status) && (
        <div className="transcript-panel">
          <div><span className="transcript-icon"><FileText size={18} /></span><div><strong>Transcrição</strong><small>{transcript ? `${transcript.detectedLanguage} · ${transcript.engine} / ${transcript.modelName}` : 'Texto segmentado e sincronizado'}</small></div></div>
          {!transcript && <button className="icon-text-button" onClick={props.onLoadTranscript} disabled={transcriptLoading}>{transcriptLoading ? 'Carregando…' : 'Ver transcrição'}</button>}
          {transcript && <p className="transcript-text">{transcript.fullText}</p>}
        </div>
      )}
    </section>
  )
}
