import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, describeError } from '../api'
import { EmptyState, ErrorState, Spinner, fmtDate, fmtScore } from '../components/ui'
import type { InterviewMode, Page, SessionStatus, SessionSummary, TargetRole, Topic } from '../types'
import { MODE_INFO, ROLE_LABEL, TOPIC_LABEL } from '../types'

export default function History() {
  const [status, setStatus] = useState<SessionStatus | ''>('')
  const [role, setRole] = useState<TargetRole | ''>('')
  const [mode, setMode] = useState<InterviewMode | ''>('')
  const [topic, setTopic] = useState<Topic | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<SessionSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const params = Object.fromEntries(Object.entries({ status, role, mode, topic, from, to, page, size: 10 }).filter(([, v]) => v !== ''))
      setData((await api.get<Page<SessionSummary>>('/interviews', { params })).data)
    } catch (e) { setError(describeError(e).message) }
  }, [status, role, mode, topic, from, to, page])
  useEffect(() => { void load() }, [load])
  const filter = <T,>(set: (v: T) => void) => (v: T) => { setPage(0); set(v) }

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold">Interview history</h1>
      <div className="card grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <div><label htmlFor="f-status" className="label">Status</label><select id="f-status" className="input" value={status} onChange={(e) => filter(setStatus)(e.target.value as SessionStatus | '')}>
          <option value="">All</option><option value="COMPLETED">Completed</option><option value="IN_PROGRESS">In progress</option><option value="FAILED">Failed</option></select></div>
        <div><label htmlFor="f-role" className="label">Role</label><select id="f-role" className="input" value={role} onChange={(e) => filter(setRole)(e.target.value as TargetRole | '')}>
          <option value="">All</option>{(Object.keys(ROLE_LABEL) as TargetRole[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</select></div>
        <div><label htmlFor="f-mode" className="label">Mode</label><select id="f-mode" className="input" value={mode} onChange={(e) => filter(setMode)(e.target.value as InterviewMode | '')}>
          <option value="">All</option>{(Object.keys(MODE_INFO) as InterviewMode[]).map((m) => <option key={m} value={m}>{MODE_INFO[m].label}</option>)}</select></div>
        <div><label htmlFor="f-topic" className="label">Topic</label><select id="f-topic" className="input" value={topic} onChange={(e) => filter(setTopic)(e.target.value as Topic | '')}>
          <option value="">All</option>{(Object.keys(TOPIC_LABEL) as Topic[]).map((t) => <option key={t} value={t}>{TOPIC_LABEL[t]}</option>)}</select></div>
        <div><label htmlFor="f-from" className="label">From</label><input id="f-from" type="date" className="input" value={from} onChange={(e) => filter(setFrom)(e.target.value)} /></div>
        <div><label htmlFor="f-to" className="label">To</label><input id="f-to" type="date" className="input" value={to} onChange={(e) => filter(setTo)(e.target.value)} /></div>
      </div>

      {error ? <ErrorState message={error} onRetry={load} /> : !data ? <Spinner /> : data.content.length === 0 ? (
        <EmptyState title="No interviews match" hint="Adjust the filters or start a new interview." action={<Link to="/interviews/new" className="btn-primary no-underline">New interview</Link>} />
      ) : (
        <div className="card overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-ink-700 text-xs uppercase text-slate-400"><tr><th className="p-3">Date</th><th className="p-3">Mode</th><th className="p-3">Role</th><th className="p-3">Topics</th><th className="p-3">Answered</th><th className="p-3">Score</th><th className="p-3">Status</th><th className="p-3" /></tr></thead>
            <tbody className="divide-y divide-ink-700">
              {data.content.map((s) => (
                <tr key={s.id}>
                  <td className="p-3">{fmtDate(s.startedAt)}</td><td className="p-3">{MODE_INFO[s.mode ?? 'TECHNICAL'].label}</td><td className="p-3">{ROLE_LABEL[s.targetRole]}</td><td className="p-3">{s.topics.join(', ')}</td>
                  <td className="p-3">{s.answeredCount}/{s.questionCount}</td><td className="p-3">{fmtScore(s.overallScore)}</td>
                  <td className="p-3 capitalize">{s.status.replace('_', ' ').toLowerCase()}</td>
                  <td className="p-3"><Link to={s.status === 'IN_PROGRESS' ? `/interviews/${s.id}/live` : `/interviews/${s.id}/report`}>{s.status === 'IN_PROGRESS' ? 'Resume' : 'Report'}</Link></td>
                </tr>))}
            </tbody>
          </table>
        </div>)}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button className="btn-ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span className="text-slate-400">Page {data.page + 1} of {data.totalPages}</span>
          <button className="btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>)}
    </div>
  )
}
