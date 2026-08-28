import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, verifyCredentials } from './api'

const credentials = { username: 'admin', password: 'segredo' }

describe('API client', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()))

  it('sends basic authentication without exposing it in the URL', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ content: [] }), { status: 200 }))
    await verifyCredentials(credentials)
    const [path, options] = vi.mocked(fetch).mock.calls[0]
    expect(path).toBe('/api/v1/videos?size=1')
    const headers = (options?.headers ?? {}) as Record<string, string>
    expect(headers.Authorization).toMatch(/^Basic /)
  })

  it('translates an unauthorized response into a useful message', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('', { status: 401 }))
    try {
      await verifyCredentials(credentials)
      expect.fail('A autenticação deveria falhar')
    } catch (caught) {
      expect(caught).toBeInstanceOf(ApiError)
      expect(caught).toMatchObject({ status: 401, message: 'Usuário ou senha inválidos.' })
    }
  })

  it('uses an idempotency key when registering a YouTube URL', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ videoId: 'v', jobId: 'j', status: 'RECEIVED', created: true }), { status: 202 }))
    await api.createYoutube(credentials, 'https://www.youtube.com/watch?v=abc12345', '', 'EXTENDED', null)
    const [, options] = vi.mocked(fetch).mock.calls[0]
    const headers = (options?.headers ?? {}) as Record<string, string>
    expect(headers['Idempotency-Key']).toBeTruthy()
    expect(options?.body).toContain('youtube.com')
    expect(options?.body).toContain('"clipQuantityMode":"EXTENDED"')
  })

  it('returns the engaging filename supplied by the backend when downloading', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(new Blob(['video']), {
      status: 200,
      headers: { 'Content-Disposition': 'attachment; filename="o-segredo-do-resultado.mp4"' },
    }))

    const asset = await api.downloadClip(credentials, 'clip-id')

    expect(asset.filename).toBe('o-segredo-do-resultado.mp4')
    expect(asset.blob).toBeInstanceOf(Blob)
  })
})
