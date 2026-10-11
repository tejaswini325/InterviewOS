import { Link } from 'react-router-dom'
import { useAuth } from '../auth'

const points = [
  ['Five interview modes', 'Technical, DSA, HR, project-based and AI/ML, each with its own question bank and follow-up questions.'],
  ['Works without AI', 'Scoring and follow-ups run on transparent rules. Optional Groq AI adds contextual follow-ups, and is always labelled.'],
  ['Honest reports', 'Heuristic scores, answer-key scores and AI assessments are marked as such, and kept apart from recommendations.'],
]

export default function Landing() {
  const { user } = useAuth()
  return (
    <div className="mx-auto max-w-4xl px-4 py-20">
      <p className="text-sm font-medium text-accent">Technical interview practice</p>
      <h1 className="mt-3 text-4xl font-semibold sm:text-5xl">Interview<span className="text-accent">OS</span></h1>
      <p className="mt-5 max-w-2xl text-lg text-slate-400">Practise a conversational interview that adapts to what you know: technical, DSA, HR, project and AI/ML rounds, with follow-up questions and a report that tells you exactly how each score was produced.</p>
      <div className="mt-8 flex gap-3">
        {user ? <Link to="/dashboard" className="btn-primary no-underline">Go to dashboard</Link> : (<>
          <Link to="/register" className="btn-primary no-underline">Create an account</Link>
          <Link to="/login" className="btn-ghost no-underline">Log in</Link></>)}
      </div>
      <div className="mt-16 grid gap-4 sm:grid-cols-3">
        {points.map(([t, d]) => <div key={t} className="card"><h3 className="font-medium">{t}</h3><p className="mt-2 text-sm text-slate-400">{d}</p></div>)}
      </div>
    </div>
  )
}
