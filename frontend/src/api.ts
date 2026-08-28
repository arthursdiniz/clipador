import type { Clip, ClipQuantityMode, Credentials, Job, JobProgress, Page, Registration, Transcript, Video } from './types'

type ProblemDetail = { detail?: string; title?: string }

export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function authorization(credentials: Credentials) {
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return `Basic ${btoa(binary)}`
}

async function errorFromResponse(response: Response) {
  let problem: ProblemDetail = {}
  try { problem = await response.json() as ProblemDetail } catch { /* body opcional */ }
  if (response.status === 401) return new ApiError('Usuário ou senha inválidos.', response.status)
  return new ApiError(problem.detail || problem.title || `A operação falhou (${response.status}).`, response.status)
}

async function request<T>(credentials: Credentials, path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { Authorization: authorization(credentials), ...init.headers },
    })
  } catch {
    throw new ApiError('Backend indisponível. Inicie o Clipador na porta 8080 e tente novamente.', 0)
  }
  if (!response.ok) throw await errorFromResponse(response)
  return response.json() as Promise<T>
}

export async function verifyCredentials(credentials: Credentials) {
  await request<Page<Video>>(credentials, '/api/v1/videos?size=1')
}

export const api = {
  listVideos: (credentials: Credentials) => request<Page<Video>>(credentials, '/api/v1/videos?page=0&size=50&sort=createdAt,desc'),
  getVideo: (credentials: Credentials, id: string) => request<Video>(credentials, `/api/v1/videos/${id}`),
  listJobs: (credentials: Credentials, videoId: string) => request<Page<Job>>(credentials, `/api/v1/videos/${videoId}/jobs?page=0&size=20&sort=createdAt,desc`),
  getProgress: (credentials: Credentials, jobId: string) => request<JobProgress>(credentials, `/api/v1/jobs/${jobId}/progress`),
  listClips: (credentials: Credentials, videoId: string) => request<Page<Clip>>(credentials, `/api/v1/videos/${videoId}/clips?page=0&size=100&sort=createdAt,desc`),
  getTranscript: (credentials: Credentials, jobId: string) => request<Transcript>(credentials, `/api/v1/jobs/${jobId}/transcript`),
  retryJob: (credentials: Credentials, jobId: string) => request<Job>(credentials, `/api/v1/jobs/${jobId}/retry`, { method: 'POST' }),
  cancelJob: (credentials: Credentials, jobId: string) => request<Job>(credentials, `/api/v1/jobs/${jobId}/cancel`, { method: 'POST' }),
  createYoutube: (credentials: Credentials, url: string, title: string,
    clipQuantityMode: ClipQuantityMode, clipCount: number | null) => request<Registration>(credentials, '/api/v1/videos/youtube', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ url, title: title.trim() || null, clipQuantityMode,
      clipCount: clipQuantityMode === 'MANUAL' ? clipCount : null }),
  }),
  downloadClip: async (credentials: Credentials, clipId: string) => {
    let response: Response
    try {
      response = await fetch(`/api/v1/clips/${clipId}/download`, { headers: { Authorization: authorization(credentials) } })
    } catch {
      throw new ApiError('Backend indisponível durante o download.', 0)
    }
    if (!response.ok) throw await errorFromResponse(response)
    return response.blob()
  },
}

export function uploadVideo(
  credentials: Credentials,
  file: File,
  title: string,
  clipQuantityMode: ClipQuantityMode,
  clipCount: number | null,
  onProgress: (progress: number) => void,
) {
  return new Promise<Registration>((resolve, reject) => {
    const data = new FormData()
    data.append('file', file)
    if (title.trim()) data.append('title', title.trim())
    data.append('clipQuantityMode', clipQuantityMode)
    if (clipQuantityMode === 'MANUAL' && clipCount != null) data.append('clipCount', String(clipCount))
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/v1/videos/upload')
    xhr.setRequestHeader('Authorization', authorization(credentials))
    xhr.setRequestHeader('Idempotency-Key', crypto.randomUUID())
    xhr.upload.onprogress = (event) => event.lengthComputable && onProgress(Math.round((event.loaded / event.total) * 100))
    xhr.onerror = () => reject(new ApiError('A conexão foi interrompida durante o upload.', 0))
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText) as Registration)
        return
      }
      let problem: ProblemDetail = {}
      try { problem = JSON.parse(xhr.responseText) as ProblemDetail } catch { /* body opcional */ }
      reject(new ApiError(problem.detail || problem.title || `O upload falhou (${xhr.status}).`, xhr.status))
    }
    xhr.send(data)
  })
}
