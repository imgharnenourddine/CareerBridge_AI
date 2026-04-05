import {
	createContext,
	useCallback,
	useContext,
	useMemo,
	useReducer,
} from 'react'
import { useNavigate } from 'react-router-dom'
import * as authApi from '../api/api'

const AuthContext = createContext(null)

const ACTIONS = {
	LOGIN_SUCCESS: 'LOGIN_SUCCESS',
	LOGOUT: 'LOGOUT',
	UPDATE_USER: 'UPDATE_USER',
}

function authReducer(state, action) {
	switch (action.type) {
		case ACTIONS.LOGIN_SUCCESS: {
			const { token, user } = action.payload
			localStorage.setItem('careerbridge_token', token)
			localStorage.setItem('careerbridge_user', JSON.stringify(user))
			return {
				...state,
				token,
				user,
				isAuthenticated: true,
				isLoading: false,
			}
		}
		case ACTIONS.LOGOUT: {
			localStorage.removeItem('careerbridge_token')
			localStorage.removeItem('careerbridge_user')
			return {
				...state,
				user: null,
				token: null,
				isAuthenticated: false,
				isLoading: false,
			}
		}
		case ACTIONS.UPDATE_USER: {
			if (!state.user) return state
			const user = { ...state.user, ...action.payload }
			localStorage.setItem('careerbridge_user', JSON.stringify(user))
			return { ...state, user }
		}
		default:
			return state
	}
}

function initAuthState() {
	const token = localStorage.getItem('careerbridge_token')
	const raw = localStorage.getItem('careerbridge_user')
	let user = null
	if (raw) {
		try {
			user = JSON.parse(raw)
		} catch {
			user = null
		}
	}
	return {
		user,
		token,
		isAuthenticated: Boolean(token && user),
		isLoading: false,
	}
}

export function AuthProvider({ children }) {
	const [state, dispatch] = useReducer(authReducer, null, initAuthState)
	const navigate = useNavigate()

	const login = useCallback(async (email, password) => {
		const res = await authApi.login({ email, password })
		const data = res.data
		dispatch({
			type: ACTIONS.LOGIN_SUCCESS,
			payload: {
				token: data.token,
				user: {
					email: data.email,
					firstName: data.firstName,
					lastName: data.lastName,
					role: data.role,
				},
			},
		})
		return res
	}, [])

	const register = useCallback(async (firstName, lastName, email, password) => {
		const res = await authApi.register({
			firstName,
			lastName,
			email,
			password,
		})
		const data = res.data
		dispatch({
			type: ACTIONS.LOGIN_SUCCESS,
			payload: {
				token: data.token,
				user: {
					email: data.email,
					firstName: data.firstName,
					lastName: data.lastName,
					role: data.role,
				},
			},
		})
		return res
	}, [])

	const logout = useCallback(() => {
		dispatch({ type: ACTIONS.LOGOUT })
		navigate('/')
	}, [navigate])

	const updateUser = useCallback((payload) => {
		dispatch({ type: ACTIONS.UPDATE_USER, payload })
	}, [])

	const value = useMemo(
		() => ({
			...state,
			login,
			register,
			logout,
			updateUser,
		}),
		[state, login, register, logout, updateUser]
	)

	return (
		<AuthContext.Provider value={value}>{children}</AuthContext.Provider>
	)
}

export function useAuth() {
	const ctx = useContext(AuthContext)
	if (!ctx) {
		throw new Error('useAuth must be used within AuthProvider')
	}
	return ctx
}
