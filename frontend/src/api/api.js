import axios from 'axios'

const api = axios.create({
	baseURL: 'http://localhost:8080',
})

api.interceptors.request.use((config) => {
	const token = localStorage.getItem('careerbridge_token')
	if (token) {
		config.headers.Authorization = `Bearer ${token}`
	}
	return config
})

api.interceptors.response.use(
	(response) => response,
	(error) => {
		if (error.response?.status === 401) {
			localStorage.removeItem('careerbridge_token')
			localStorage.removeItem('careerbridge_user')
			window.location.href = '/login'
		}
		return Promise.reject(error)
	}
)

// AUTH
export const register = (data) => api.post('/api/auth/register', data)
export const login = (data) => api.post('/api/auth/login', data)

// PROFILE
export const getProfile = () => api.get('/api/profile')
export const updateProfile = (data) => api.put('/api/profile', data)
export const changePassword = (data) => api.put('/api/profile/password', data)
export const uploadProfilePhoto = (formData) =>
	api.post('/api/profile/photo', formData, {
		headers: { 'Content-Type': 'multipart/form-data' },
	})
export const getProfilePhoto = () =>
	api.get('/api/profile/photo', { responseType: 'blob' })

// DASHBOARD
export const getDashboardStats = () => api.get('/api/dashboard/stats')

// INTERVIEW
export const startInterview = () => api.post('/api/interview/start')
export const respondToInterview = (interviewId, formData) =>
	api.post(`/api/interview/${interviewId}/respond`, formData, {
		headers: { 'Content-Type': 'multipart/form-data' },
	})
export const getInterviewMessages = (interviewId) =>
	api.get(`/api/interview/${interviewId}/messages`)
export const getCurrentInterview = () => api.get('/api/interview/current')

// POST INTERVIEW
export const startPostInterview = (data) =>
	api.post('/api/post-interview/start', data)
export const sendUserChoice = (data) => api.post('/api/post-interview/choice', data)
export const getPostInterviewMessages = (interviewId) =>
	api.get(`/api/post-interview/${interviewId}/messages`)
