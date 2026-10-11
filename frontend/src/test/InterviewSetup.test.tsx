import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InterviewSetup from '../pages/InterviewSetup'

const get = vi.fn(); const post = vi.fn()
vi.mock('../api', () => ({
  api: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) },
  describeError: (e: { problem?: unknown }) => e.problem ?? { message: 'boom' },
}))

const topics = { codingEnabled: false, topics: [
  { topic: 'JAVA', total: 6, byDifficulty: { EASY: 2, MEDIUM: 3, HARD: 1 } },
  { topic: 'OS', total: 5, byDifficulty: { EASY: 1, MEDIUM: 3, HARD: 1 } },
  { topic: 'HR', total: 6, byDifficulty: { EASY: 2, MEDIUM: 3, HARD: 1 } },
  { topic: 'ML', total: 8, byDifficulty: { EASY: 2, MEDIUM: 2, HARD: 4 } },
] }

const renderPage = (url = '/interviews/new') => render(
  <MemoryRouter initialEntries={[url]}>
    <Routes><Route path="/interviews/new" element={<InterviewSetup />} /><Route path="/interviews/:id/live" element={<div>LIVE WORKSPACE</div>} /></Routes>
  </MemoryRouter>)

describe('InterviewSetup', () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset()
    get.mockImplementation((url: string) => url === '/resume'
      ? Promise.resolve({ data: { uploaded: false } })
      : Promise.resolve({ data: topics }))
  })

  it('loads topics from the backend and blocks submission until one is chosen', async () => {
    renderPage()
    expect(await screen.findByLabelText('Java')).toBeInTheDocument()
    expect(screen.getByText(/6 questions/)).toBeInTheDocument()
    expect(screen.getByText('Select at least one topic.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start interview' })).toBeDisabled()
  })

  it('refuses a question count the selected topics cannot satisfy', async () => {
    renderPage()
    await userEvent.click(await screen.findByLabelText('Java'))
    const count = screen.getByLabelText('Number of questions')
    await userEvent.clear(count); await userEvent.type(count, '20')
    expect(screen.getByText(/Only 6 question\(s\) are available/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start interview' })).toBeDisabled()
  })

  it('posts the configuration and opens the workspace', async () => {
    post.mockResolvedValue({ data: { id: 42 } })
    renderPage()
    await userEvent.click(await screen.findByLabelText('Java'))
    await userEvent.click(screen.getByLabelText('Operating systems'))
    await userEvent.selectOptions(screen.getByLabelText('Starting difficulty'), 'HARD')
    await userEvent.click(screen.getByRole('button', { name: 'Start interview' }))
    await waitFor(() => expect(screen.getByText('LIVE WORKSPACE')).toBeInTheDocument())
    expect(post).toHaveBeenCalledWith('/interviews', { targetRole: 'SOFTWARE_ENGINEER', mode: 'TECHNICAL', topics: ['JAVA', 'OS'], difficulty: 'HARD', durationMinutes: 30, questionCount: 8 })
  })

  it('offers to resume when an interview is already active', async () => {
    post.mockRejectedValue({ problem: { code: 'ACTIVE_SESSION_EXISTS', message: 'You already have an interview in progress.', extra: { sessionId: 7 } } })
    renderPage()
    await userEvent.click(await screen.findByLabelText('Java'))
    const count = screen.getByLabelText('Number of questions')
    await userEvent.clear(count); await userEvent.type(count, '5')
    await userEvent.click(screen.getByRole('button', { name: 'Start interview' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('already have an interview')
    expect(screen.getByRole('link', { name: 'Resume it' })).toHaveAttribute('href', '/interviews/7/live')
  })

  it('shows a retry control when topics fail to load', async () => {
    get.mockRejectedValueOnce({ problem: { message: 'Cannot reach the server.' } })
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server.')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByLabelText('Java')).toBeInTheDocument()
  })

  it('shows only the topics that belong to the chosen mode and preselects single-topic modes', async () => {
    renderPage()
    await screen.findByLabelText('Java')
    expect(screen.queryByLabelText('Behavioural / HR')).not.toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('HR / behavioural mode'))
    expect(screen.queryByLabelText('Java')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Behavioural / HR')).toBeChecked()
    expect(screen.getByLabelText('Number of questions')).toHaveValue(6)   // clamped to what the HR bank can supply
    expect(screen.getByRole('button', { name: 'Start interview' })).toBeEnabled()
  })

  it('starts an AI/ML interview with the mode in the request', async () => {
    post.mockResolvedValue({ data: { id: 3 } })
    renderPage()
    await userEvent.click(await screen.findByLabelText('AI / ML mode'))
    await userEvent.click(screen.getByRole('button', { name: 'Start interview' }))
    await waitFor(() => expect(screen.getByText('LIVE WORKSPACE')).toBeInTheDocument())
    expect(post).toHaveBeenCalledWith('/interviews', expect.objectContaining({ mode: 'AI_ML', topics: ['ML'] }))
  })

  it('uploads a resume before starting the personalized resume and role interview', async () => {
    post.mockResolvedValueOnce({ data: { uploaded: true, filename: 'resume.pdf' } })
      .mockResolvedValueOnce({ data: { id: 12 } })
    renderPage()
    await userEvent.click(await screen.findByLabelText('Resume + technical mode'))
    expect(screen.getByText(/sent to Groq/)).toBeInTheDocument()
    const resume = new File(['A detailed resume with Java and backend project experience.'], 'resume.pdf', { type: 'application/pdf' })
    await userEvent.upload(screen.getByLabelText('Choose resume'), resume)
    expect(await screen.findByText('resume.pdf')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Start interview' }))
    await waitFor(() => expect(screen.getByText('LIVE WORKSPACE')).toBeInTheDocument())
    expect(post).toHaveBeenLastCalledWith('/interviews', expect.objectContaining({
      mode: 'RESUME', targetRole: 'SOFTWARE_ENGINEER', questionCount: 8,
    }))
  })

  it('prefills mode, topic and difficulty from a practice suggestion link', async () => {
    renderPage('/interviews/new?mode=AI_ML&topics=ML&difficulty=EASY')
    expect(await screen.findByLabelText('AI / ML')).toBeChecked()
    expect(screen.getByLabelText('AI / ML mode')).toBeChecked()
    expect(screen.getByLabelText('Starting difficulty')).toHaveValue('EASY')
  })
})
