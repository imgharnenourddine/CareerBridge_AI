import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import InterviewPage from './pages/InterviewPage'
import MessagesPage from './pages/MessagesPage'

function ProtectedRoute({ children }) {
	const { isAuthenticated, isLoading } = useAuth()
	if (isLoading) {
		return <div className="p-6 text-center text-neutral-600">Loading…</div>
	}
	if (!isAuthenticated) {
		return <Navigate to="/login" replace />
	}
	return children
}

function PublicOnlyRoute({ children }) {
	const { isAuthenticated, isLoading } = useAuth()
	if (isLoading) {
		return <div className="p-6 text-center text-neutral-600">Loading…</div>
	}
	if (isAuthenticated) {
		return <Navigate to="/dashboard" replace />
	}
	return children
}

export default function App() {
	return (
		<AuthProvider>
			<Routes>
				<Route path="/" element={<HomePage />} />
				<Route
					path="/login"
					element={
						<PublicOnlyRoute>
							<LoginPage />
						</PublicOnlyRoute>
					}
				/>
				<Route
					path="/register"
					element={
						<PublicOnlyRoute>
							<RegisterPage />
						</PublicOnlyRoute>
					}
				/>
				<Route
					path="/dashboard"
					element={
						<ProtectedRoute>
							<DashboardPage />
						</ProtectedRoute>
					}
				/>
				<Route
					path="/interview"
					element={
						<ProtectedRoute>
							<InterviewPage />
						</ProtectedRoute>
					}
				/>
				<Route
					path="/messages"
					element={
						<ProtectedRoute>
							<MessagesPage />
						</ProtectedRoute>
					}
				/>
				<Route path="*" element={<Navigate to="/" replace />} />
			</Routes>
		</AuthProvider>
	)
}
