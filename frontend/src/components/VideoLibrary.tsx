import { Film, Link2, Upload } from 'lucide-react'
import { formatDate, formatDuration, videoName } from '../format'
import type { Job, Video } from '../types'

type Props = {
  videos: Video[]
  jobsByVideo: Record<string, Job | undefined>
  selectedId: string | null
  onSelect: (video: Video) => void
}

export function VideoLibrary({ videos, jobsByVideo, selectedId, onSelect }: Props) {
  if (videos.length === 0) return (
    <section className="empty-library">
      <span><Film size={26} /></span>
      <h2>Seus projetos aparecerão aqui</h2>
      <p>Adicione um link ou arquivo para acompanhar o processamento e encontrar os clipes prontos.</p>
    </section>
  )

  return (
    <section className="library-section">
      <div className="section-heading library-heading"><div><h2>Projetos recentes</h2><p>{videos.length} {videos.length === 1 ? 'vídeo' : 'vídeos'} no estúdio</p></div></div>
      <div className="video-list">
        {videos.map((video) => {
          const job = jobsByVideo[video.id]
          return (
            <button key={video.id} className={`video-row ${selectedId === video.id ? 'selected' : ''}`} onClick={() => onSelect(video)}>
              <span className="video-thumb">{video.sourceType === 'YOUTUBE' ? <Link2 size={20} /> : <Upload size={20} />}</span>
              <span className="video-info"><strong>{videoName(video.title, video.originalFilename)}</strong><small>{formatDuration(video.durationSeconds)} · {formatDate(video.createdAt)}</small></span>
              {job ? <span className={`status-pill status-${job.status.toLowerCase()}`}>{job.status === 'COMPLETED' ? 'Pronto' : job.status === 'FAILED' ? 'Falhou' : `${job.progress}%`}</span> : <span className="status-pill">Preparando</span>}
            </button>
          )
        })}
      </div>
    </section>
  )
}
