import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, describeError } from '../api'
import { ErrorState, Spinner } from '../components/ui'
import type { ExperienceLevel, Profile, TargetRole, Topic } from '../types'
import { ROLE_LABEL, TOPIC_LABEL } from '../types'

export default function ProfilePage() {
  const [p, setP] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saveMsg, setSaveMsg] = useState<string | null>(null)
  const [saveErr, setSaveErr] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try { setP((await api.get<Profile>('/profile')).data) } catch (e) { setError(describeError(e).message) }
  }, [])
  useEffect(() => { void load() }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!p) return <Spinner />

  const save = async (e: FormEvent) => {
    e.preventDefault(); setSaving(true); setSaveMsg(null); setSaveErr(null)
    try {
      setP((await api.put<Profile>('/profile', { targetRole: p.targetRole, experienceLevel: p.experienceLevel, skillPreferences: p.skillPreferences, displayName: p.displayName, weeklyGoal: p.weeklyGoal })).data)
      setSaveMsg('Profile saved.')
    } catch (err) { setSaveErr(describeError(err).message) } finally { setSaving(false) }
  }
  const toggle = (t: Topic) => setP({ ...p, skillPreferences: p.skillPreferences.includes(t) ? p.skillPreferences.filter((x) => x !== t) : [...p.skillPreferences, t] })

  return (
    <form onSubmit={save} className="mx-auto max-w-2xl space-y-5">
      <h1 className="text-2xl font-semibold">Candidate profile</h1>
      <div className="card space-y-4">
        <div><label className="label" htmlFor="pe">Email</label><input id="pe" className="input" value={p.email} disabled /></div>
        <div><label className="label" htmlFor="pn">Display name</label><input id="pn" className="input" required maxLength={100} value={p.displayName} onChange={(e) => setP({ ...p, displayName: e.target.value })} /></div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div><label className="label" htmlFor="pr">Target role</label><select id="pr" className="input" value={p.targetRole} onChange={(e) => setP({ ...p, targetRole: e.target.value as TargetRole })}>
            {(Object.keys(ROLE_LABEL) as TargetRole[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</select></div>
          <div><label className="label" htmlFor="px">Experience level</label><select id="px" className="input" value={p.experienceLevel} onChange={(e) => setP({ ...p, experienceLevel: e.target.value as ExperienceLevel })}>
            {['FRESHER', 'JUNIOR', 'MID', 'SENIOR'].map((l) => <option key={l} value={l}>{l.charAt(0) + l.slice(1).toLowerCase()}</option>)}</select></div>
        </div>
        <div><label className="label" htmlFor="pg">Weekly practice goal (interviews)</label>
          <input id="pg" type="number" min={1} max={21} className="input max-w-32" value={p.weeklyGoal} onChange={(e) => setP({ ...p, weeklyGoal: Math.max(1, Math.min(21, Number(e.target.value) || 1)) })} /></div>
        <fieldset><legend className="label">Skill preferences</legend>
          <div className="flex flex-wrap gap-2">{(Object.keys(TOPIC_LABEL) as Topic[]).map((t) => (
            <label key={t} className={`badge cursor-pointer px-3 py-1.5 ${p.skillPreferences.includes(t) ? 'border-accent bg-accent-soft/40 text-white' : 'border-ink-600 text-slate-400'}`}>
              <input type="checkbox" className="sr-only" checked={p.skillPreferences.includes(t)} onChange={() => toggle(t)} />{TOPIC_LABEL[t]}</label>))}</div></fieldset>
      </div>
      {saveErr && <p role="alert" className="text-sm text-red-300">{saveErr}</p>}
      {saveMsg && <p role="status" className="text-sm text-emerald-300">{saveMsg}</p>}
      <button className="btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save profile'}</button>
    </form>
  )
}
