import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }

describe('Clipador app', () => {
  it('authenticates and opens the studio without persisting the password', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(emptyPage), { status: 200 })))
    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText('Senha'), 'local-secret')
    await user.click(screen.getByRole('button', { name: 'Entrar no estúdio' }))
    expect(await screen.findByText('Transforme conteúdo em clipes.')).toBeInTheDocument()
    expect(screen.getByText('Novo processamento')).toBeInTheDocument()
  })
})
