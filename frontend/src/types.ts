export type Credentials = { username: string; password: string }
export type ClipQuantityMode = 'AUTO' | 'EXTENDED' | 'MANUAL'

export type JobStatus =
  | 'RECEIVED' | 'DOWNLOADING' | 'DOWNLOADED' | 'EXTRACTING_AUDIO'
  | 'TRANSCRIBING' | 'TRANSCRIBED' | 'ANALYZING' | 'ANALYZED'
  | 'SELECTING_CLIPS' | 'GENERATING_CLIPS' | 'GENERATING_SUBTITLES'
  | 'RENDERING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export type Registration = {
  videoId: string
  jobId: string
  status: JobStatus
  correlationId: string
  created: boolean
}

export type Video = {
  id: string
  sourceType: 'YOUTUBE' | 'UPLOAD'
  sourceUrl: string | null
  originalFilename: string | null
  title: string
  channel: string | null
  durationSeconds: number | null
  width: number | null
  height: number | null
  fps: number | null
  videoCodec: string | null
  audioCodec: string | null
  detectedLanguage: string | null
  thumbnailUrl: string | null
  createdAt: string
  updatedAt: string
}

export type Job = {
  id: string
  videoId: string
  status: JobStatus
  progress: number
  currentStage: string
  errorCode: string | null
  errorMessage: string | null
  correlationId: string
  attemptCount: number
  clipQuantityMode: ClipQuantityMode
  requestedClipCount: number | null
  targetClipCount: number | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export type JobProgress = Pick<Job, 'status' | 'progress' | 'currentStage' | 'errorCode' | 'errorMessage' | 'clipQuantityMode' | 'requestedClipCount' | 'targetClipCount' | 'updatedAt'> & { jobId: string }

export type Clip = {
  id: string
  jobId: string
  candidateId: string
  title: string | null
  format: 'HORIZONTAL_16_9' | 'VERTICAL_9_16' | 'SQUARE_1_1'
  width: number
  height: number
  durationSeconds: number
  subtitlePath: string | null
  srtPath: string | null
  vttPath: string | null
  assPath: string | null
  thumbnailPath: string | null
  renderError: string | null
  createdAt: string
}

export type Transcript = {
  id: string
  detectedLanguage: string
  languageProbability: number | null
  engine: string
  modelName: string
  wordTimestamps: boolean
  fullText: string
}

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export const ACTIVE_STATUSES: JobStatus[] = [
  'RECEIVED', 'DOWNLOADING', 'DOWNLOADED', 'EXTRACTING_AUDIO', 'TRANSCRIBING',
  'TRANSCRIBED', 'ANALYZING', 'ANALYZED', 'SELECTING_CLIPS', 'GENERATING_CLIPS',
  'GENERATING_SUBTITLES', 'RENDERING',
]
