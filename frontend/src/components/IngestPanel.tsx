import { type ChangeEvent, type FormEvent, useRef, useState } from 'react'
import { ArrowRight, FileVideo, Gauge, Layers3, Link2, SlidersHorizontal, Upload, X } from 'lucide-react'
import { ApiError, api, uploadVideo } from '../api'
import type { ClipQuantityMode, Credentials, Registration } from '../types'

type Props = {
  credentials: Credentials
  onCreated: (registration: Registration) => void
  onError: (message: string) => void
}

export function IngestPanel({ credentials, onCreated, onError }: Props) {
  const [mode, setMode] = useState<'youtube' | 'upload' | null>(null)
  const [url, setUrl] = useState('')
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [quantityMode, setQuantityMode] = useState<ClipQuantityMode>('AUTO')
  const [clipCount, setClipCount] = useState(10)
  const inputRef = useRef<HTMLInputElement>(null)

  function reset() {
    setMode(null); setUrl(''); setTitle(''); setFile(null); setUploadProgress(0)
    setQuantityMode('AUTO'); setClipCount(10)
  }

  async function submitYoutube(event: FormEvent) {
    event.preventDefault(); setSubmitting(true)
    try { onCreated(await api.createYoutube(credentials, url.trim(), title, quantityMode, clipCount)); reset() }
    catch (caught) { onError(caught instanceof ApiError ? caught.message : 'Não foi possível adicionar o link.') }
    finally { setSubmitting(false) }
  }

  async function submitUpload(event: FormEvent) {
    event.preventDefault()
    if (!file) return
    setSubmitting(true); setUploadProgress(0)
    try { onCreated(await uploadVideo(credentials, file, title, quantityMode, clipCount, setUploadProgress)); reset() }
    catch (caught) { onError(caught instanceof ApiError ? caught.message : 'Não foi possível enviar o arquivo.') }
    finally { setSubmitting(false) }
  }

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0] || null)
  }

  if (mode === 'youtube') return (
    <section className="ingest-form-card">
      <button className="close-button" onClick={reset} aria-label="Fechar"><X size={18} /></button>
      <span className="source-icon"><Link2 size={22} /></span>
      <div className="form-copy"><h2>Adicionar link do YouTube</h2><p>O worker fará o download de forma segura e extrairá os metadados.</p></div>
      <form onSubmit={submitYoutube}>
        <label htmlFor="youtube-url">URL do vídeo</label>
        <input id="youtube-url" type="url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://www.youtube.com/watch?v=…" required autoFocus />
        <label htmlFor="youtube-title">Título personalizado <small>opcional</small></label>
        <input id="youtube-title" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={512} placeholder="Use o título original automaticamente" />
        <QuantitySelector mode={quantityMode} count={clipCount} onMode={setQuantityMode} onCount={setClipCount} />
        <button className="primary-button submit-project" disabled={submitting}>{submitting ? 'Adicionando…' : 'Iniciar processamento'} {!submitting && <ArrowRight size={17} />}</button>
      </form>
    </section>
  )

  if (mode === 'upload') return (
    <section className="ingest-form-card">
      <button className="close-button" onClick={reset} aria-label="Fechar"><X size={18} /></button>
      <span className="source-icon"><Upload size={22} /></span>
      <div className="form-copy"><h2>Enviar arquivo de vídeo</h2><p>Formatos aceitos: MP4, MOV, MKV e WebM.</p></div>
      <form onSubmit={submitUpload}>
        <input ref={inputRef} className="visually-hidden" id="video-file" type="file" accept=".mp4,.mov,.mkv,.webm,video/mp4,video/quicktime,video/x-matroska,video/webm" onChange={chooseFile} />
        <button className={`drop-zone ${file ? 'has-file' : ''}`} type="button" onClick={() => inputRef.current?.click()}>
          <FileVideo size={25} />
          {file ? <><strong>{file.name}</strong><small>{(file.size / 1024 / 1024).toFixed(1)} MB · clique para trocar</small></> : <><strong>Escolha um vídeo do computador</strong><small>O limite é definido na configuração do backend</small></>}
        </button>
        <label htmlFor="upload-title">Título personalizado <small>opcional</small></label>
        <input id="upload-title" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={512} placeholder="Usar o nome do arquivo" />
        <QuantitySelector mode={quantityMode} count={clipCount} onMode={setQuantityMode} onCount={setClipCount} />
        {submitting && <div className="upload-meter"><span style={{ width: `${uploadProgress}%` }} /><small>Enviando {uploadProgress}%</small></div>}
        <button className="primary-button submit-project" disabled={!file || submitting}>{submitting ? 'Enviando…' : 'Enviar e processar'} {!submitting && <ArrowRight size={17} />}</button>
      </form>
    </section>
  )

  return (
    <section className="new-project">
      <div className="section-heading"><div><h2>Novo processamento</h2><p>Escolha como deseja adicionar o vídeo.</p></div></div>
      <div className="source-grid">
        <article className="source-card source-youtube">
          <span className="source-icon"><Link2 size={22} /></span>
          <div><h3>Link do YouTube</h3><p>Cole a URL de um conteúdo que você tem autorização para processar.</p></div>
          <button className="secondary-button" onClick={() => setMode('youtube')}>Adicionar link <ArrowRight size={17} /></button>
        </article>
        <article className="source-card source-upload">
          <span className="source-icon"><Upload size={22} /></span>
          <div><h3>Arquivo do computador</h3><p>Envie MP4, MOV, MKV ou WebM com progresso visível.</p></div>
          <button className="secondary-button" onClick={() => setMode('upload')}>Escolher arquivo <ArrowRight size={17} /></button>
        </article>
      </div>
    </section>
  )
}

function QuantitySelector({ mode, count, onMode, onCount }: {
  mode: ClipQuantityMode
  count: number
  onMode: (mode: ClipQuantityMode) => void
  onCount: (count: number) => void
}) {
  return (
    <fieldset className="quantity-selector">
      <legend>Quantidade de cortes</legend>
      <div className="quantity-options">
        <button type="button" className={mode === 'AUTO' ? 'selected' : ''} aria-pressed={mode === 'AUTO'} onClick={() => onMode('AUTO')}>
          <Gauge size={18} /><span><strong>Automático</strong><small>Aumenta conforme a duração</small></span>
        </button>
        <button type="button" className={mode === 'EXTENDED' ? 'selected' : ''} aria-pressed={mode === 'EXTENDED'} onClick={() => onMode('EXTENDED')}>
          <Layers3 size={18} /><span><strong>Mais opções</strong><small>Cerca de 50% mais cortes</small></span>
        </button>
        <button type="button" className={mode === 'MANUAL' ? 'selected' : ''} aria-pressed={mode === 'MANUAL'} onClick={() => onMode('MANUAL')}>
          <SlidersHorizontal size={18} /><span><strong>Escolher</strong><small>Defina entre 1 e 30</small></span>
        </button>
      </div>
      {mode === 'MANUAL' && <><label className="manual-count" htmlFor="clip-count">Quero <input id="clip-count" type="number" min="1" max="30" value={count} onChange={(event) => onCount(Math.min(30, Math.max(1, Number(event.target.value))))} required /> cortes</label><p>A meta pode resultar em menos cortes se não houver trechos distintos com qualidade suficiente.</p></>}
      {mode !== 'MANUAL' && <p>{mode === 'AUTO' ? 'Mínimo de 5 cortes, crescendo com a minutagem do vídeo.' : 'Gera no mínimo 3 opções extras, respeitando o limite de 30.'}</p>}
    </fieldset>
  )
}
