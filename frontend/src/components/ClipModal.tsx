import { useState } from 'react'
import { Check, Copy, Download, LoaderCircle, Sparkles, X } from 'lucide-react'
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
  const [copyStatus, setCopyStatus] = useState<'idle' | 'copied' | 'error'>('idle')
  const suggestedTitle = clip.title?.trim() || 'Título ainda não disponível'

  async function copySuggestedTitle() {
    if (!clip.title?.trim()) return
    try {
      await navigator.clipboard.writeText(clip.title)
      setCopyStatus('copied')
    } catch {
      setCopyStatus('error')
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="clip-modal" role="dialog" aria-modal="true" aria-label="Prévia do clipe">
        <header><div><strong>{clip.title || 'Prévia do clipe'}</strong><small>{formatLabel(clip.format)} · {formatDuration(clip.durationSeconds)}</small></div><button className="icon-button modal-close" onClick={onClose} aria-label="Fechar"><X size={20} /></button></header>
        <div className="video-stage">
          {!url && !error && <div className="preview-loading"><LoaderCircle className="spin" /> Preparando prévia…</div>}
          {error && <div className="preview-loading error">{error}</div>}
          {url && <video src={url} controls autoPlay playsInline />}
        </div>
        <section className="suggested-title" aria-labelledby="suggested-title-label">
          <div className="suggested-title-copy">
            <span id="suggested-title-label"><Sparkles size={14} /> Título sugerido pela IA</span>
            <strong>{suggestedTitle}</strong>
            <small>Use este título ao publicar o corte para destacar o principal gancho do conteúdo.</small>
          </div>
          <div className="suggested-title-action">
            <button className={`copy-title-button ${copyStatus === 'copied' ? 'copied' : ''}`} type="button" onClick={() => void copySuggestedTitle()} disabled={!clip.title?.trim()}>
              {copyStatus === 'copied' ? <Check size={16} /> : <Copy size={16} />}
              {copyStatus === 'copied' ? 'Título copiado' : 'Copiar título'}
            </button>
            {copyStatus === 'error' && <small className="copy-title-error" role="alert">Não foi possível copiar. Selecione o título manualmente.</small>}
          </div>
        </section>
        <footer><p>O arquivo é carregado sob demanda e liberado ao fechar esta janela.</p><button className="primary-button download-button" onClick={onDownload}><Download size={16} /> Baixar MP4</button></footer>
      </section>
    </div>
  )
}
