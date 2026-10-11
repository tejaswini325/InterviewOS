import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Dashboard from '../pages/Dashboard'

const get = vi.fn()
vi.mock('../api', () => ({ api: { get: (...a: unknown[]) => get(...a) }, describeError: () => ({ message: 'boom' }) }))
vi.mock('../auth', () => ({ useAuth: () => ({ user: { displayName: 'Asha' } }) }))

const overview = { total: 3, completed: 2, inProgress: 1, failed: 0, averageScore: 71, bestScore: 80, latestScore: 80,
  trend: [{ sessionId: 1, date: '2026-10-01T10:00:00Z', targetRole: 'BACKEND_DEVELOPER', overallScore: 62 }, { sessionId: 2, date: '2026-10-08T10:00:00Z', targetRole: 'BACKEND_DEVELOPER', overallScore: 80 }],
  completedThisWeek: 2, weeklyGoal: 4 }
const history = { content: [{ id: 9, status: 'IN_PROGRESS', targetRole: 'BACKEND_DEVELOPER', mode: 'HR', topics: ['HR'], baseDifficulty: 'MEDIUM', questionCount: 6, answeredCount: 2, startedAt: '2026-10-10T10:00:00Z' }], page: 0, size: 5, totalElements: 1, totalPages: 1 }
const suggestions = [
  { title: 'Strengthen OS', reason: 'Your measured OS score is 35% over 4 question(s).', basis: 'MEASURED', mode: 'TECHNICAL', topics: ['OS'], difficulty: 'EASY' },
  { title: 'Try ML', reason: 'You have not practised ML yet.', basis: 'NEW_TOPIC', mode: 'AI_ML', topics: ['ML'], difficulty: 'MEDIUM' },
]

const route = (data: Record<string, unknown>) => get.mockImplementation((url: string) => Promise.resolve({ data: data[url] }))
const renderPage = () => render(<MemoryRouter><Dashboard /></MemoryRouter>)

describe('Dashboard', () => {
  beforeEach(() => get.mockReset())

  it('shows weekly goal progress, a resume card and recommendations from backend data', async () => {
    route({ '/analytics/overview': overview, '/interviews': history, '/practice/suggestions': suggestions })
    renderPage()
    expect(await screen.findByText('2 of 4 interviews this week')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Weekly goal' })).toHaveAttribute('aria-valuenow', '2')
    expect(screen.getByText(/2\/6 answered/)).toBeInTheDocument()
    const rec = screen.getByText('Strengthen OS').closest('.card') as HTMLElement
    expect(within(rec).getByText('from your scores')).toBeInTheDocument()
    expect(within(rec).getByRole('link', { name: 'Start Technical practice' })).toHaveAttribute('href', '/interviews/new?mode=TECHNICAL&topics=OS&difficulty=EASY')
    expect(screen.getByText('new topic')).toBeInTheDocument()
  })

  it('shows empty states for a new user', async () => {
    route({ '/analytics/overview': { ...overview, total: 0, completed: 0, inProgress: 0, averageScore: null, bestScore: null, latestScore: null, trend: [], completedThisWeek: 0 },
      '/interviews': { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }, '/practice/suggestions': suggestions })
    renderPage()
    expect(await screen.findByText('No completed interviews yet')).toBeInTheDocument()
    expect(screen.getByText('Nothing here yet')).toBeInTheDocument()
  })

  it('shows an error with retry when the backend is unreachable', async () => {
    get.mockRejectedValueOnce({}).mockRejectedValueOnce({}).mockRejectedValueOnce({})
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
