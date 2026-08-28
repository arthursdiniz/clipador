import { Download, LoaderCircle, X } from 'lucide-react'
import type { Clip } from '../types'
import { formatDuration, formatLabel } from '../format'

type Props = {
  clip: Clip
  url: string | null
  error: string
  onClose: () => void
  onDownload: () => void
}

export function ClipModal({ clip, url, error, onClose, onDownload }: Props) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="clip-modal" role="dialog" aria-modal="true" aria-label="Prévia do clipe">
        <header><div><strong>Prévia do clipe</strong><small>{formatLabel(clip.format)} · {formatDuration(clip.durationSeconds)}</small></div><button className="icon-button modal-close" onClick={onClose} aria-label="Fechar"><X size={20} /></button></header>
        <div className="video-stage">
          {!url && !error && <div className="preview-loading"><LoaderCircle className="spin" /> Preparando prévia…</div>}
          {error && <div className="preview-loading error">{error}</div>}
          {url && <video src={url} controls autoPlay playsInline />}
        </div>
        <footer><p>O arquivo é carregado sob demanda e liberado ao fechar esta janela.</p><button className="primary-button download-button" onClick={onDownload}><Download size={16} /> Baixar MP4</button></footer>
      </section>
    </div>
  )
}
