import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, describeError } from '../api'
import { ErrorState, Spinner } from '../components/ui'
import type { Difficulty, InterviewMode, SessionView, TargetRole, Topic, TopicsResponse } from '../types'
import { MODE_INFO, ROLE_LABEL, TOPIC_LABEL } from '../types'

const MODES = Object.keys(MODE_INFO) as InterviewMode[]

export default function InterviewSetup() {
  const nav = useNavigate()
  const [params] = useSearchParams()   // deep links from dashboard suggestions: ?mode=&topics=&difficulty=
  const initialMode = (MODES.includes(params.get('mode') as InterviewMode) ? params.get('mode') : 'TECHNICAL') as InterviewMode
  const [data, setData] = useState<TopicsResponse | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [mode, setMode] = useState<InterviewMode>(initialMode)
  const [role, setRole] = useState<TargetRole>('SOFTWARE_ENGINEER')
  const [topics, setTopics] = useState<Topic[]>(() => {
    const fromUrl = (params.get('topics') ?? '').split(',').filter((t): t is Topic => MODE_INFO[initialMode].topics.includes(t as Topic))
    return fromUrl.length ? fromUrl
      : MODE_INFO[initialMode].topics.length === 1 ? MODE_INFO[initialMode].topics
      : initialMode === 'RESUME' ? MODE_INFO[initialMode].topics.filter((t) => t !== 'PROJECT') : []
  })
  const [difficulty, setDifficulty] = useState<Difficulty>((['EASY', 'MEDIUM', 'HARD'].includes(params.get('difficulty') ?? '') ? params.get('difficulty') : 'MEDIUM') as Difficulty)
  const [duration, setDuration] = useState(30)
  const [count, setCount] = useState(8)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeId, setActiveId] = useState<number | null>(null)
  const [resumeUploaded, setResumeUploaded] = useState(false)
  const [resumeFilename, setResumeFilename] = useState<string | null>(null)
  const [resumeBusy, setResumeBusy] = useState(false)
  const [resumeError, setResumeError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoadError(null)
    try { setData((await api.get<TopicsResponse>('/questions/topics')).data) } catch (e) { setLoadError(describeError(e).message) }
  }, [])
  const loadResume = useCallback(async () => {
    setResumeError(null)
    try {
      const { data: resume } = await api.get<{ uploaded: boolean; filename?: string | null }>('/resume')
      setResumeUploaded(resume.uploaded); setResumeFilename(resume.filename ?? null)
    } catch (e) { setResumeError(describeError(e).message) }
  }, [])
  useEffect(() => { void load() }, [load])
  useEffect(() => { if (initialMode === 'RESUME') void loadResume() }, [initialMode, loadResume])

  if (loadError) return <ErrorState message={loadError} onRetry={load} />
  if (!data) return <Spinner />

  const totalFor = (ts: Topic[]) => data.topics.filter((t) => ts.includes(t.topic)).reduce((n, t) => n + t.total, 0)
  const available = totalFor(topics)
  const toggle = (t: Topic) => setTopics((cur) => (cur.includes(t) ? cur.filter((x) => x !== t) : [...cur, t]))

  const pickMode = (m: InterviewMode) => {
    setMode(m)
    const ts = MODE_INFO[m].topics.length === 1 ? MODE_INFO[m].topics
      : m === 'RESUME' ? MODE_INFO[m].topics.filter((t) => t !== 'PROJECT') : []
    setTopics(ts)
    if (ts.length && m !== 'RESUME') setCount((c) => Math.max(3, Math.min(c, totalFor(ts) || c)))
    if (m === 'RESUME' && !resumeUploaded) void loadResume()
  }

  const uploadResume = async (file: File) => {
    setResumeBusy(true); setResumeError(null)
    const body = new FormData()
    body.append('file', file)
    try {
      const { data: resume } = await api.post<{ uploaded: boolean; filename?: string | null }>('/resume', body)
      setResumeUploaded(resume.uploaded); setResumeFilename(resume.filename ?? null)
    } catch (e) {
      setResumeError(describeError(e).message)
    } finally { setResumeBusy(false) }
  }

  const removeResume = async () => {
    setResumeBusy(true); setResumeError(null)
    try {
      await api.delete('/resume')
      setResumeUploaded(false); setResumeFilename(null)
    } catch (e) {
      setResumeError(describeError(e).message)
    } finally { setResumeBusy(false) }
  }

  const validation = topics.length === 0 ? 'Select at least one topic.'
    : mode === 'RESUME' && topics.every((t) => t === 'PROJECT') ? 'Select at least one technical topic.'
    : count < 3 || count > 30 ? 'Choose between 3 and 30 questions.'
    : mode !== 'RESUME' && count > available ? `Only ${available} question(s) are available for the selected topics.`
    : mode === 'RESUME' && !resumeUploaded ? 'Upload a PDF or DOCX resume to start this interview.'
    : duration < 5 || duration > 180 ? 'Choose a duration between 5 and 180 minutes.' : null

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (validation) return
    setSubmitting(true); setError(null); setActiveId(null)
    try {
      const r = await api.post<SessionView>('/interviews', { targetRole: role, mode, topics, difficulty, durationMinutes: duration, questionCount: count })
      nav(`/interviews/${r.data.id}/live`)
    } catch (err) {
      const p = describeError(err)
      if (p.code === 'ACTIVE_SESSION_EXISTS') setActiveId(Number(p.extra?.sessionId))
      setError(p.message)
    } finally { setSubmitting(false) }
  }

  const modeTopics = data.topics.filter((t) => MODE_INFO[mode].topics.includes(t.topic))

  return (
    <form onSubmit={submit} className="mx-auto max-w-3xl space-y-6">
      <div><h1 className="text-2xl font-semibold">New interview</h1><p className="text-sm text-slate-400">Choose a mode, then what to cover. Difficulty adapts to your answers as you go.</p></div>

      <fieldset className="card">
        <legend className="label">Interview mode</legend>
        <div role="radiogroup" aria-label="Interview mode" className="grid gap-3 sm:grid-cols-2">
          {MODES.map((m) => (
            <label key={m} className={`cursor-pointer rounded-lg border p-3 ${mode === m ? 'border-accent bg-accent-soft/30' : 'border-ink-600 hover:bg-ink-800'}`}>
              <input type="radio" name="mode" className="sr-only" checked={mode === m} onChange={() => pickMode(m)} aria-label={`${MODE_INFO[m].label} mode`} />
              <span className="block text-sm font-medium">{MODE_INFO[m].label}</span>
              <span className="text-xs text-slate-400">{MODE_INFO[m].blurb}</span>
            </label>))}
        </div>
      </fieldset>

      <fieldset className="card">
        <legend className="label">Topics</legend>
        <div className="grid gap-3 sm:grid-cols-2">
          {modeTopics.map((t) => (
            <label key={t.topic} className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 ${topics.includes(t.topic) ? 'border-accent bg-accent-soft/30' : 'border-ink-600'}`}>
              <input type="checkbox" className="mt-1" checked={topics.includes(t.topic)} onChange={() => toggle(t.topic)} aria-label={TOPIC_LABEL[t.topic]} />
              <span><span className="block text-sm font-medium">{TOPIC_LABEL[t.topic]}</span>
                <span className="text-xs text-slate-400">{t.total} questions · {t.byDifficulty.EASY}/{t.byDifficulty.MEDIUM}/{t.byDifficulty.HARD} easy/med/hard</span></span>
            </label>))}
        </div>
        {!data.codingEnabled && <p className="mt-3 text-xs text-slate-500">Coding questions are switched off: this server has no code sandbox, so nothing would actually be executed.</p>}
      </fieldset>

      {mode === 'RESUME' && (
        <fieldset className="card space-y-3">
          <legend className="label">Resume</legend>
          <p className="text-xs text-slate-400">
            Upload a PDF or DOCX (up to 5 MB). The extracted text is stored with your profile until replaced or removed.
            If Groq is configured, it is sent to Groq to generate personalized questions; otherwise local templates are used.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <label className="btn-ghost cursor-pointer">
              {resumeBusy ? 'Working…' : resumeUploaded ? 'Replace resume' : 'Choose resume'}
              <input type="file" className="sr-only" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                disabled={resumeBusy} onChange={(e) => {
                  const file = e.currentTarget.files?.[0]
                  if (file) void uploadResume(file)
                  e.currentTarget.value = ''
                }} />
            </label>
            {resumeFilename && <span className="text-sm text-slate-300">{resumeFilename}</span>}
            {resumeUploaded && <button type="button" className="btn-ghost" disabled={resumeBusy} onClick={() => void removeResume()}>Remove resume</button>}
          </div>
          {resumeError && <p role="alert" className="text-sm text-red-300">{resumeError}</p>}
        </fieldset>
      )}

      <div className="card grid gap-4 sm:grid-cols-2">
        <div><label htmlFor="role" className="label">Target role</label>
          <select id="role" className="input" value={role} onChange={(e) => setRole(e.target.value as TargetRole)}>
            {(Object.keys(ROLE_LABEL) as TargetRole[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</select></div>
        <div><label htmlFor="difficulty" className="label">Starting difficulty</label>
          <select id="difficulty" className="input" value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)}>
            <option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select></div>
        <div><label htmlFor="count" className="label">Number of questions</label>
          <input id="count" type="number" className="input" min={3} max={30} value={count} onChange={(e) => setCount(Number(e.target.value))} /></div>
        <div><label htmlFor="duration" className="label">Duration (minutes)</label>
          <input id="duration" type="number" className="input" min={5} max={180} value={duration} onChange={(e) => setDuration(Number(e.target.value))} /></div>
      </div>

      {validation && <p role="status" className="text-sm text-amber-300">{validation}</p>}
      {error && (
        <div role="alert" className="card border-red-500/40 text-sm text-red-300">{error}{' '}
          {activeId && <Link to={`/interviews/${activeId}/live`}>Resume it</Link>}</div>)}
      <button type="submit" className="btn-primary" disabled={submitting || validation !== null}>{submitting ? 'Starting…' : 'Start interview'}</button>
    </form>
  )
}
