import { describe, expect, it } from 'vitest'
import { formatDuration, formatLabel, statusLabels, videoName } from './format'

describe('formatters', () => {
  it('formats short and long durations', () => {
    expect(formatDuration(62)).toBe('1:02')
    expect(formatDuration(3723)).toBe('1:02:03')
    expect(formatDuration(null)).toBe('Duração pendente')
  })

  it('presents domain labels in Portuguese', () => {
    expect(statusLabels.TRANSCRIBING).toBe('Transcrevendo')
    expect(formatLabel('VERTICAL_9_16')).toBe('9:16 vertical')
    expect(videoName(null, 'episodio.mp4')).toBe('episodio.mp4')
  })
})
