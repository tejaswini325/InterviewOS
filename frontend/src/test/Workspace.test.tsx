import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Workspace from '../pages/Workspace'

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
vi.mock('../api', () => ({
  api: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) },
  describeError: (e: { problem?: unknown }) => e.problem ?? { message: 'boom' },
}))

const mcq = { interviewQuestionId: 11, questionId: 101, bookmarked: false, position: 1, topic: 'JAVA', subtopic: 'Strings', difficulty: 'MEDIUM', type: 'MCQ',
  statement: 'What does == compare for objects?', options: [{ id: 'A', text: 'Values' }, { id: 'B', text: 'References' }], sampleTests: null, tags: [], timeEstimateSec: 60, presentedAt: '' }
const open = { ...mcq, interviewQuestionId: 12, questionId: 102, position: 2, topic: 'HR', subtopic: 'Teamwork', difficulty: 'HARD', type: 'LONG', statement: 'Tell me about a disagreement with a teammate.', options: null }
const session = { id: 5, status: 'IN_PROGRESS', targetRole: 'BACKEND_DEVELOPER', mode: 'TECHNICAL', topics: ['JAVA'], baseDifficulty: 'MEDIUM', currentDifficulty: 'MEDIUM',
  durationMinutes: 30, questionCount: 3, answeredCount: 0, startedAt: '', deadlineAt: '', remainingSeconds: 1500, currentQuestion: mcq, pendingFollowUp: null, aiAvailable: false }

const q = (m: { interviewQuestionId: number; statement: string; position: number }, position = m.position) => ({ role: 'INTERVIEWER', kind: 'QUESTION', source: 'QUESTION_BANK', content: m.statement, position, interviewQuestionId: m.interviewQuestionId })
let transcript: unknown[]
let sess: unknown

const renderPage = () => render(
  <MemoryRouter initialEntries={['/interviews/5/live']}>
    <Routes><Route path="/interviews/:id/live" element={<Workspace />} /><Route path="/interviews/:id/report" element={<div>REPORT PAGE</div>} /></Routes>
  </MemoryRouter>)

describe('Workspace (conversational interview room)', () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); del.mockReset()
    transcript = [q(mcq)]; sess = session
    get.mockImplementation((url: string) => Promise.resolve({ data: url.endsWith('/transcript') ? transcript : sess }))
  })

  it('shows the interviewer message, source label, mode, timer and progress', async () => {
    renderPage()
    const chat = await screen.findByLabelText('Conversation')
    expect(within(chat).getByText('What does == compare for objects?')).toBeInTheDocument()
    expect(within(chat).getByText('Interviewer')).toBeInTheDocument()
    expect(within(chat).getByText('Question bank')).toBeInTheDocument()
    expect(screen.getByText('Technical interview')).toBeInTheDocument()
    expect(screen.getByText(/Question 1 of 3/)).toBeInTheDocument()
    expect(screen.getByTestId('difficulty')).toHaveTextContent('MEDIUM')
    expect(screen.getByLabelText('time remaining')).toHaveTextContent('25:00')
  })

  it('states plainly that follow-ups are rule-based when AI is not configured', async () => {
    renderPage()
    expect(await screen.findByTestId('engine-note')).toHaveTextContent('AI is not configured: follow-up questions are rule-based')
  })

  it('says follow-ups may be AI-generated only when AI is available', async () => {
    sess = { ...session, aiAvailable: true }
    renderPage()
    expect(await screen.findByTestId('engine-note')).toHaveTextContent('may be AI-generated (Groq)')
  })

  it('submits an MCQ answer and moves on to the next adapted question', async () => {
    post.mockImplementation((url: string) => {
      if (url.endsWith('/answers')) return Promise.resolve({ data: { answerId: 1, answeredCount: 1, questionCount: 3, followUp: null } })
      transcript = [q(mcq), { role: 'CANDIDATE', kind: 'ANSWER', source: 'CANDIDATE', content: 'B. References', position: 1, interviewQuestionId: 11 }, q(open)]
      return Promise.resolve({ data: { done: false, question: open, answeredCount: 1, questionCount: 3 } })
    })
    renderPage()
    const submit = await screen.findByRole('button', { name: 'Submit answer' })
    expect(submit).toBeDisabled()
    await userEvent.click(screen.getByLabelText(/References/))
    await userEvent.click(submit)
    expect(await screen.findByText('Tell me about a disagreement with a teammate.')).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('/interviews/5/answers', expect.objectContaining({ interviewQuestionId: 11, selectedOption: 'B', skipped: false }))
    expect(screen.getByText(/Question 2 of 3/)).toBeInTheDocument()
    expect(screen.getByTestId('difficulty')).toHaveTextContent('HARD')
    expect(screen.getByLabelText('Your answer')).toHaveValue('')
  })

  it('shows a rule-based follow-up labelled as such, then sends the reply and continues', async () => {
    sess = { ...session, currentQuestion: open }
    transcript = [q(open)]
    post.mockImplementation((url: string) => {
      if (url.endsWith('/answers')) {
        transcript = [q(open), { role: 'CANDIDATE', kind: 'ANSWER', source: 'CANDIDATE', content: 'It went fine.', position: 2, interviewQuestionId: 12 },
          { role: 'INTERVIEWER', kind: 'FOLLOW_UP', source: 'RULE_BASED', content: 'What exactly did you do to resolve it, personally?', position: 2, interviewQuestionId: 12 }]
        return Promise.resolve({ data: { answerId: 2, answeredCount: 1, questionCount: 3, followUp: { id: 9, interviewQuestionId: 12, kind: 'FOLLOW_UP', source: 'RULE_BASED', content: 'What exactly did you do to resolve it, personally?' } } })
      }
      if (url.endsWith('/follow-up')) return Promise.resolve({ data: { id: 10 } })
      transcript = [...transcript, q({ ...mcq, interviewQuestionId: 13, position: 3 })]
      return Promise.resolve({ data: { done: false, question: { ...mcq, interviewQuestionId: 13, position: 3 }, answeredCount: 1, questionCount: 3 } })
    })
    renderPage()
    await userEvent.type(await screen.findByLabelText('Your answer'), 'It went fine.')
    await userEvent.click(screen.getByRole('button', { name: 'Submit answer' }))

    const chat = screen.getByLabelText('Conversation')
    expect(await within(chat).findByText('What exactly did you do to resolve it, personally?')).toBeInTheDocument()
    expect(within(chat).getByText('Rule-based follow-up')).toBeInTheDocument()
    expect(within(chat).queryByText(/AI-generated/)).not.toBeInTheDocument()   // never claim a rule-based question is AI

    await userEvent.type(screen.getByLabelText('Your reply to the follow-up'), 'I proposed a plan.')
    await userEvent.click(screen.getByRole('button', { name: 'Send reply' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/interviews/5/follow-up', { interviewQuestionId: 12, answerText: 'I proposed a plan.', skipped: false }))
    expect(await screen.findByText('What does == compare for objects?')).toBeInTheDocument()
  })

  it('labels an AI-generated follow-up as AI', async () => {
    sess = { ...session, currentQuestion: null, pendingFollowUp: { id: 9, interviewQuestionId: 12, kind: 'FOLLOW_UP', source: 'AI', content: 'How would you have handled it under a tighter deadline?' }, aiAvailable: true }
    transcript = [q(open), { role: 'CANDIDATE', kind: 'ANSWER', source: 'CANDIDATE', content: 'We talked.', position: 2, interviewQuestionId: 12 },
      { role: 'INTERVIEWER', kind: 'FOLLOW_UP', source: 'AI', content: 'How would you have handled it under a tighter deadline?', position: 2, interviewQuestionId: 12 }]
    renderPage()   // resume with a pending follow-up
    const chat = await screen.findByLabelText('Conversation')
    expect(within(chat).getByText('AI-generated (Groq)')).toBeInTheDocument()
    expect(screen.getByLabelText('Your reply to the follow-up')).toBeInTheDocument()
  })

  it('lets the candidate skip a follow-up', async () => {
    sess = { ...session, currentQuestion: null, pendingFollowUp: { id: 9, interviewQuestionId: 12, kind: 'FOLLOW_UP', source: 'RULE_BASED', content: 'Can you say more?' } }
    transcript = [q(open)]
    post.mockImplementation((url: string) => url.endsWith('/follow-up') ? Promise.resolve({ data: {} })
      : Promise.resolve({ data: { done: false, question: mcq, answeredCount: 1, questionCount: 3 } }))
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Skip follow-up' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/interviews/5/follow-up', expect.objectContaining({ skipped: true })))
  })

  it('keeps the answer and offers a retry when submission fails', async () => {
    post.mockRejectedValueOnce({ problem: { message: 'Cannot reach the server.' } })
    renderPage()
    await userEvent.click(await screen.findByLabelText(/Values/))
    await userEvent.click(screen.getByRole('button', { name: 'Submit answer' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server.')
    expect(screen.getByLabelText(/Values/)).toBeChecked()
    post.mockImplementation((url: string) => {
      if (url.endsWith('/answers')) return Promise.resolve({ data: { answeredCount: 1, questionCount: 3, followUp: null } })
      transcript = [...transcript, q(open)]
      return Promise.resolve({ data: { done: false, question: open, answeredCount: 1, questionCount: 3 } })
    })
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Tell me about a disagreement with a teammate.')).toBeInTheDocument()
  })

  it('bookmarks and un-bookmarks the current question', async () => {
    post.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Bookmark question' }))
    expect(post).toHaveBeenCalledWith('/bookmarks/101')
    await userEvent.click(await screen.findByRole('button', { name: 'Bookmarked' }))
    expect(del).toHaveBeenCalledWith('/bookmarks/101')
  })

  it('offers speech only where the browser supports it (text is the default)', async () => {
    renderPage()
    await screen.findByLabelText('Conversation')
    expect(screen.queryByRole('button', { name: /Read aloud/ })).not.toBeInTheDocument()   // jsdom has no speechSynthesis
    expect(screen.queryByRole('button', { name: 'Dictate' })).not.toBeInTheDocument()
  })

  it('asks for confirmation before finishing and then opens the report', async () => {
    post.mockResolvedValue({ data: {} })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Finish interview' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Unanswered questions will count as zero')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(post).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Finish interview' }))
    await userEvent.click(screen.getByRole('button', { name: 'Finish and see report' }))
    await waitFor(() => expect(screen.getByText('REPORT PAGE')).toBeInTheDocument())
    expect(post).toHaveBeenCalledWith('/interviews/5/complete')
  })

  it('completes automatically when the server says there are no more questions', async () => {
    post.mockImplementation((url: string) => url.endsWith('/answers') ? Promise.resolve({ data: { answeredCount: 3, questionCount: 3, followUp: null } })
      : url.endsWith('/next-question') ? Promise.resolve({ data: { done: true, doneReason: 'ALL_ANSWERED', answeredCount: 3, questionCount: 3 } }) : Promise.resolve({ data: {} }))
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Skip' }))
    await waitFor(() => expect(screen.getByText('REPORT PAGE')).toBeInTheDocument())
  })

  it('redirects to the report when the interview is already completed', async () => {
    sess = { ...session, status: 'COMPLETED' }
    renderPage()
    expect(await screen.findByText('REPORT PAGE')).toBeInTheDocument()
  })
})
