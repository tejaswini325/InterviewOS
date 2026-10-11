import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, describeError } from '../api'
import { DifficultyBadge, EmptyState, ErrorState, Spinner } from '../components/ui'
import type { BookmarkView } from '../types'
import { MODE_INFO, TOPIC_LABEL } from '../types'

const modeFor = (topic: BookmarkView['topic']) => (Object.keys(MODE_INFO) as (keyof typeof MODE_INFO)[]).find((m) => MODE_INFO[m].topics.length === 1 && MODE_INFO[m].topics[0] === topic) ?? 'TECHNICAL'

export default function Bookmarks() {
  const [items, setItems] = useState<BookmarkView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const load = useCallback(async () => {
    setError(null)
    try { setItems((await api.get<BookmarkView[]>('/bookmarks')).data) } catch (e) { setError(describeError(e).message) }
  }, [])
  useEffect(() => { void load() }, [load])

  const remove = async (id: number) => {
    try { await api.delete(`/bookmarks/${id}`); setItems((cur) => cur?.filter((b) => b.questionId !== id) ?? null) } catch (e) { setError(describeError(e).message) }
  }

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!items) return <Spinner />
  return (
    <div className="space-y-5">
      <div><h1 className="text-2xl font-semibold">Bookmarked questions</h1><p className="text-sm text-slate-400">Questions you flagged during interviews. Practise their topics from here.</p></div>
      {items.length === 0 ? <EmptyState title="No bookmarks yet" hint="Use “Bookmark question” in the interview room to save a question." /> : (
        <ul className="space-y-3">
          {items.map((b) => (
            <li key={b.questionId} className="card">
              <div className="mb-2 flex flex-wrap items-center gap-2 text-xs"><span className="badge border-ink-600 text-slate-300">{TOPIC_LABEL[b.topic]} · {b.subtopic}</span><DifficultyBadge difficulty={b.difficulty} /></div>
              <p className="text-sm text-slate-100">{b.statement}</p>
              <div className="mt-3 flex gap-2">
                <Link className="btn-ghost no-underline" to={`/interviews/new?mode=${modeFor(b.topic)}&topics=${b.topic}&difficulty=${b.difficulty}`}>Practise this topic</Link>
                <button className="btn-ghost" onClick={() => remove(b.questionId)}>Remove</button>
              </div>
            </li>))}
        </ul>)}
    </div>
  )
}
