import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, describeError } from '../api'
import { useAuth } from '../auth'
import { EmptyState, ErrorState, Spinner, Stat, fmtDate, fmtScore } from '../components/ui'
import type { Overview, Page, PracticeSuggestion, SessionSummary } from '../types'
import { MODE_INFO } from '../types'

export default function Dashboard() {
  const { user } = useAuth()
  const [overview, setOverview] = useState<Overview | null>(null)
  const [recent, setRecent] = useState<SessionSummary[]>([])
  const [suggestions, setSuggestions] = useState<PracticeSuggestion[]>([])
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [o, h, s] = await Promise.all([api.get<Overview>('/analytics/overview'), api.get<Page<SessionSummary>>('/interviews', { params: { size: 5 } }),
        api.get<PracticeSuggestion[]>('/practice/suggestions')])
      setOverview(o.data); setRecent(h.data.content); setSuggestions(s.data)
    } catch (e) { setError(describeError(e).message) }
  }, [])
  useEffect(() => { void load() }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!overview) return <Spinner />
  const active = recent.find((s) => s.status === 'IN_PROGRESS')

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div><h1 className="text-2xl font-semibold">Welcome back, {user?.displayName}</h1><p className="text-sm text-slate-400">Your interview practice at a glance.</p></div>
        <Link to="/interviews/new" className="btn-primary no-underline">Start an interview</Link>
      </div>
      {active && (
        <div className="card flex flex-wrap items-center justify-between gap-3 border-accent/40">
          <p className="text-sm">You have an interview in progress ({active.answeredCount}/{active.questionCount} answered).</p>
          <Link to={`/interviews/${active.id}/live`} className="btn-primary no-underline">Resume</Link>
        </div>
      )}
      <div className="card">
        <div className="mb-2 flex items-center justify-between text-sm"><h2 className="font-medium">Weekly practice goal</h2>
          <span className="text-slate-400">{overview.completedThisWeek} of {overview.weeklyGoal} interviews this week</span></div>
        <div className="h-2 overflow-hidden rounded-full bg-ink-700" role="progressbar" aria-label="Weekly goal" aria-valuemin={0} aria-valuemax={overview.weeklyGoal} aria-valuenow={Math.min(overview.completedThisWeek, overview.weeklyGoal)}>
          <div className="h-full bg-emerald-400" style={{ width: `${Math.min(100, (overview.completedThisWeek / Math.max(1, overview.weeklyGoal)) * 100)}%` }} /></div>
        <p className="mt-2 text-xs text-slate-500">Change the goal on your profile page.</p>
      </div>
      <div>
        <h2 className="mb-3 font-medium">Recommended practice</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          {suggestions.map((s) => (
            <div key={s.title} className="card flex flex-col justify-between gap-3">
              <div><div className="mb-1 flex items-center gap-2"><span className="font-medium">{s.title}</span>
                <span className={`badge ${s.basis === 'MEASURED' ? 'border-emerald-500/40 text-emerald-300' : 'border-ink-600 text-slate-400'}`}>{s.basis === 'MEASURED' ? 'from your scores' : s.basis === 'BOOKMARKS' ? 'from your bookmarks' : 'new topic'}</span></div>
                <p className="text-sm text-slate-400">{s.reason}</p></div>
              <Link className="btn-ghost no-underline" to={`/interviews/new?mode=${s.mode}&topics=${s.topics.join(',')}&difficulty=${s.difficulty}`}>Start {MODE_INFO[s.mode].label} practice</Link>
            </div>))}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-4">
        <Stat label="Interviews" value={overview.total} />
        <Stat label="Completed" value={overview.completed} />
        <Stat label="Average score" value={fmtScore(overview.averageScore)} />
        <Stat label="Best score" value={fmtScore(overview.bestScore)} />
      </div>
      <div className="card">
        <h2 className="mb-4 font-medium">Score trend</h2>
        {overview.trend.length === 0 ? <EmptyState title="No completed interviews yet" hint="Finish an interview to see your trend." /> : (
          <div className="h-64"><ResponsiveContainer>
            <LineChart data={overview.trend.map((t, i) => ({ n: i + 1, score: t.overallScore }))}>
              <CartesianGrid stroke="#232a38" /><XAxis dataKey="n" stroke="#64748b" /><YAxis domain={[0, 100]} stroke="#64748b" />
              <Tooltip contentStyle={{ background: '#11151d', border: '1px solid #2f3849' }} /><Line dataKey="score" stroke="#7c8cff" strokeWidth={2} dot />
            </LineChart></ResponsiveContainer></div>
        )}
      </div>
      <div className="card">
        <h2 className="mb-3 font-medium">Recent interviews</h2>
        {recent.length === 0 ? <EmptyState title="Nothing here yet" /> : (
          <ul className="divide-y divide-ink-700 text-sm">
            {recent.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                <span>{MODE_INFO[s.mode ?? 'TECHNICAL'].label} · {fmtDate(s.startedAt)}</span>
                <span className="text-slate-400">{s.status === 'COMPLETED' ? fmtScore(s.overallScore) : s.status.replace('_', ' ').toLowerCase()}</span>
                <Link to={s.status === 'IN_PROGRESS' ? `/interviews/${s.id}/live` : `/interviews/${s.id}/report`}>{s.status === 'IN_PROGRESS' ? 'Resume' : 'Report'}</Link>
              </li>))}
          </ul>)}
      </div>
    </div>
  )
}
