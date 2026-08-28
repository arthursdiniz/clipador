import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { Film, LockKeyhole, LogOut, MonitorPlay, Play, Sparkles } from 'lucide-react'
import { ApiError, api, verifyCredentials } from './api'
import { ClipModal } from './components/ClipModal'
import { IngestPanel } from './components/IngestPanel'
import { ProjectWorkspace } from './components/ProjectWorkspace'
import { VideoLibrary } from './components/VideoLibrary'
import { ACTIVE_STATUSES, type Clip, type Credentials, type Job, type Registration, type Transcript, type Video } from './types'
import './App.css'

function Brand() {
  return <div className="brand" aria-label="Clipador"><span className="brand-mark"><Play size={17} fill="currentColor" /></span><span>clipador</span></div>
}

function messageOf(caught: unknown, fallback: string) {
  return caught instanceof ApiError ? caught.message : fallback
}

function LoginScreen({ onLogin }: { onLogin: (credentials: Credentials) => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setSubmitting(true)
    const credentials = { username: username.trim(), password }
    try { await verifyCredentials(credentials); onLogin(credentials) }
    catch (caught) { setError(messageOf(caught, 'Não foi possível acessar o backend local.')) }
    finally { setSubmitting(false) }
  }

  return (
    <main className="login-page">
      <section className="login-story">
        <Brand />
        <div className="story-copy"><span className="eyebrow"><Sparkles size={15} /> Estúdio inteligente de cortes</span><h1>Do vídeo longo ao momento que prende.</h1><p>Transcreva, encontre os melhores trechos e gere clipes verticais prontos para publicar — em um fluxo só.</p></div>
        <div className="pipeline-preview" aria-label="Etapas do processamento"><div className="pipeline-card pipeline-source"><Film size={22} /><span>Vídeo original</span><strong>Conteúdo completo</strong></div><div className="pipeline-line"><span /></div><div className="pipeline-card pipeline-result"><Sparkles size={22} /><span>Clip Intelligence</span><strong>Melhores momentos</strong></div></div>
        <p className="local-note">Processamento local · seus arquivos continuam sob seu controle</p>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <span className="login-icon"><LockKeyhole size={21} /></span><h2>Bem-vindo de volta</h2><p>Entre com o usuário configurado no backend.</p>
          <form onSubmit={handleSubmit}>
            <label htmlFor="username">Usuário</label><input id="username" value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
            <label htmlFor="password">Senha</label><input id="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" placeholder="Digite sua senha" required />
            {error && <div className="form-error" role="alert">{error}</div>}
            <button className="primary-button login-button" type="submit" disabled={submitting}>{submitting ? 'Conectando…' : 'Entrar no estúdio'} {!submitting && <span aria-hidden>→</span>}</button>
          </form>
          <div className="login-help"><span className="status-dot" /> Backend esperado em localhost:8080</div>
        </div>
      </section>
    </main>
  )
}

function Studio({ credentials, onLogout }: { credentials: Credentials; onLogout: () => void }) {
  const [view, setView] = useState<'studio' | 'videos'>('studio')
  const [videos, setVideos] = useState<Video[]>([])
  const [jobsByVideo, setJobsByVideo] = useState<Record<string, Job | undefined>>({})
  const [selectedVideo, setSelectedVideo] = useState<Video | null>(null)
  const [currentJob, setCurrentJob] = useState<Job | undefined>()
  const [clips, setClips] = useState<Clip[]>([])
  const [transcript, setTranscript] = useState<Transcript | null>(null)
  const [loading, setLoading] = useState(true)
  const [transcriptLoading, setTranscriptLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [previewClip, setPreviewClip] = useState<Clip | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [previewError, setPreviewError] = useState('')

  const closePreview = useCallback(() => {
    setPreviewUrl((current) => {
      if (current) URL.revokeObjectURL(current)
      return null
    })
    setPreviewClip(null)
    setPreviewError('')
  }, [])

  const loadProject = useCallback(async (video: Video, silent = false) => {
    if (!silent) setLoading(true)
    setError('')
    try {
      const [jobsPage, clipsPage] = await Promise.all([api.listJobs(credentials, video.id), api.listClips(credentials, video.id)])
      const latestJob = jobsPage.content[0]
      setSelectedVideo(video); setCurrentJob(latestJob); setClips(clipsPage.content); setTranscript(null)
      if (latestJob) setJobsByVideo((existing) => ({ ...existing, [video.id]: latestJob }))
    } catch (caught) { setError(messageOf(caught, 'Não foi possível carregar o projeto.')) }
    finally { if (!silent) setLoading(false) }
  }, [credentials])

  const loadVideos = useCallback(async (preferredId?: string) => {
    setLoading(true); setError('')
    try {
      const page = await api.listVideos(credentials)
      setVideos(page.content)
      const jobPairs = await Promise.all(page.content.map(async (video) => {
        try { return [video.id, (await api.listJobs(credentials, video.id)).content[0]] as const }
        catch { return [video.id, undefined] as const }
      }))
      setJobsByVideo(Object.fromEntries(jobPairs))
      const preferred = page.content.find((video) => video.id === preferredId)
        || page.content[0]
      if (preferred) await loadProject(preferred, true)
      else { setSelectedVideo(null); setCurrentJob(undefined); setClips([]) }
    } catch (caught) { setError(messageOf(caught, 'Não foi possível carregar os vídeos.')) }
    finally { setLoading(false) }
  }, [credentials, loadProject])

  useEffect(() => {
    void Promise.resolve().then(() => loadVideos())
  }, [loadVideos])

  useEffect(() => {
    if (!currentJob || !ACTIVE_STATUSES.includes(currentJob.status) || !selectedVideo) return
    const timer = window.setInterval(async () => {
      try {
        const progress = await api.getProgress(credentials, currentJob.id)
        const updated = { ...currentJob, ...progress, id: progress.jobId }
        setCurrentJob(updated)
        setJobsByVideo((existing) => ({ ...existing, [selectedVideo.id]: updated }))
        if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
          setClips((await api.listClips(credentials, selectedVideo.id)).content)
        }
      } catch (caught) { setError(messageOf(caught, 'A atualização automática foi interrompida.')) }
    }, 2500)
    return () => window.clearInterval(timer)
  }, [credentials, currentJob, selectedVideo])

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(''), 4500)
    return () => window.clearTimeout(timer)
  }, [notice])

  useEffect(() => {
    function closeWithEscape(event: KeyboardEvent) { if (event.key === 'Escape') closePreview() }
    window.addEventListener('keydown', closeWithEscape)
    return () => window.removeEventListener('keydown', closeWithEscape)
  }, [closePreview])

  async function handleCreated(registration: Registration) {
    setNotice(registration.created ? 'Vídeo recebido. O processamento foi iniciado.' : 'Este vídeo já estava registrado; abrimos o processamento existente.')
    await loadVideos(registration.videoId)
  }

  async function retry() {
    if (!currentJob || !selectedVideo) return
    try { const job = await api.retryJob(credentials, currentJob.id); setCurrentJob(job); setJobsByVideo((value) => ({ ...value, [selectedVideo.id]: job })); setNotice('Processamento reenviado para a fila.') }
    catch (caught) { setError(messageOf(caught, 'Não foi possível tentar novamente.')) }
  }

  async function cancel() {
    if (!currentJob || !selectedVideo) return
    try { const job = await api.cancelJob(credentials, currentJob.id); setCurrentJob(job); setJobsByVideo((value) => ({ ...value, [selectedVideo.id]: job })); setNotice('Cancelamento solicitado.') }
    catch (caught) { setError(messageOf(caught, 'Não foi possível cancelar.')) }
  }

  async function loadTranscript() {
    if (!currentJob) return
    setTranscriptLoading(true)
    try { setTranscript(await api.getTranscript(credentials, currentJob.id)) }
    catch (caught) { setError(messageOf(caught, 'A transcrição ainda não está disponível.')) }
    finally { setTranscriptLoading(false) }
  }

  async function download(clip: Clip) {
    try {
      const blob = await api.downloadClip(credentials, clip.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a'); link.href = url; link.download = `clipador-${clip.id}.mp4`; link.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (caught) { setError(messageOf(caught, 'Não foi possível baixar o clipe.')) }
  }

  async function preview(clip: Clip) {
    closePreview(); setPreviewClip(clip); setPreviewError('')
    try { setPreviewUrl(URL.createObjectURL(await api.downloadClip(credentials, clip.id))) }
    catch (caught) { setPreviewError(messageOf(caught, 'Não foi possível preparar a prévia.')) }
  }

  return (
    <div className="studio-shell">
      <aside className="sidebar">
        <Brand />
        <nav aria-label="Navegação principal">
          <button className={`nav-item ${view === 'studio' ? 'active' : ''}`} onClick={() => setView('studio')}><MonitorPlay size={19} /> <span>Estúdio</span></button>
          <button className={`nav-item ${view === 'videos' ? 'active' : ''}`} onClick={() => setView('videos')}><Film size={19} /> <span>Meus vídeos</span></button>
        </nav>
        <div className="sidebar-user"><span className="avatar">{credentials.username.slice(0, 1).toUpperCase()}</span><div><strong>{credentials.username}</strong><small>Ambiente local</small></div><button className="icon-button" onClick={onLogout} aria-label="Sair"><LogOut size={18} /></button></div>
      </aside>
      <main className="studio-main">
        <header className="studio-header"><div><span className="eyebrow">{view === 'studio' ? 'Seu espaço de criação' : 'Biblioteca local'}</span><h1>{view === 'studio' ? 'Transforme conteúdo em clipes.' : 'Seus vídeos e resultados.'}</h1></div><span className="connection-badge"><span className="status-dot" /> Backend conectado</span></header>
        {error && <div className="global-alert" role="alert"><span>{error}</span><button onClick={() => setError('')}>Fechar</button></div>}
        {notice && <div className="toast" role="status"><span className="status-dot" /> {notice}</div>}
        {view === 'studio' && <IngestPanel credentials={credentials} onCreated={handleCreated} onError={setError} />}
        <VideoLibrary videos={videos} jobsByVideo={jobsByVideo} selectedId={selectedVideo?.id || null} onSelect={(video) => void loadProject(video)} />
        {selectedVideo && <ProjectWorkspace video={selectedVideo} job={currentJob} clips={clips} transcript={transcript} loading={loading} transcriptLoading={transcriptLoading} onRefresh={() => void loadProject(selectedVideo)} onRetry={() => void retry()} onCancel={() => void cancel()} onPreview={(clip) => void preview(clip)} onDownload={(clip) => void download(clip)} onLoadTranscript={() => void loadTranscript()} />}
        {loading && videos.length === 0 && <div className="loading-block">Carregando seus projetos…</div>}
      </main>
      {previewClip && <ClipModal clip={previewClip} url={previewUrl} error={previewError} onClose={closePreview} onDownload={() => void download(previewClip)} />}
    </div>
  )
}

export default function App() {
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  return credentials ? <Studio credentials={credentials} onLogout={() => setCredentials(null)} /> : <LoginScreen onLogin={setCredentials} />
}
