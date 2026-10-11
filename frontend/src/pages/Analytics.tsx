import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, describeError } from '../api'
import { EmptyState, ErrorState, Spinner, fmtDate } from '../components/ui'
import type { Overview, SkillView } from '../types'

export default function Analytics() {
  const [overview, setOverview] = useState<Overview | null>(null)
  const [skills, setSkills] = useState<SkillView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const load = useCallback(async () => {
    setError(null)
    try {
      const [o, s] = await Promise.all([api.get<Overview>('/analytics/overview'), api.get<SkillView[]>('/analytics/skills')])
      setOverview(o.data); setSkills(s.data)
    } catch (e) { setError(describeError(e).message) }
  }, [])
  useEffect(() => { void load() }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!overview || !skills) return <Spinner />
  if (skills.length === 0) return <EmptyState title="No skill data yet" hint="Complete an interview and your topic scores will appear here." action={<Link to="/interviews/new" className="btn-primary no-underline">Start an interview</Link>} />

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Skill analytics</h1>
      <div className="card"><h2 className="mb-1 font-medium">Topic skill scores</h2>
        <p className="mb-4 text-xs text-slate-500">A moving average across your completed interviews (recent interviews weigh more). Measured, not a prediction.</p>
        <div className="h-64"><ResponsiveContainer><BarChart data={skills}><CartesianGrid stroke="#232a38" /><XAxis dataKey="topic" stroke="#64748b" /><YAxis domain={[0, 100]} stroke="#64748b" />
          <Tooltip contentStyle={{ background: '#11151d', border: '1px solid #2f3849' }} /><Bar dataKey="score" fill="#7c8cff" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div></div>
      <div className="card"><h2 className="mb-4 font-medium">Overall score per interview</h2>
        <div className="h-64"><ResponsiveContainer><LineChart data={overview.trend.map((t) => ({ date: fmtDate(t.date), score: t.overallScore }))}><CartesianGrid stroke="#232a38" /><XAxis dataKey="date" stroke="#64748b" /><YAxis domain={[0, 100]} stroke="#64748b" />
          <Tooltip contentStyle={{ background: '#11151d', border: '1px solid #2f3849' }} /><Line dataKey="score" stroke="#34d399" strokeWidth={2} /></LineChart></ResponsiveContainer></div></div>
      <div className="card overflow-x-auto p-0"><table className="w-full text-left text-sm"><thead className="border-b border-ink-700 text-xs uppercase text-slate-400"><tr><th className="p-3">Topic</th><th className="p-3">Score</th><th className="p-3">Questions answered</th><th className="p-3">Updated</th></tr></thead>
        <tbody className="divide-y divide-ink-700">{skills.map((s) => <tr key={s.topic}><td className="p-3">{s.topic}</td><td className="p-3">{Math.round(s.score)}%</td><td className="p-3">{s.attempts}</td><td className="p-3">{fmtDate(s.updatedAt)}</td></tr>)}</tbody></table></div>
    </div>
  )
}
