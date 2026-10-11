import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import Layout from './components/Layout'
import Landing from './pages/Landing'
import { Login, Register } from './pages/AuthPages'
import Dashboard from './pages/Dashboard'
import InterviewSetup from './pages/InterviewSetup'
import Workspace from './pages/Workspace'
import Report from './pages/Report'
import History from './pages/History'
import Analytics from './pages/Analytics'
import Bookmarks from './pages/Bookmarks'
import ProfilePage from './pages/ProfilePage'

function Protected({ children }: { children: JSX.Element }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<Protected><Layout /></Protected>}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/interviews/new" element={<InterviewSetup />} />
        <Route path="/interviews/:id/live" element={<Workspace />} />
        <Route path="/interviews/:id/report" element={<Report />} />
        <Route path="/history" element={<History />} />
        <Route path="/analytics" element={<Analytics />} />
        <Route path="/bookmarks" element={<Bookmarks />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
