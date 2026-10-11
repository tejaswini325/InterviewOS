import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { describeError, type ApiProblem } from '../api'

function Shell({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto flex min-h-screen max-w-sm flex-col justify-center px-4">
      <Link to="/" className="mb-6 text-sm text-slate-400 no-underline">← InterviewOS</Link>
      <div className="card"><h1 className="mb-5 text-xl font-semibold">{title}</h1>{children}</div>
    </div>
  )
}

function useSubmit(fn: () => Promise<void>) {
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState<ApiProblem | null>(null)
  const nav = useNavigate()
  return {
    busy, problem,
    onSubmit: async (e: FormEvent) => {
      e.preventDefault(); setBusy(true); setProblem(null)
      try { await fn(); nav('/dashboard') } catch (err) { setProblem(describeError(err)) } finally { setBusy(false) }
    },
  }
}

function Field({ id, label, error, ...rest }: { id: string; label: string; error?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return <div className="mb-4"><label htmlFor={id} className="label">{label}</label><input id={id} className="input" {...rest} />{error && <p className="mt-1 text-xs text-red-300">{error}</p>}</div>
}

export function Login() {
  const { login } = useAuth()
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('')
  const { busy, problem, onSubmit } = useSubmit(() => login(email, password))
  return (
    <Shell title="Log in">
      <form onSubmit={onSubmit}>
        <Field id="email" label="Email" type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field id="password" label="Password" type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        {problem && <p role="alert" className="mb-3 text-sm text-red-300">{problem.message}</p>}
        <button className="btn-primary w-full" disabled={busy}>{busy ? 'Signing in…' : 'Log in'}</button>
      </form>
      <p className="mt-4 text-sm text-slate-400">No account? <Link to="/register">Register</Link></p>
    </Shell>
  )
}

export function Register() {
  const { register } = useAuth()
  const [displayName, setName] = useState(''); const [email, setEmail] = useState(''); const [password, setPassword] = useState('')
  const { busy, problem, onSubmit } = useSubmit(() => register(email, password, displayName))
  return (
    <Shell title="Create your account">
      <form onSubmit={onSubmit}>
        <Field id="name" label="Name" required maxLength={100} value={displayName} onChange={(e) => setName(e.target.value)} error={problem?.fieldErrors?.displayName} />
        <Field id="email" label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} error={problem?.fieldErrors?.email} />
        <Field id="password" label="Password (10+ characters)" type="password" required minLength={10} maxLength={72} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} error={problem?.fieldErrors?.password} />
        {problem && !problem.fieldErrors && <p role="alert" className="mb-3 text-sm text-red-300">{problem.message}</p>}
        <button className="btn-primary w-full" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
      </form>
      <p className="mt-4 text-sm text-slate-400">Already registered? <Link to="/login">Log in</Link></p>
    </Shell>
  )
}
