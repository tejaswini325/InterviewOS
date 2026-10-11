import { useEffect, useRef, type ReactNode } from 'react'
import type { Difficulty } from '../types'

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return <div role="status" className="flex items-center gap-3 py-10 text-slate-400"><span className="h-4 w-4 animate-spin rounded-full border-2 border-ink-600 border-t-accent" />{label}…</div>
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="card border-red-500/40 text-sm">
      <p className="text-red-300">{message}</p>
      {onRetry && <button className="btn-ghost mt-3" onClick={onRetry}>Retry</button>}
    </div>
  )
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="card py-10 text-center">
      <p className="font-medium text-slate-100">{title}</p>
      {hint && <p className="mt-1 text-sm text-slate-400">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

const DIFF_STYLE: Record<string, string> = {
  EASY: 'border-emerald-500/40 text-emerald-300', MEDIUM: 'border-amber-500/40 text-amber-300', HARD: 'border-rose-500/40 text-rose-300',
}
export function DifficultyBadge({ difficulty }: { difficulty: Difficulty | string }) {
  return <span className={`badge ${DIFF_STYLE[difficulty] ?? 'border-ink-600'}`} data-testid="difficulty">{difficulty}</span>
}

export function ConfirmDialog({ open, title, body, confirmLabel, onConfirm, onCancel }:
  { open: boolean; title: string; body: string; confirmLabel: string; onConfirm: () => void; onCancel: () => void }) {
  const ref = useRef<HTMLButtonElement>(null)
  useEffect(() => { if (open) ref.current?.focus() }, [open])
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="card w-full max-w-md">
        <h2 className="text-lg font-semibold">{title}</h2>
        <p className="mt-2 text-sm text-slate-400">{body}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button ref={ref} className="btn-ghost" onClick={onCancel}>Cancel</button>
          <button className="btn-primary" onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return <div className="card"><p className="label">{label}</p><p className="text-2xl font-semibold text-slate-50">{value}</p>{hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}</div>
}

export const fmtDate = (iso?: string) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '-')
export const fmtScore = (v?: number | null) => (v == null ? '-' : `${Math.round(v)}%`)
export const fmtClock = (s: number) => `${String(Math.floor(Math.max(0, s) / 60)).padStart(2, '0')}:${String(Math.max(0, s) % 60).padStart(2, '0')}`
