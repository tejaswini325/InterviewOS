import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, describeError } from '../api'
import { ConfirmDialog, DifficultyBadge, ErrorState, Spinner, fmtClock } from '../components/ui'
import { speak, speechOutputSupported, stopSpeaking, useDictation } from '../speech'
import type { AnswerAck, NextQuestionResponse, QuestionView, SessionView, TranscriptMessage, TurnView } from '../types'
import { MODE_INFO, TOPIC_LABEL } from '../types'

const SOURCE_LABEL: Record<string, { text: string; cls: string }> = {
  QUESTION_BANK: { text: 'Question bank', cls: 'border-ink-600 text-slate-400' },
  RULE_BASED: { text: 'Rule-based follow-up', cls: 'border-amber-500/40 text-amber-300' },
  AI: { text: 'AI-generated (Groq)', cls: 'border-violet-500/40 text-violet-300' },
}

export default function Workspace() {
  const { id } = useParams()
  const nav = useNavigate()
  const [session, setSession] = useState<SessionView | null>(null)
  const [transcript, setTranscript] = useState<TranscriptMessage[]>([])
  const [question, setQuestion] = useState<QuestionView | null>(null)
  const [followUp, setFollowUp] = useState<TurnView | null>(null)
  const [bookmarked, setBookmarked] = useState(false)
  const [answered, setAnswered] = useState(0)
  const [text, setText] = useState('')
  const [option, setOption] = useState<string | null>(null)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmFinish, setConfirmFinish] = useState(false)
  const [readAloud, setReadAloud] = useState(false)
  const shownAt = useRef(Date.now())
  const finishing = useRef(false)
  const bottom = useRef<HTMLDivElement>(null)
  const spoken = useRef(0)

  const dictation = useDictation((t) => setText(t))
  const canSpeak = speechOutputSupported()

  const refreshTranscript = useCallback(async () => {
    try { setTranscript((await api.get<TranscriptMessage[]>(`/interviews/${id}/transcript`)).data) } catch { /* the chat view is a convenience: do not block the interview */ }
  }, [id])

  const finish = useCallback(async () => {
    if (finishing.current) return
    finishing.current = true
    try {
      await api.post(`/interviews/${id}/complete`)
      nav(`/interviews/${id}/report`, { replace: true })
    } catch (e) { finishing.current = false; setSubmitError(describeError(e).message) }
  }, [id, nav])

  const showQuestion = useCallback((q: QuestionView) => {
    setQuestion(q); setFollowUp(null); setText(''); setOption(null); setSubmitError(null); setBookmarked(q.bookmarked); shownAt.current = Date.now()
  }, [])

  const fetchNext = useCallback(async () => {
    const r = (await api.post<NextQuestionResponse>(`/interviews/${id}/next-question`)).data
    setAnswered(r.answeredCount)
    if (r.done || !r.question) { await finish(); return }
    showQuestion(r.question)
    await refreshTranscript()
  }, [id, finish, showQuestion, refreshTranscript])

  const load = useCallback(async () => {
    setLoadError(null)
    try {
      const s = (await api.get<SessionView>(`/interviews/${id}`)).data
      if (s.status !== 'IN_PROGRESS') { nav(`/interviews/${id}/report`, { replace: true }); return }
      setSession(s); setAnswered(s.answeredCount); setSecondsLeft(s.remainingSeconds)
      await refreshTranscript()
      if (s.currentQuestion) showQuestion(s.currentQuestion)
      else if (s.pendingFollowUp) { setQuestion(null); setFollowUp(s.pendingFollowUp); setText('') }
      else await fetchNext()
    } catch (e) { setLoadError(describeError(e).message) }
  }, [id, nav, showQuestion, fetchNext, refreshTranscript])
  useEffect(() => { void load() }, [load])

  useEffect(() => {
    if (!session) return
    const t = setInterval(() => setSecondsLeft((s) => Math.max(0, s - 1)), 1000)
    return () => clearInterval(t)
  }, [session])
  useEffect(() => { if (session && secondsLeft === 0) void finish() }, [session, secondsLeft, finish])

  useEffect(() => { bottom.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' }) }, [transcript, followUp])

  // optional read-aloud of new interviewer messages (text stays the default)
  useEffect(() => {
    if (!readAloud) { stopSpeaking(); spoken.current = transcript.length; return }
    const fresh = transcript.slice(spoken.current).filter((m) => m.role === 'INTERVIEWER')
    if (fresh.length) speak(fresh[fresh.length - 1].content)
    spoken.current = transcript.length
  }, [transcript, readAloud])

  const submitAnswer = async (skipped: boolean) => {
    if (!question) return
    dictation.stop(); setBusy(true); setSubmitError(null)
    try {
      const ack = (await api.post<AnswerAck>(`/interviews/${id}/answers`, {
        interviewQuestionId: question.interviewQuestionId, skipped,
        answerText: question.type === 'MCQ' ? undefined : text, selectedOption: question.type === 'MCQ' ? option ?? undefined : undefined,
        timeSpentSec: Math.round((Date.now() - shownAt.current) / 1000),
      })).data
      setAnswered(ack.answeredCount)
      if (ack.followUp) { setFollowUp(ack.followUp); setQuestion(null); setText(''); await refreshTranscript() }
      else await fetchNext()
    } catch (e) { setSubmitError(describeError(e).message) } finally { setBusy(false) }
  }

  const submitFollowUp = async (skipped: boolean) => {
    if (!followUp) return
    dictation.stop(); setBusy(true); setSubmitError(null)
    try {
      await api.post(`/interviews/${id}/follow-up`, { interviewQuestionId: followUp.interviewQuestionId, answerText: skipped ? undefined : text, skipped })
      await fetchNext()
    } catch (e) { setSubmitError(describeError(e).message) } finally { setBusy(false) }
  }

  const toggleBookmark = async () => {
    if (!question) return
    const next = !bookmarked
    setBookmarked(next)
    try { next ? await api.post(`/bookmarks/${question.questionId}`) : await api.delete(`/bookmarks/${question.questionId}`) }
    catch (e) { setBookmarked(!next); setSubmitError(describeError(e).message) }
  }

  if (loadError) return <ErrorState message={loadError} onRetry={load} />
  if (!session || (!question && !followUp)) return <Spinner label="Preparing your interview" />

  const total = session.questionCount
  const inFollowUp = followUp !== null && question === null
  const current = question
  const canSubmit = inFollowUp || current?.type !== 'MCQ' ? text.trim().length > 0 : option !== null
  const low = secondsLeft <= 60
  const position = current?.position ?? Math.max(1, answered)

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4">
      <div className="card flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="font-medium">{MODE_INFO[session.mode ?? 'TECHNICAL'].label} interview</span>
          <span className="text-slate-400">Question {Math.min(position, total)} of {total}</span>
          {current && <><span className="badge border-ink-600 text-slate-300">{TOPIC_LABEL[current.topic]} · {current.subtopic}</span><DifficultyBadge difficulty={current.difficulty} /></>}
          {inFollowUp && <span className="badge border-ink-600 text-slate-300">Follow-up</span>}
        </div>
        <div aria-label="time remaining" className={`font-mono text-lg ${low ? 'text-rose-300' : 'text-slate-100'}`}>{fmtClock(secondsLeft)}</div>
      </div>

      <div className="h-2 overflow-hidden rounded-full bg-ink-700" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={answered}>
        <div className="h-full bg-accent transition-all" style={{ width: `${(answered / total) * 100}%` }} />
      </div>
      <p className="text-xs text-slate-500" data-testid="engine-note">
        {session.aiAvailable ? 'Follow-up questions may be AI-generated (Groq); each message is labelled with its source.' : 'AI is not configured: follow-up questions are rule-based. Scores are keyword/concept heuristics.'}
      </p>

      <section aria-label="Conversation" className="card max-h-[45vh] min-h-48 space-y-4 overflow-y-auto">
        {transcript.map((m, i) => {
          const mine = m.role === 'CANDIDATE'
          const label = SOURCE_LABEL[m.source]
          return (
            <div key={i} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[85%] rounded-xl px-4 py-3 text-sm ${mine ? 'bg-accent-soft/50 text-slate-100' : 'border border-ink-700 bg-ink-800 text-slate-100'}`}>
                <div className="mb-1 flex items-center gap-2 text-xs text-slate-400">
                  <span>{mine ? 'You' : 'Interviewer'}</span>
                  {!mine && label && <span className={`badge ${label.cls}`}>{label.text}</span>}
                </div>
                <p className="whitespace-pre-wrap">{m.content}</p>
              </div>
            </div>)
        })}
        <div ref={bottom} />
      </section>

      <div className="card space-y-4">
        {current?.type === 'MCQ' && (
          <div role="radiogroup" aria-label="Answer options" className="space-y-2">
            {current.options?.map((o) => (
              <label key={o.id} className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm ${option === o.id ? 'border-accent bg-accent-soft/30' : 'border-ink-600 hover:bg-ink-800'}`}>
                <input type="radio" name="option" className="mt-0.5" checked={option === o.id} onChange={() => setOption(o.id)} />
                <span><span className="mr-2 font-mono text-slate-400">{o.id}.</span>{o.text}</span>
              </label>))}
          </div>)}

        {(inFollowUp || (current && current.type !== 'MCQ')) && (
          <div>
            <div className="mb-1 flex items-center justify-between">
              <label htmlFor="answer" className="label mb-0">{inFollowUp ? 'Your reply to the follow-up' : current?.type === 'CODING' ? 'Your code' : 'Your answer'}</label>
              {dictation.supported && (
                <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={dictation.listening ? dictation.stop : dictation.start} aria-pressed={dictation.listening}>
                  {dictation.listening ? 'Stop dictation' : 'Dictate'}
                </button>)}
            </div>
            <textarea id="answer" className={`input ${current?.type === 'CODING' ? 'font-mono' : ''}`} rows={inFollowUp || current?.type === 'SHORT' ? 4 : 8} maxLength={inFollowUp ? 5000 : 10000}
              value={text} onChange={(e) => setText(e.target.value)} placeholder="Type your answer. Full sentences and concrete examples score best." />
            <p className="mt-1 text-xs text-slate-500">{text.length} characters</p>
          </div>)}

        {current?.sampleTests && current.sampleTests.length > 0 && (
          <div><p className="label">Sample test cases</p>
            <ul className="space-y-1 font-mono text-xs text-slate-300">{current.sampleTests.map((t, i) => <li key={i}>input: {t.input} → expected: {t.expected}</li>)}</ul></div>)}

        {submitError && (
          <div role="alert" className="rounded-lg border border-red-500/40 p-3 text-sm text-red-300">
            {submitError}
            <button className="btn-ghost ml-3" onClick={() => (inFollowUp ? submitFollowUp(false) : submitAnswer(false))} disabled={busy || !canSubmit}>Retry</button>
          </div>)}

        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap gap-2">
            <button className="btn-danger" onClick={() => setConfirmFinish(true)} disabled={busy}>Finish interview</button>
            {current && <button className="btn-ghost" onClick={toggleBookmark} aria-pressed={bookmarked}>{bookmarked ? 'Bookmarked' : 'Bookmark question'}</button>}
            {canSpeak && <button className="btn-ghost" onClick={() => setReadAloud((v) => !v)} aria-pressed={readAloud}>{readAloud ? 'Read aloud: on' : 'Read aloud: off'}</button>}
          </div>
          <div className="flex gap-2">
            <button className="btn-ghost" onClick={() => (inFollowUp ? submitFollowUp(true) : submitAnswer(true))} disabled={busy}>{inFollowUp ? 'Skip follow-up' : 'Skip'}</button>
            <button className="btn-primary" onClick={() => (inFollowUp ? submitFollowUp(false) : submitAnswer(false))} disabled={busy || !canSubmit}>
              {busy ? 'Saving…' : inFollowUp ? 'Send reply' : 'Submit answer'}
            </button>
          </div>
        </div>
      </div>
      <p className="text-center text-xs text-slate-500">Questions adapt to your answers, so earlier questions cannot be revisited. Progress is saved: you can leave and resume.</p>

      <ConfirmDialog open={confirmFinish} title="Finish the interview now?" confirmLabel="Finish and see report"
        body="Unanswered questions will count as zero. This cannot be undone."
        onCancel={() => setConfirmFinish(false)} onConfirm={() => { setConfirmFinish(false); void finish() }} />
    </div>
  )
}
