import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
	getPostInterviewMessages,
	sendUserChoice,
	startPostInterview,
} from '../api/api'
import botAvatar from '../assets/download.png'
import logoImg from '../assets/download.png'

const messagesPageStyles = `
        /* Exact Design System */
        body { 
            font-family: 'Plus Jakarta Sans', sans-serif; 
            background-color: #08090c; 
            color: #ffffff; 
        }
        .messages-page-root .glass-panel { 
            background: rgba(255, 255, 255, 0.03); 
            backdrop-filter: blur(12px); 
            -webkit-backdrop-filter: blur(12px);
        }
        .messages-page-root .btn-neon {
            background: linear-gradient(90deg, #FF0055, #d40046);
            box-shadow: 0 0 20px rgba(255, 0, 85, 0.4);
            transition: all 0.3s ease;
            color: white;
            border: none;
        }
        .messages-page-root .btn-neon:hover {
            box-shadow: 0 0 35px rgba(255, 0, 85, 0.7);
            transform: translateY(-2px);
        }
        
        /* Custom Scrollbar */
        .messages-page-root ::-webkit-scrollbar { width: 6px; }
        .messages-page-root ::-webkit-scrollbar-track { background: transparent; }
        .messages-page-root ::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.1); border-radius: 10px; }
        .messages-page-root ::-webkit-scrollbar-thumb:hover { background: rgba(255, 0, 85, 0.5); }
`

function getApiError(err) {
	const d = err.response?.data
	if (typeof d === 'string') return d
	if (d?.error) return d.error
	if (d?.message) return d.message
	return err.message || 'Request failed'
}

function sortMessages(list) {
	const arr = Array.isArray(list) ? [...list] : []
	arr.sort((a, b) => {
		const oa = a.orderIndex ?? 0
		const ob = b.orderIndex ?? 0
		if (oa !== ob) return oa - ob
		const ta = new Date(a.sentAt ?? a.createdAt ?? 0).getTime()
		const tb = new Date(b.sentAt ?? b.createdAt ?? 0).getTime()
		return ta - tb
	})
	return arr
}

function parseBotPayload(content) {
	if (content == null || content === '') {
		return { text: '', options: null, parsedType: null }
	}
	const raw = String(content).trim()
	try {
		const j = JSON.parse(raw)
		if (j && typeof j === 'object') {
			return {
				text: j.message != null ? String(j.message) : raw,
				options: Array.isArray(j.options) ? j.options : null,
				parsedType: j.type != null ? String(j.type) : null,
			}
		}
	} catch {
		/* plain text */
	}
	return { text: raw, options: null, parsedType: null }
}

function renderMessageContent(content) {
	try {
		const parsed = JSON.parse(content)
		const message = parsed.message || content

		const lines = String(message).split('\n').filter(Boolean)

		return (
			<div className="flex flex-col gap-2">
				{lines.map((line, i) => {
					if (line.startsWith('**') && line.endsWith('**')) {
						return (
							<p
								key={i}
								className="mt-2 font-black text-sm tracking-wider text-white uppercase"
							>
								{line.replace(/\*\*/g, '')}
							</p>
						)
					}
					if (line.startsWith('•')) {
						return (
							<p
								key={i}
								className="border-l-2 border-[#FF0055]/50 pl-3 text-sm text-gray-300"
							>
								{line}
							</p>
						)
					}
					if (line.includes('→')) {
						const [label, value] = line.split('→')
						return (
							<p key={i} className="text-sm">
								<span className="font-semibold text-gray-400">{label.trim()} </span>
								<span className="font-bold text-white">{value?.trim()}</span>
							</p>
						)
					}
					if (
						line.includes('@') ||
						line.toLowerCase().includes('tel') ||
						line.toLowerCase().includes('phone')
					) {
						return (
							<p key={i} className="text-sm font-semibold text-[#FF0055]">
								{line}
							</p>
						)
					}
					if (line.includes('MAD')) {
						return (
							<p key={i} className="text-sm font-bold text-green-400">
								{line}
							</p>
						)
					}
					if (line.startsWith('---')) {
						return <div key={i} className="my-2 border-t border-white/10" />
					}
					return (
						<p key={i} className="text-sm leading-relaxed text-gray-300">
							{line}
						</p>
					)
				})}
			</div>
		)
	} catch {
		return <p className="text-sm leading-relaxed text-gray-300">{content}</p>
	}
}

function formatMsgTime(iso) {
	if (!iso) return ''
	try {
		return new Date(iso).toLocaleTimeString(undefined, {
			hour: 'numeric',
			minute: '2-digit',
		})
	} catch {
		return ''
	}
}

async function fetchCompletedInterviews() {
	const token = localStorage.getItem('careerbridge_token')
	const res = await fetch('http://localhost:8080/api/interview/completed', {
		headers: token ? { Authorization: `Bearer ${token}` } : {},
	})
	if (!res.ok) throw new Error('Failed to load conversations')
	return res.json()
}

function formatSidebarTime(iso) {
	if (!iso) return ''
	try {
		const d = new Date(iso)
		const now = new Date()
		const sameDay =
			d.getDate() === now.getDate() &&
			d.getMonth() === now.getMonth() &&
			d.getFullYear() === now.getFullYear()
		if (sameDay) {
			return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
		}
		return d.toLocaleDateString(undefined, { weekday: 'short' })
	} catch {
		return ''
	}
}

const SIDEBAR_PREVIEWS = [
	'Your skill analysis is ready...',
	'We recommend 3 career sectors...',
	'Your formation enrollment is confirmed...',
	'Your application has been sent...',
]

export default function MessagesPage() {
	const navigate = useNavigate()
	const location = useLocation()
	const { logout, user } = useAuth()

	const [messages, setMessages] = useState([])
	const [interviewId, setInterviewId] = useState(null)
	const [completedConversations, setCompletedConversations] = useState([])
	const [isLoading, setIsLoading] = useState(true)
	const [error, setError] = useState('')
	const [lastReadIndex, setLastReadIndex] = useState(0)
	const [buttonsDisabled, setButtonsDisabled] = useState(false)
	const [noInterview, setNoInterview] = useState(false)
	const [initKey, setInitKey] = useState(0)

	const messagesEndRef = useRef(null)
	const pollingRef = useRef(null)
	const botCountAtChoice = useRef(0)

	const [optimisticUserTexts, setOptimisticUserTexts] = useState([])

	const scrollToBottom = useCallback(() => {
		messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
	}, [])

	useEffect(() => {
		let cancelled = false
		async function boot() {
			setError('')
			setNoInterview(false)
			try {
				const list = await fetchCompletedInterviews()
				if (cancelled) return
				setCompletedConversations(Array.isArray(list) ? list : [])
				if (!list || list.length === 0) {
					setNoInterview(true)
					setInterviewId(null)
					setMessages([])
					setIsLoading(false)
					return
				}
				setNoInterview(false)
				setInterviewId(list[0].interviewId)
			} catch (e) {
				if (!cancelled) {
					setError('Failed to load messages. Please try again.')
					setIsLoading(false)
				}
			}
		}
		boot()
		return () => {
			cancelled = true
		}
	}, [initKey])

	useEffect(() => {
		if (interviewId == null) return

		let cancelled = false
		let pollingInterval = null
		const id = interviewId

		async function load() {
			setIsLoading(true)
			setError('')
			const context = `POST_INTERVIEW_${id}`
			console.log('Loading messages for context:', context)

			let msgs = []
			try {
				const msgsRes = await getPostInterviewMessages(id)
				msgs = sortMessages(msgsRes.data || [])
				console.log('Messages found:', msgs.length)
			} catch {
				msgs = []
			}

			if (msgs.length === 0) {
				console.log('No messages for this interview — starting new post-interview flow')
				try {
					await startPostInterview({ interviewId: id })
					const msgsRes2 = await getPostInterviewMessages(id)
					msgs = sortMessages(msgsRes2.data || [])
				} catch (e) {
					console.error('startPostInterview failed:', e)
					try {
						const msgsRes3 = await getPostInterviewMessages(id)
						msgs = sortMessages(msgsRes3.data || [])
					} catch {
						/* ignore */
					}
				}
			}

			if (cancelled) return

			setMessages(msgs)
			setLastReadIndex(msgs.length)
			setIsLoading(false)

			pollingInterval = setInterval(async () => {
				if (cancelled) return
				try {
					const res = await getPostInterviewMessages(id)
					const newMsgs = sortMessages(res.data || [])
					setMessages((prev) => {
						if (newMsgs.length !== prev.length) return newMsgs
						const prevStr = JSON.stringify(
							prev.map((m) => ({ id: m.id, content: m.content, sender: m.sender }))
						)
						const nextStr = JSON.stringify(
							newMsgs.map((m) => ({ id: m.id, content: m.content, sender: m.sender }))
						)
						if (prevStr !== nextStr) return newMsgs
						return prev
					})
				} catch {
					/* keep polling */
				}
			}, 5000)

			pollingRef.current = pollingInterval
		}

		load()

		return () => {
			cancelled = true
			if (pollingInterval) clearInterval(pollingInterval)
			if (pollingRef.current) {
				clearInterval(pollingRef.current)
				pollingRef.current = null
			}
		}
	}, [interviewId, initKey])

	function selectConversation(nextId) {
		if (nextId === interviewId) return
		setInterviewId(nextId)
		setLastReadIndex(0)
		setOptimisticUserTexts([])
		setButtonsDisabled(false)
	}

	const iid = interviewId

	useEffect(() => {
		if (!buttonsDisabled) return
		const bots = messages.filter((m) => m.sender === 'BOT').length
		if (bots > botCountAtChoice.current) {
			setButtonsDisabled(false)
			setOptimisticUserTexts([])
		}
	}, [messages, buttonsDisabled])

	useEffect(() => {
		setOptimisticUserTexts((opts) =>
			opts.filter((opt) => !messages.some((m) => m.sender === 'USER' && String(m.content ?? '') === opt))
		)
	}, [messages])

	useEffect(() => {
		scrollToBottom()
	}, [messages, optimisticUserTexts, scrollToBottom])

	const navBtn = (to, label) => {
		const active = location.pathname === to
		return (
			<button
				type="button"
				onClick={() => navigate(to)}
				className={
					active
						? 'border-b-2 border-[#FF0055] pb-1 text-white transition hover:text-white'
						: 'text-gray-300 transition hover:text-white'
				}
			>
				{label}
			</button>
		)
	}

	let lastBotIndex = -1
	for (let i = messages.length - 1; i >= 0; i--) {
		if (messages[i].sender === 'BOT') {
			lastBotIndex = i
			break
		}
	}

	const unreadCount =
		lastReadIndex >= 0 && messages.length > lastReadIndex + 1
			? messages.length - lastReadIndex - 1
			: 0

	const showNewDivider =
		lastReadIndex >= 0 && messages.length > lastReadIndex + 1 && messages.length > 0

	async function handleChoiceClick(choice, parsedType) {
		if (iid == null || buttonsDisabled) return
		setButtonsDisabled(true)
		botCountAtChoice.current = messages.filter((m) => m.sender === 'BOT').length
		setOptimisticUserTexts((o) => [...o, choice])
		try {
			await sendUserChoice({
				interviewId: iid,
				choice,
				context: parsedType ?? '',
			})
		} catch (e) {
			setButtonsDisabled(false)
			setOptimisticUserTexts((o) => o.slice(0, -1))
			setError(getApiError(e))
		}
	}

	const displayRows = []
	messages.forEach((m, idx) => {
		if (showNewDivider && idx === lastReadIndex + 1) {
			displayRows.push({ type: 'divider', key: `divider-${idx}` })
		}
		displayRows.push({ type: 'msg', key: m.id ?? `m-${idx}`, msg: m, idx })
	})
	optimisticUserTexts.forEach((text, oi) => {
		displayRows.push({ type: 'optimistic', key: `opt-${oi}-${text}`, text })
	})

	const navUsername = user
		? `${(user.firstName?.[0] || '').toUpperCase()}.${(user.lastName || '').toUpperCase()}`
		: ''
	const imagePathForPhoto = user?.imagePath || '/default-avatar.png'

	const shellNav = (
		<nav className="glass-panel fixed top-0 left-0 z-50 flex h-[72px] w-full items-center justify-between border-b border-white/5 px-8 py-4">
			<div className="flex items-center gap-3">
				<img src={logoImg} alt="CareerBridge Logo" className="h-10 w-10 object-contain" />
				<span className="text-xl font-bold tracking-tight">
					CareerBridge <span className="text-[#FF0055]">AI</span>
				</span>
			</div>

			<div className="hidden gap-8 text-sm font-bold text-gray-300 md:flex">
				{navBtn('/dashboard', 'Dashboard')}
				{navBtn('/interview', 'Interview')}
				{navBtn('/messages', 'Messages')}
			</div>

			<div className="flex items-center gap-6">
				<div className="hidden items-center gap-3 md:flex">
					<span className="text-sm font-semibold">{navUsername}</span>
					{user?.imagePath ? (
						<img
							src={imagePathForPhoto}
							alt="Profile"
							className="w-10 h-10 rounded-full object-cover border-2 border-[#FF0055] shadow-[0_0_10px_rgba(255,0,85,0.4)]"
						/>
					) : (
						<div className="flex h-10 w-10 items-center justify-center rounded-full border-2 border-[#FF0055] bg-gray-800 text-xs font-bold shadow-[0_0_10px_rgba(255,0,85,0.4)]">
							{`${user?.firstName?.[0] || ''}${user?.lastName?.[0] || ''}`.toUpperCase()}
						</div>
					)}
				</div>
				<button
					type="button"
					onClick={() => logout()}
					className="text-sm font-bold tracking-wider text-gray-400 uppercase transition hover:text-[#FF0055]"
				>
					Logout
				</button>
			</div>
		</nav>
	)

	if (isLoading) {
		return (
			<>
				<style>{messagesPageStyles}</style>
				<div className="messages-page-root relative flex h-screen flex-col overflow-hidden bg-[#08090c] text-white antialiased">
					{shellNav}
					<div className="flex flex-1 items-center justify-center pt-[72px] text-gray-400">Loading…</div>
				</div>
			</>
		)
	}

	if (noInterview && !error && !isLoading) {
		return (
			<>
				<style>{messagesPageStyles}</style>
				<div className="messages-page-root relative flex min-h-screen flex-col overflow-x-hidden bg-[#08090c] text-white antialiased">
					{shellNav}
					<div className="flex flex-1 flex-col items-center justify-center px-8 pt-[100px]">
						<p className="mb-6 max-w-md text-center text-lg text-gray-300">
							Complete your voice interview first
						</p>
						<button
							type="button"
							onClick={() => navigate('/interview')}
							className="rounded-xl bg-gradient-to-r from-[#FF0055] to-[#d40046] px-8 py-4 text-sm font-bold tracking-wide text-white shadow-[0_0_20px_rgba(255,0,85,0.4)]"
						>
							Go to Interview
						</button>
					</div>
				</div>
			</>
		)
	}

	return (
		<>
			<style>{messagesPageStyles}</style>
			<div className="messages-page-root relative flex h-screen flex-col overflow-hidden bg-[#08090c] text-white antialiased">
				{shellNav}

				{error ? (
					<div className="flex flex-1 flex-col items-center justify-center gap-4 px-8 pt-[72px]">
						<p className="text-center text-red-400">{error}</p>
						<button
							type="button"
							onClick={() => {
								setError('')
								setInitKey((k) => k + 1)
							}}
							className="btn-neon rounded-xl px-6 py-3 text-sm font-bold text-white"
						>
							Retry
						</button>
					</div>
				) : (
					<div className="flex h-full w-full pt-[72px]">
						<div className="glass-panel flex h-full w-[30%] flex-col border-r border-white/5">
							<div className="p-6 pb-4">
								<h1 className="mb-6 text-3xl font-black text-white">Messages</h1>
								<input
									type="text"
									placeholder="Search conversations..."
									className="glass-panel w-full rounded-full border border-white/10 px-5 py-3 text-sm text-white placeholder-gray-500 transition-colors focus:border-[#FF0055] focus:outline-none"
								/>
							</div>

							<div className="flex flex-1 flex-col overflow-y-auto">
								{completedConversations.map((conv, convIdx) => {
									const active = conv.interviewId === interviewId
									const rowTime = formatSidebarTime(conv.completedAt)
									const preview =
										SIDEBAR_PREVIEWS[convIdx % SIDEBAR_PREVIEWS.length] ?? SIDEBAR_PREVIEWS[0]
									return (
										<button
											key={conv.interviewId}
											type="button"
											onClick={() => selectConversation(conv.interviewId)}
											className={`flex w-full cursor-pointer items-start gap-4 border-l-4 p-5 text-left transition-colors ${
												active
													? 'border-l-[#FF0055] bg-white/5 hover:bg-white/10'
													: 'border-l-transparent hover:bg-white/5'
											}`}
										>
											<div className="relative min-w-max">
												<img
													src={botAvatar}
													alt="Avatar"
													className="h-12 w-12 rounded-full border border-white/10 bg-gray-900 object-cover"
												/>
												{active && unreadCount > 0 ? (
													<div className="absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-[#FF0055] text-[10px] font-bold text-white shadow-[0_0_10px_rgba(255,0,85,0.8)]">
														{unreadCount > 9 ? '9+' : unreadCount}
													</div>
												) : null}
											</div>
											<div className="min-w-0 flex-1 overflow-hidden">
												<div className="mb-1 flex items-center justify-between">
													<h3 className="text-sm font-bold text-white">
														CareerBridge <span className="text-[#FF0055]">AI</span>
													</h3>
													<span
														className={`text-xs ${active ? 'text-gray-400' : 'text-gray-500'}`}
													>
														{rowTime || '—'}
													</span>
												</div>
												<p
													className={`truncate text-sm ${active ? 'text-gray-400' : 'text-gray-500'}`}
												>
													{preview}
												</p>
											</div>
										</button>
									)
								})}
							</div>
						</div>

						<div className="flex h-full w-[70%] flex-col bg-[#08090c]">
							<div className="glass-panel flex h-[80px] shrink-0 items-center justify-between border-b border-white/5 px-8">
								<div className="flex items-center gap-4">
									<img
										src={botAvatar}
										alt="CareerBridge AI"
										className="h-12 w-12 rounded-full border-2 border-[#FF0055] bg-gray-900 object-cover shadow-[0_0_10px_rgba(255,0,85,0.3)]"
									/>
									<div>
										<h2 className="text-lg leading-tight font-bold text-white">
											CareerBridge <span className="text-[#FF0055]">AI</span>
										</h2>
										<p className="text-xs text-gray-400">AI Career Advisor</p>
									</div>
								</div>
								<div className="flex items-center gap-2 rounded-full border border-green-500/20 bg-green-500/10 px-3 py-1.5 text-xs font-bold tracking-wider text-green-400 uppercase">
									<div className="h-2 w-2 rounded-full bg-green-500 shadow-[0_0_5px_#22c55e]" />
									Online
								</div>
							</div>

							<div className="flex flex-1 flex-col gap-8 overflow-y-auto p-8">
								{displayRows.map((row) => {
									if (row.type === 'divider') {
										return (
											<div key={row.key} className="my-6 flex items-center gap-4">
												<div className="h-px flex-1 bg-[rgba(255,0,85,0.4)]" />
												<span className="text-xs font-bold tracking-widest text-gray-400 uppercase">
													New Messages
												</span>
												<div className="h-px flex-1 bg-[rgba(255,0,85,0.4)]" />
											</div>
										)
									}
									if (row.type === 'optimistic') {
										return (
											<div
												key={row.key}
												className="mt-2 flex max-w-[80%] flex-col items-end gap-1 self-end"
											>
												<div className="btn-neon rounded-2xl rounded-br-none px-5 py-3 text-sm text-white shadow-lg">
													{row.text}
												</div>
											</div>
										)
									}
									const m = row.msg
									const idx = row.idx
									const ts = formatMsgTime(m.sentAt ?? m.createdAt)
									const isNewBlock = showNewDivider && idx > lastReadIndex

									if (m.sender === 'USER') {
										return (
											<div
												key={row.key}
												className={`mt-2 flex max-w-[80%] flex-col items-end gap-1 self-end ${
													isNewBlock ? 'border-l-2 border-[#FF0055]/30 pl-4' : ''
												}`}
											>
												<div className="btn-neon rounded-2xl rounded-br-none px-5 py-3 text-sm text-white shadow-lg">
													{String(m.content ?? '')}
												</div>
												<span className="mr-1 text-[10px] font-bold text-gray-500">{ts}</span>
											</div>
										)
									}

									const { options, parsedType } = parseBotPayload(m.content)
									const showOpts =
										lastBotIndex === idx &&
										options &&
										options.length > 0 &&
										!buttonsDisabled

									return (
										<div
											key={row.key}
											className={`flex max-w-[85%] items-end gap-3 self-start ${
												isNewBlock ? 'mt-2 border-l-2 border-[#FF0055]/30 pl-4' : ''
											}`}
										>
											<img
												src={botAvatar}
												alt="Bot"
												className="mb-5 h-8 w-8 rounded-full border border-white/10 bg-gray-900 object-cover"
											/>
											<div className="flex flex-col gap-1">
												<div className="glass-panel rounded-2xl rounded-bl-none border border-white/5 p-4 text-sm leading-relaxed text-white shadow-lg">
													{renderMessageContent(m.content)}
												</div>
												{showOpts ? (
													<div className="mt-2 flex flex-wrap gap-2">
														{options.map((opt) => (
															<button
																key={opt}
																type="button"
																disabled={buttonsDisabled}
																onClick={() => handleChoiceClick(opt, parsedType)}
																className="rounded-full bg-gradient-to-r from-[#FF0055] to-[#d40046] px-4 py-2 text-xs font-bold text-white transition-all hover:-translate-y-0.5 hover:shadow-[0_0_15px_rgba(255,0,85,0.5)] disabled:opacity-50"
															>
																{opt}
															</button>
														))}
													</div>
												) : null}
												<span className="ml-1 mt-1 text-[10px] font-bold text-gray-500">{ts}</span>
											</div>
										</div>
									)
								})}

								{buttonsDisabled ? (
									<div className="flex items-center gap-2 self-start text-sm text-gray-400">
										<span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-[#FF0055] border-t-transparent" />
										Waiting for response…
									</div>
								) : null}

								<div ref={messagesEndRef} className="h-4" />
							</div>

							<div className="glass-panel shrink-0 border-t border-white/5 p-6">
								<div className="flex items-center gap-4">
									<input
										type="text"
										placeholder="Type your message..."
										readOnly
										tabIndex={-1}
										aria-hidden
										className="flex-1 rounded-full border border-white/10 bg-white/5 px-6 py-3.5 text-sm text-white shadow-inner placeholder-gray-500 transition-colors focus:border-[#FF0055] focus:outline-none"
									/>
									<button
										type="button"
										tabIndex={-1}
										className="btn-neon flex h-12 w-12 shrink-0 items-center justify-center rounded-full group"
										aria-hidden
									>
										<svg
											xmlns="http://www.w3.org/2000/svg"
											width="20"
											height="20"
											viewBox="0 0 24 24"
											fill="none"
											stroke="currentColor"
											strokeWidth="2.5"
											strokeLinecap="round"
											strokeLinejoin="round"
											className="transform text-white transition-transform group-hover:translate-x-1 group-hover:-translate-y-1"
										>
											<line x1="22" y1="2" x2="11" y2="13" />
											<polygon points="22 2 15 22 11 13 2 9 22 2" />
										</svg>
									</button>
								</div>
							</div>
						</div>
					</div>
				)}
			</div>
		</>
	)
}
