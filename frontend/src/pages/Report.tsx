import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, describeError } from '../api'
import { DifficultyBadge, EmptyState, ErrorState, Spinner, Stat, fmtScore } from '../components/ui'
import type { JobView, ReportView } from '../types'

const KIND: Record<string, { text: string; cls: string; hint: string }> = {
  ANSWER_KEY: { text: 'answer key', cls: 'border-emerald-500/40 text-emerald-300', hint: 'Compared against the correct option.' },
  HEURISTIC: { text: 'heuristic score', cls: 'border-amber-500/40 text-amber-300', hint: 'Keyword and expected-concept matching. It does not understand meaning.' },
  AI_ASSISTED: { text: 'AI-assisted score', cls: 'border-violet-500/40 text-violet-300', hint: 'Heuristic score blended 50/50 with an AI assessment.' },
}
const SRC: Record<string, string> = { AI: 'AI-generated', RULE_BASED: 'rule-based', CANDIDATE: 'you' }

const AXIS = '#64748b'
const tip = { contentStyle: { background: '#11151d', border: '1px solid #2f3849' } }

export default function Report() {
  const { id } = useParams()
  const [report, setReport] = useState<ReportView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tries, setTries] = useState(0)
  const [retrying, setRetrying] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try { setReport((await api.get<ReportView>(`/interviews/${id}/report`)).data) } catch (e) { setError(describeError(e).message) }
  }, [id])
  useEffect(() => { void load() }, [load])

  // The report is produced by a background job: poll while it is still being generated.
  useEffect(() => {
    if (report?.status !== 'GENERATING' || tries >= 30) return
    const t = setTimeout(() => { setTries((n) => n + 1); void load() }, 2000)
    return () => clearTimeout(t)
  }, [report, tries, load])

  const retryGeneration = async () => {
    setRetrying(true)
    try {
      const jobs = (await api.get<JobView[]>('/jobs', { params: { status: 'DEAD' } })).data
      const job = jobs.find((j) => j.kind === 'REPORT' && j.sessionId === Number(id))
      if (job) await api.post(`/jobs/${job.id}/retry`)
      setTries(0); await load()
    } catch (e) { setError(describeError(e).message) } finally { setRetrying(false) }
  }

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!report) return <Spinner />
  if (report.status === 'GENERATING') {
    return tries >= 30
      ? <ErrorState message="The report is taking longer than expected." onRetry={() => { setTries(0); void load() }} />
      : <Spinner label="Generating your report" />
  }
  if (report.status === 'FAILED' || !report.data) {
    return <div role="alert" className="card border-red-500/40"><p className="text-red-300">Report generation failed.</p>
      <button className="btn-primary mt-3" onClick={retryGeneration} disabled={retrying}>{retrying ? 'Retrying…' : 'Retry generation'}</button></div>
  }

  const d = report.data
  const missed = d.answers.filter((a) => a.correctness !== 'CORRECT')

  return (
    <div className="space-y-6">
      <div><Link to="/history" className="text-sm">← History</Link><h1 className="mt-1 text-2xl font-semibold">Interview report</h1></div>

      <p role="note" className={`card text-sm ${d.aiUsed ? 'border-violet-500/30 text-violet-200' : 'text-slate-400'}`} data-testid="ai-note">
        {d.aiUsed ? 'AI assistance (Groq) contributed to parts of this report. Those items are labelled.' : 'No AI was used for this report: scores come from answer keys and keyword/concept heuristics, and follow-ups were rule-based.'}
      </p>

      {d.hasPendingEvaluations && <p role="status" className="card border-amber-500/40 text-sm text-amber-300">Some answers still have provisional scores.</p>}

      <section aria-label="Measured results" className="space-y-4">
        <div className="flex items-center gap-2"><h2 className="text-lg font-medium">Measured results</h2><span className="badge border-emerald-500/40 text-emerald-300">computed from your answers</span></div>
        <div className="grid gap-4 sm:grid-cols-4">
          <Stat label="Overall score" value={<span data-testid="overall">{fmtScore(d.overallScore)}</span>} />
          <Stat label="Answered" value={`${d.answeredCount}/${d.questionCount}`} />
          <Stat label="Strongest topic" value={d.strongestTopic ?? '-'} />
          <Stat label="Weakest topic" value={d.weakestTopic ?? '-'} hint={d.weakestTopic ? undefined : 'Needs 2+ topics'} />
        </div>

        <div className="grid gap-4 lg:grid-cols-2">
          <div className="card"><h3 className="mb-3 text-sm font-medium">Score by topic (%)</h3>
            <div className="h-56"><ResponsiveContainer><BarChart data={d.topics}><CartesianGrid stroke="#232a38" /><XAxis dataKey="topic" stroke={AXIS} /><YAxis domain={[0, 100]} stroke={AXIS} /><Tooltip {...tip} /><Bar dataKey="avgScore" name="Score" fill="#7c8cff" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div></div>
          <div className="card"><h3 className="mb-3 text-sm font-medium">Accuracy by difficulty (%)</h3>
            <div className="h-56"><ResponsiveContainer><BarChart data={d.difficulties}><CartesianGrid stroke="#232a38" /><XAxis dataKey="difficulty" stroke={AXIS} /><YAxis domain={[0, 100]} stroke={AXIS} /><Tooltip {...tip} /><Bar dataKey="avgScore" name="Accuracy" fill="#34d399" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div></div>
        </div>

        <div className="card"><h3 className="mb-3 text-sm font-medium">Time per question (seconds, actual vs estimate)</h3>
          <div className="h-56"><ResponsiveContainer><BarChart data={d.answers.map((a) => ({ q: `Q${a.position}`, actual: a.timeSpentSec, estimate: a.timeEstimateSec }))}><CartesianGrid stroke="#232a38" /><XAxis dataKey="q" stroke={AXIS} /><YAxis stroke={AXIS} /><Tooltip {...tip} /><Legend /><Bar dataKey="actual" fill="#7c8cff" /><Bar dataKey="estimate" fill="#475569" /></BarChart></ResponsiveContainer></div></div>

        <div className="card">
          <h3 className="mb-3 text-sm font-medium">How difficulty adapted</h3>
          <ol className="space-y-3 text-sm">
            {d.difficultyHistory.map((h) => (
              <li key={h.position} className="flex gap-3"><span className="w-8 shrink-0 text-slate-500">Q{h.position}</span><DifficultyBadge difficulty={h.difficulty} /><span className="text-slate-400">{h.reason}</span></li>))}
          </ol>
        </div>

        <div className="card">
          <h3 className="mb-3 text-sm font-medium">Questions to review ({missed.length})</h3>
          {missed.length === 0 ? <p className="text-sm text-slate-400">Every question was answered correctly.</p> : (
            <ul className="space-y-4">
              {missed.map((a) => (
                <li key={a.position} className="rounded-lg border border-ink-700 p-4 text-sm">
                  <div className="mb-2 flex flex-wrap items-center gap-2"><span className="font-medium">Q{a.position}</span><span className="badge border-ink-600">{a.topic}</span><DifficultyBadge difficulty={a.difficulty} />
                    <span className="badge border-ink-600">{a.correctness.toLowerCase()}{a.score != null ? ` · ${Math.round(a.score * 100)}%` : ''}</span>
                    {a.evaluationKind && KIND[a.evaluationKind] && <span className={`badge ${KIND[a.evaluationKind].cls}`} title={KIND[a.evaluationKind].hint}>{KIND[a.evaluationKind].text}</span>}</div>
                  <p className="text-slate-100">{a.statement}</p>
                  <p className="mt-2 text-slate-400"><span className="text-slate-500">Your answer: </span>{a.answered ? (a.selectedOption ?? a.yourAnswer) : 'not answered'}</p>
                  {a.correctAnswer && <p className="text-slate-400"><span className="text-slate-500">Correct: </span>{a.correctAnswer}</p>}
                  {a.missing.length > 0 && <p className="text-slate-400"><span className="text-slate-500">Missing: </span>{a.missing.join('; ')}</p>}
                  {a.feedback && <p className="mt-1 text-slate-300">{a.feedback}</p>}
                  {a.followUps && a.followUps.length > 0 && (
                    <div className="mt-2 space-y-1 border-l-2 border-ink-600 pl-3 text-slate-400">
                      {a.followUps.map((f, i) => <p key={i}><span className="text-slate-500">{f.role === 'INTERVIEWER' ? `Follow-up (${SRC[f.source] ?? f.source}): ` : 'Your reply: '}</span>{f.content}</p>)}
                    </div>)}
                </li>))}
            </ul>)}
        </div>
      </section>

      <section aria-label="Recommendations" className="card">
        <div className="mb-3 flex items-center gap-2"><h2 className="text-lg font-medium">Revision recommendations</h2><span className="badge border-amber-500/40 text-amber-300">rule-based, not measured</span></div>
        {d.recommendations.length === 0 ? <p className="text-sm text-slate-400">No topic fell below 60% in this interview.</p> : (
          <ul className="space-y-2 text-sm">{d.recommendations.map((r) => (
            <li key={r.topic}><span className="font-medium">{r.topic}</span> <span className="text-slate-400">{r.reason}{r.subtopics.length > 0 && ` Revisit: ${r.subtopics.join(', ')}.`}</span></li>))}</ul>)}
      </section>

      {report.aiSummary && (
        <section aria-label="AI summary" className="card border-violet-500/30">
          <div className="mb-2 flex items-center gap-2"><h2 className="text-lg font-medium">AI summary</h2><span className="badge border-violet-500/40 text-violet-300">AI-generated, not measured</span></div>
          <p className="whitespace-pre-wrap text-sm text-slate-300">{report.aiSummary.text}</p>
        </section>)}
      {!report.aiSummary && <EmptyState title="No AI summary" hint="No AI provider is configured, so only measured results and rule-based recommendations are shown." />}
    </div>
  )
}
