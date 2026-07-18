import React from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { CategoryProvider } from './context/CategoryContext'
import MainLayout from './components/MainLayout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import AddRecord from './pages/AddRecord'
import RecordList from './pages/RecordList'
import CategoryManage from './pages/CategoryManage'
import SnakeGame from './pages/SnakeGame'
import Report from './pages/Report'
import FeedbackPage from './pages/Feedback'
import NotificationCenter from './pages/NotificationCenter'
import AdminPanel from './pages/AdminPanel'

const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { token } = useAuth()
  const location = useLocation()
  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}

const RequireAdmin: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { role } = useAuth()
  if (role !== 'ADMIN') {
    return <Navigate to="/dashboard" replace />
  }
  return <>{children}</>
}

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            path="/*"
            element={
              <RequireAuth>
                <CategoryProvider>
                  <MainLayout />
                </CategoryProvider>
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="add" element={<AddRecord />} />
            <Route path="list" element={<RecordList />} />
            <Route path="categories" element={<CategoryManage />} />
            <Route path="snake" element={<SnakeGame />} />
            <Route path="report" element={<Report />} />
            <Route path="feedback" element={<FeedbackPage />} />
            <Route path="notifications" element={<NotificationCenter />} />
            <Route
              path="admin"
              element={
                <RequireAdmin>
                  <AdminPanel />
                </RequireAdmin>
              }
            />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
