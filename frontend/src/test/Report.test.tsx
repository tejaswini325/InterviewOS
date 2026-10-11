import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Report from '../pages/Report'

const get = vi.fn()
vi.mock('../api', () => ({ api: { get: (...a: unknown[]) => get(...a), post: vi.fn() }, describeError: () => ({ message: 'boom' }) }))

const data = {
  mode: 'HR', aiUsed: false, overallScore: 66.67, questionCount: 3, answeredCount: 3, totalTimeSec: 90, hasPendingEvaluations: false,
  topics: [{ topic: 'JAVA', questions: 2, answered: 2, avgScore: 100, correct: 2 }, { topic: 'OS', questions: 1, answered: 1, avgScore: 0, correct: 0 }],
  difficulties: [{ difficulty: 'MEDIUM', questions: 3, avgScore: 66.67 }], strongestTopic: 'JAVA', weakestTopic: 'OS',
  answers: [
    { position: 1, topic: 'JAVA', subtopic: 'Strings', difficulty: 'EASY', type: 'MCQ', statement: 'Q1', answered: true, selectedOption: 'B', correctAnswer: 'B: References', score: 1, correctness: 'CORRECT', strengths: [], missing: [], feedback: '', evaluator: 'MCQ_KEY', finalized: true, timeSpentSec: 20, timeEstimateSec: 60 },
    { position: 2, topic: 'JAVA', subtopic: 'GC', difficulty: 'MEDIUM', type: 'LONG', statement: 'Q2', answered: true, yourAnswer: 'x', score: 1, correctness: 'CORRECT', strengths: ['a'], missing: [], feedback: 'ok', evaluator: 'RUBRIC', finalized: true, timeSpentSec: 30, timeEstimateSec: 300 },
    { position: 3, topic: 'OS', subtopic: 'Deadlock', difficulty: 'MEDIUM', type: 'SHORT', statement: 'Name the Coffman conditions', answered: true, yourAnswer: 'mutex', score: 0, correctness: 'INCORRECT', strengths: [], missing: ['circular wait'], feedback: 'Revise these concepts', evaluator: 'RUBRIC', finalized: true, timeSpentSec: 40, timeEstimateSec: 200,
      evaluationKind: 'HEURISTIC', followUps: [{ role: 'INTERVIEWER', source: 'RULE_BASED', content: 'Can you name the other conditions?' }, { role: 'CANDIDATE', source: 'CANDIDATE', content: 'Hold and wait.' }] },
  ],
  difficultyHistory: [{ position: 1, difficulty: 'MEDIUM', reason: 'Baseline difficulty MEDIUM.' }, { position: 2, difficulty: 'MEDIUM', reason: 'Last answer 100%: difficulty held.' }],
  recommendations: [{ topic: 'OS', reason: 'Scored 0% across 1 question(s) in this interview.', subtopics: ['Deadlock'] }],
}
const base = { sessionId: 9, targetRole: 'BACKEND_DEVELOPER', sessionTopics: ['JAVA', 'OS'], startedAt: '2026-10-10T10:00:00Z' }

const renderPage = () => render(<MemoryRouter initialEntries={['/interviews/9/report']}><Routes><Route path="/interviews/:id/report" element={<Report />} /></Routes></MemoryRouter>)

describe('Report', () => {
  beforeEach(() => get.mockReset())

  it('renders measured results, review items and the adaptation history from backend data', async () => {
    get.mockResolvedValue({ data: { ...base, status: 'READY', data } })
    renderPage()
    expect(await screen.findByTestId('overall')).toHaveTextContent('67%')
    const measured = screen.getByLabelText('Measured results')
    expect(within(measured).getByText('3/3')).toBeInTheDocument()
    expect(within(measured).getByText('Name the Coffman conditions')).toBeInTheDocument()
    expect(within(measured).getByText(/circular wait/)).toBeInTheDocument()
    expect(within(measured).getByText('Questions to review (1)')).toBeInTheDocument() // only the incorrect answer is listed
    expect(within(measured).getByText(/Baseline difficulty MEDIUM/)).toBeInTheDocument()
  })

  it('keeps recommendations and AI commentary visibly separate from measured data', async () => {
    get.mockResolvedValue({ data: { ...base, status: 'READY', data, aiSummary: { text: 'Strong on Java.', source: 'AI_GENERATED' } } })
    renderPage()
    await screen.findByTestId('overall')
    expect(within(screen.getByLabelText('Recommendations')).getByText('rule-based, not measured')).toBeInTheDocument()
    const ai = screen.getByLabelText('AI summary')
    expect(within(ai).getByText('AI-generated, not measured')).toBeInTheDocument()
    expect(ai).toHaveTextContent('Strong on Java.')
    expect(within(screen.getByLabelText('Measured results')).queryByText('Strong on Java.')).not.toBeInTheDocument()
  })

  it('does not invent an AI summary when none exists', async () => {
    get.mockResolvedValue({ data: { ...base, status: 'READY', data } })
    renderPage()
    await screen.findByTestId('overall')
    expect(screen.queryByLabelText('AI summary')).not.toBeInTheDocument()
    expect(screen.getByText('No AI summary')).toBeInTheDocument()
  })

  it('shows progress while the report job is running', async () => {
    get.mockResolvedValue({ data: { ...base, status: 'GENERATING' } })
    renderPage()
    expect(await screen.findByRole('status')).toHaveTextContent('Generating your report')
  })

  it('offers retry when generation failed', async () => {
    get.mockResolvedValue({ data: { ...base, status: 'FAILED' } })
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Report generation failed')
    expect(screen.getByRole('button', { name: 'Retry generation' })).toBeInTheDocument()
  })

  it('shows an error with retry when the request fails', async () => {
    get.mockRejectedValueOnce({ problem: 'network down' })
    get.mockResolvedValue({ data: { ...base, status: 'READY', data } })
    renderPage()
    expect(await screen.findByText('boom')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
    screen.getByRole('button', { name: 'Retry' }).click()
    expect(await screen.findByTestId('overall')).toBeInTheDocument()
  })

  it('labels how each score was produced and never calls a heuristic an AI evaluation', async () => {
    get.mockResolvedValue({ data: { ...base, status: 'READY', data } })
    renderPage()
    await screen.findByTestId('overall')
    expect(screen.getByText('heuristic score')).toHaveAttribute('title', expect.stringContaining('does not understand meaning'))
    expect(screen.queryByText('AI-assisted score')).not.toBeInTheDocument()
    expect(screen.getByTestId('ai-note')).toHaveTextContent('No AI was used for this report')
  })

  it('shows follow-up questions with their true source', async () => {
    get.mockResolvedValue({ data: { ...base, status: 'READY', data } })
    renderPage()
    await screen.findByTestId('overall')
    expect(screen.getByText(/Follow-up \(rule-based\):/)).toBeInTheDocument()
    expect(screen.getByText('Can you name the other conditions?')).toBeInTheDocument()
    expect(screen.getByText(/Your reply:/)).toBeInTheDocument()
  })

  it('discloses AI involvement when the report used it', async () => {
    const ai = { ...data, aiUsed: true, answers: data.answers.map((a, i) => (i === 2 ? { ...a, evaluationKind: 'AI_ASSISTED' } : a)) }
    get.mockResolvedValue({ data: { ...base, status: 'READY', data: ai } })
    renderPage()
    await screen.findByTestId('overall')
    expect(screen.getByTestId('ai-note')).toHaveTextContent('AI assistance (Groq) contributed')
    expect(screen.getByText('AI-assisted score')).toBeInTheDocument()
  })
})
