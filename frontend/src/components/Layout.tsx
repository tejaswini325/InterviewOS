import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'

const links: [string, string][] = [
  ['/dashboard', 'Dashboard'], ['/interviews/new', 'New interview'], ['/history', 'History'], ['/analytics', 'Skills'], ['/bookmarks', 'Bookmarks'], ['/profile', 'Profile'],
]

export default function Layout() {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const item = ({ isActive }: { isActive: boolean }) =>
    `block rounded-lg px-3 py-2 text-sm no-underline transition ${isActive ? 'bg-accent-soft/60 text-white' : 'text-slate-400 hover:bg-ink-800 hover:text-white'}`
  return (
    <div className="min-h-screen md:flex">
      <aside className="border-b border-ink-700 bg-ink-900 md:sticky md:top-0 md:h-screen md:w-60 md:shrink-0 md:border-b-0 md:border-r">
        <div className="flex items-center justify-between px-4 py-3 md:block md:py-5">
          <span className="text-lg font-semibold text-slate-50">Interview<span className="text-accent">OS</span></span>
          <div className="text-xs text-slate-500 md:mt-1">{user?.displayName}</div>
        </div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-3 pb-3 md:block md:space-y-1 md:overflow-visible">
          {links.map(([to, label]) => <NavLink key={to} to={to} className={item}>{label}</NavLink>)}
        </nav>
        <div className="hidden px-3 pb-5 md:absolute md:bottom-0 md:block md:w-full">
          <button className="btn-ghost w-full" onClick={async () => { await logout(); nav('/') }}>Log out</button>
        </div>
        <div className="px-3 pb-3 md:hidden"><button className="btn-ghost" onClick={async () => { await logout(); nav('/') }}>Log out</button></div>
      </aside>
      <main className="min-w-0 flex-1 px-4 py-8 md:px-10"><div className="mx-auto max-w-5xl"><Outlet /></div></main>
    </div>
  )
}
