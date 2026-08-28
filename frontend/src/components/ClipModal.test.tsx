import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ClipModal } from './ClipModal'
import type { Clip } from '../types'

const clip: Clip = {
  id: 'clip-id',
  jobId: 'job-id',
  candidateId: 'candidate-id',
  title: 'O erro que ninguém percebe',
  format: 'VERTICAL_9_16',
  width: 1080,
  height: 1920,
  durationSeconds: 42,
  subtitlePath: 'subtitles.ass',
  srtPath: 'subtitles.srt',
  vttPath: 'subtitles.vtt',
  assPath: 'subtitles.ass',
  thumbnailPath: 'thumbnail.jpg',
  renderError: null,
  createdAt: '2026-08-28T18:00:00Z',
}
const expectedTitle = 'O erro que ninguém percebe'

describe('ClipModal', () => {
  it('shows and copies the AI-suggested publication title', async () => {
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')

    render(<ClipModal clip={clip} url={null} error="" onClose={vi.fn()} onDownload={vi.fn()} />)

    expect(screen.getByText('Título sugerido pela IA')).toBeInTheDocument()
    expect(screen.getAllByText(expectedTitle).length).toBeGreaterThan(0)
    expect(screen.getByText(`${expectedTitle.length}/100 caracteres`, { exact: false })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Copiar título' }))
    expect(writeText).toHaveBeenCalledWith(expectedTitle)
    expect(screen.getByRole('button', { name: 'Título copiado' })).toBeInTheDocument()
  })
})
