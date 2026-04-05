import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { Mic } from 'lucide-react'
import { getProfile, getProfilePhoto, respondToInterview, startInterview } from '../api/api'
import { useAuth } from '../context/AuthContext'
import logoImg from '../assets/download.png'

const API_BASE = 'http://localhost:8080'
const MAX_QUESTIONS = 7

const QUESTIONS = [
	'Hello! I am your AI career advisor from The Forge Guild. I am here to help you find a new career path. History shows that workers who lost jobs to automation successfully transitioned to new sectors. I will ask you 7 short questions to understand your profile. First question: What was your main job at the factory and how many years did you work there?',
	'Thank you! What were your main daily tasks at work? For example: operating machines, quality control, assembly, maintenance, or logistics?',
	'Did your job require more physical skills, or more technical and problem-solving skills?',
	'Have you ever used computers, software, or any digital tools in your work?',
	'What are you most passionate about outside of work? Any hobbies or interests that could be relevant to a new career?',
	'Are you available for full-time training, or do you need a part-time option due to personal commitments?',
	'Last question: do you prefer working in a technical indoor environment, outdoors, or with direct contact with people and customers?',
]

const CLOSING_MESSAGE =
	'Thank you for your answers. I now have everything I need to analyze your profile. Your personalized career recommendations will be ready shortly in your message inbox. Please check it now.'

function getApiError(err) {
	const d = err.response?.data
	if (typeof d === 'string') return d
	if (d?.error) return d.error
	if (d?.message) return d.message
	return err.message || 'Request failed'
}

function speakText(text, onFinished) {
	window.speechSynthesis.cancel()
	const utterance = new SpeechSynthesisUtterance(text)
	utterance.lang = 'en-US'
	utterance.rate = 0.85
	utterance.pitch = 1.0
	utterance.volume = 1.0
	utterance.onend = () => {
		console.log('TTS finished')
		onFinished?.()
	}
	utterance.onerror = (e) => {
		console.error('TTS error', e)
		onFinished?.()
	}
	window.speechSynthesis.speak(utterance)
	return () => window.speechSynthesis.cancel()
}

async function completeInterviewRequest(interviewId) {
	const token = localStorage.getItem('careerbridge_token')
	await axios.post(`${API_BASE}/api/interview/${interviewId}/complete`, null, {
		headers: token ? { Authorization: `Bearer ${token}` } : {},
	})
}

const circleClass = {
	idle: 'state-idle',
	'ai-speaking': 'state-ai-speaking',
	'user-speaking': 'state-user-speaking',
	processing: 'state-processing',
	complete: 'state-complete',
}

function speakerLabelClass(phase) {
	if (phase === 'ai-speaking') return 'text-[#FF0055]'
	if (phase === 'user-speaking') return 'text-white'
	if (phase === 'processing') return 'text-orange-400'
	if (phase === 'complete') return 'text-green-400'
	return 'text-transparent'
}

function speakerLabelText(phase) {
	if (phase === 'idle') return ''
	if (phase === 'ai-speaking') return 'CareerBridge AI is speaking...'
	if (phase === 'user-speaking') return 'Your turn to speak'
	if (phase === 'processing') return 'Processing your answer...'
	if (phase === 'complete') return 'Interview Complete ✓'
	return ''
}

export default function InterviewPage() {
	const navigate = useNavigate()
	const { user, logout } = useAuth()

	const [phase, setPhase] = useState('idle')
	const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0)
	const [isStarted, setIsStarted] = useState(false)
	const [isRecording, setIsRecording] = useState(false)
	const [error, setError] = useState('')
	const [micDenied, setMicDenied] = useState(false)
	const [interviewId, setInterviewId] = useState(null)
	const [photoUrl, setPhotoUrl] = useState(null)
	const [profileImagePath, setProfileImagePath] = useState(null)

	const mediaRecorderRef = useRef(null)
	const chunksRef = useRef([])
	const streamRef = useRef(null)
	const speakCleanupRef = useRef(null)
	const interviewIdRef = useRef(null)
	const questionIndexRef = useRef(0)

	useEffect(() => {
		interviewIdRef.current = interviewId
	}, [interviewId])

	useEffect(() => {
		questionIndexRef.current = currentQuestionIndex
	}, [currentQuestionIndex])

	useEffect(() => {
		const interval = setInterval(() => {
			if (window.speechSynthesis.speaking) {
				window.speechSynthesis.pause()
				window.speechSynthesis.resume()
			}
		}, 10000)
		return () => clearInterval(interval)
	}, [])

	const stopSpeechCleanup = useCallback(() => {
		if (speakCleanupRef.current) {
			speakCleanupRef.current()
			speakCleanupRef.current = null
		}
		window.speechSynthesis?.cancel()
	}, [])

	useEffect(() => {
		let cancelled = false
		getProfile()
			.then((r) => {
				if (!cancelled) setProfileImagePath(r.data?.imagePath ?? null)
			})
			.catch(() => {
				if (!cancelled) setProfileImagePath(null)
			})
		return () => {
			cancelled = true
		}
	}, [])

	const imagePathForPhoto = user?.imagePath ?? profileImagePath

	useEffect(() => {
		let cancelled = false
		async function loadPhoto() {
			try {
				if (imagePathForPhoto) {
					const res = await getProfilePhoto()
					if (cancelled) return
					const blob = new Blob([res.data], { type: res.headers['content-type'] || 'image/jpeg' })
					const next = URL.createObjectURL(blob)
					setPhotoUrl((prev) => {
						if (prev) URL.revokeObjectURL(prev)
						return next
					})
				} else {
					setPhotoUrl((prev) => {
						if (prev) URL.revokeObjectURL(prev)
						return null
					})
				}
			} catch {
				setPhotoUrl((prev) => {
					if (prev) URL.revokeObjectURL(prev)
					return null
				})
			}
		}
		loadPhoto()
		return () => {
			cancelled = true
		}
	}, [imagePathForPhoto])

	useEffect(() => {
		return () => {
			window.speechSynthesis?.cancel()
			mediaRecorderRef.current?.stop()
			streamRef.current?.getTracks().forEach((t) => t.stop())
		}
	}, [])

	const startRecording = useCallback(async () => {
		setMicDenied(false)
		setError('')

		if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
			setError('Recording is not supported in this browser.')
			setPhase('idle')
			return
		}

		try {
			const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
			streamRef.current = stream
			const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
				? 'audio/webm;codecs=opus'
				: MediaRecorder.isTypeSupported('audio/webm')
					? 'audio/webm'
					: 'audio/mp4'
			const rec = new MediaRecorder(stream, { mimeType: mime })
			chunksRef.current = []
			rec.ondataavailable = (e) => {
				if (e.data.size) chunksRef.current.push(e.data)
			}
			rec.start(200)
			mediaRecorderRef.current = rec
			setIsRecording(true)
			setPhase('user-speaking')
		} catch {
			setMicDenied(true)
			setError('Microphone access is required to continue the interview.')
			setPhase('idle')
		}
	}, [])

	const stopRecordingAndSend = useCallback(async () => {
		const id = interviewIdRef.current
		const rec = mediaRecorderRef.current
		const stream = streamRef.current
		const answeredIndex = questionIndexRef.current

		mediaRecorderRef.current = null
		streamRef.current = null
		setIsRecording(false)

		if (!id || !rec) return undefined

		return new Promise((resolve, reject) => {
			rec.onstop = async () => {
				stream?.getTracks().forEach((t) => t.stop())
				try {
					const blob = new Blob(chunksRef.current, { type: rec.mimeType || 'audio/webm' })
					chunksRef.current = []
					setPhase('processing')
					const fd = new FormData()
					fd.append('audio', blob, 'recording.webm')
					await respondToInterview(id, fd)
					resolve(answeredIndex)
				} catch (e) {
					reject(e)
				}
			}
			try {
				rec.stop()
			} catch (e) {
				reject(e)
			}
		})
	}, [])

	const continueAfterAnswer = useCallback(
		(answeredIndex) => {
			stopSpeechCleanup()
			if (answeredIndex < 6) {
				const next = answeredIndex + 1
				setCurrentQuestionIndex(next)
				setPhase('ai-speaking')
				speakCleanupRef.current = speakText(QUESTIONS[next], () => {
					speakCleanupRef.current = null
					setPhase('user-speaking')
					startRecording()
				})
				return
			}

			setPhase('ai-speaking')
			speakCleanupRef.current = speakText(CLOSING_MESSAGE, async () => {
				speakCleanupRef.current = null
				const iid = interviewIdRef.current
				try {
					if (iid) {
						await completeInterviewRequest(iid)
						localStorage.setItem('careerbridge_latest_completed_interview_id', String(iid))
					}
				} catch {
					/* still complete UI */
				}
				setPhase('complete')
				setTimeout(() => navigate('/messages'), 3000)
			})
		},
		[navigate, startRecording, stopSpeechCleanup]
	)

	const handleStartInterview = useCallback(async () => {
		setError('')
		setIsStarted(true)
		try {
			const res = await startInterview()
			const id = res.data?.interviewId
			if (!id) throw new Error('No interview id')
			setInterviewId(id)
			setCurrentQuestionIndex(0)
			questionIndexRef.current = 0
			setPhase('ai-speaking')
			stopSpeechCleanup()
			speakCleanupRef.current = speakText(QUESTIONS[0], () => {
				speakCleanupRef.current = null
				setPhase('user-speaking')
				startRecording()
			})
		} catch (e) {
			const raw = e.response?.data?.message ?? e.response?.data?.error ?? e.message ?? ''
			const msg = String(raw)
			const lc = msg.toLowerCase()
			if (lc.includes('already completed') || (lc.includes('completed') && !lc.includes('not completed'))) {
				navigate('/messages')
				return
			}
			setError(getApiError(e))
			setIsStarted(false)
			setPhase('idle')
		}
	}, [navigate, startRecording, stopSpeechCleanup])

	const onStopSpeaking = useCallback(async () => {
		setError('')
		try {
			const answeredIndex = await stopRecordingAndSend()
			if (answeredIndex === undefined || answeredIndex === null) return
			continueAfterAnswer(answeredIndex)
		} catch (e) {
			setError(getApiError(e))
			setPhase('idle')
		}
	}, [continueAfterAnswer, stopRecordingAndSend])

	const visualClass = circleClass[phase] || 'state-idle'
	const showProgress = isStarted && phase !== 'idle' && phase !== 'complete'

	const showStart = phase === 'idle' && !isStarted
	const showStop = phase === 'user-speaking' && isRecording

	const navUsername = user
		? `${(user.firstName?.[0] || '').toUpperCase()}.${(user.lastName || '').toUpperCase()}`
		: ''

	return (
		<>
			<style>{`
				body { font-family: 'Plus Jakarta Sans', sans-serif; }
				.interview-root .glass-panel {
					background: rgba(255, 255, 255, 0.03);
					backdrop-filter: blur(12px);
					-webkit-backdrop-filter: blur(12px);
				}
				.interview-root .btn-neon {
					background: linear-gradient(90deg, #FF0055, #d40046);
					box-shadow: 0 0 20px rgba(255, 0, 85, 0.4);
					transition: all 0.3s ease;
					color: white;
					border: none;
				}
				.interview-root .btn-neon:hover:not(:disabled) {
					box-shadow: 0 0 35px rgba(255, 0, 85, 0.7);
					transform: translateY(-2px);
				}
				.interview-root #voice-circle {
					width: 192px;
					height: 192px;
					border-radius: 50%;
					background-color: #FF0055;
					transition: background-color 0.5s ease, box-shadow 0.5s ease;
					position: relative;
					z-index: 10;
				}
				.interview-root .ring {
					position: absolute;
					top: 50%;
					left: 50%;
					transform: translate(-50%, -50%);
					border-radius: 50%;
					border: 2px solid transparent;
					pointer-events: none;
				}
				.interview-root .state-idle #voice-circle {
					animation: breathe-idle 2s ease-in-out infinite;
				}
				.interview-root .state-idle .ring { display: none; }
				@keyframes breathe-idle {
					0%, 100% { transform: scale(1); box-shadow: 0 0 20px rgba(255, 0, 85, 0.2); }
					50% { transform: scale(1.05); box-shadow: 0 0 40px rgba(255, 0, 85, 0.5); }
				}
				.interview-root .state-ai-speaking #voice-circle {
					background-color: #FF0055;
					animation: breathe-ai 1s ease-in-out infinite;
					box-shadow: 0 0 50px rgba(255, 0, 85, 0.8);
				}
				.interview-root .state-ai-speaking .ring {
					border-color: #FF0055;
					animation: pulse-ring-red 1s cubic-bezier(0.215, 0.61, 0.355, 1) infinite;
				}
				.interview-root .state-ai-speaking .ring-1 { animation-delay: 0s; }
				.interview-root .state-ai-speaking .ring-2 { animation-delay: 0.3s; }
				.interview-root .state-ai-speaking .ring-3 { animation-delay: 0.6s; }
				@keyframes breathe-ai {
					0%, 100% { transform: scale(1); }
					50% { transform: scale(1.15); }
				}
				@keyframes pulse-ring-red {
					0% { width: 192px; height: 192px; opacity: 1; border-width: 4px; }
					100% { width: 450px; height: 450px; opacity: 0; border-width: 1px; }
				}
				.interview-root .state-user-speaking #voice-circle {
					background-color: #ffffff;
					animation: breathe-user 2s ease-in-out infinite;
					box-shadow: 0 0 40px rgba(255, 255, 255, 0.5);
				}
				.interview-root .state-user-speaking .ring {
					border-color: #ffffff;
					animation: pulse-ring-white 2s cubic-bezier(0.215, 0.61, 0.355, 1) infinite;
				}
				.interview-root .state-user-speaking .ring-1 { animation-delay: 0s; }
				.interview-root .state-user-speaking .ring-2 { animation-delay: 0.6s; }
				.interview-root .state-user-speaking .ring-3 { animation-delay: 1.2s; }
				@keyframes breathe-user {
					0%, 100% { transform: scale(1); }
					50% { transform: scale(1.08); }
				}
				@keyframes pulse-ring-white {
					0% { width: 192px; height: 192px; opacity: 0.7; border-width: 3px; }
					100% { width: 380px; height: 380px; opacity: 0; border-width: 1px; }
				}
				.interview-root .state-processing #voice-circle {
					background-color: #6b7280;
					animation: breathe-processing 1.2s ease-in-out infinite;
					box-shadow: 0 0 30px rgba(250, 204, 21, 0.35);
				}
				.interview-root .state-processing .ring { display: none; }
				@keyframes breathe-processing {
					0%, 100% { transform: scale(1); opacity: 0.85; }
					50% { transform: scale(1.06); opacity: 1; }
				}
				.interview-root .state-complete #voice-circle {
					background-color: #22c55e;
					animation: breathe-complete 1.5s ease-in-out infinite;
					box-shadow: 0 0 45px rgba(34, 197, 94, 0.65);
				}
				.interview-root .state-complete .ring { display: none; }
				@keyframes breathe-complete {
					0%, 100% { transform: scale(1); }
					50% { transform: scale(1.08); }
				}
			`}</style>

			<div className="interview-root relative flex min-h-screen flex-col overflow-hidden bg-[#08090c] text-white antialiased">
				<nav className="fixed top-0 left-0 z-50 flex w-full items-center justify-between border-b border-white/5 glass-panel px-8 py-4">
					<div className="flex items-center gap-3">
						<img src={logoImg} alt="CareerBridge Logo" className="h-10 w-10 object-contain" />
						<span className="text-xl font-bold tracking-tight">
							CareerBridge <span className="text-[#FF0055]">AI</span>
						</span>
					</div>
					<div className="hidden gap-8 text-sm font-bold text-gray-300 md:flex">
						<button type="button" onClick={() => navigate('/dashboard')} className="transition hover:text-white">
							Dashboard
						</button>
						<span className="border-b-2 border-[#FF0055] pb-1 text-white">Interview</span>
						<button type="button" onClick={() => navigate('/messages')} className="transition hover:text-white">
							Messages
						</button>
					</div>
					<div className="flex items-center gap-6">
						<div className="hidden items-center gap-3 md:flex">
							<span className="text-sm font-semibold">{navUsername}</span>
							{photoUrl ? (
								<img
									src={photoUrl}
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

				<div className="relative mb-24 flex min-h-0 w-full flex-1 flex-col pt-[72px]">
					<div className="flex min-h-0 flex-1 flex-col items-center justify-center px-4">
						{error ? (
							<div className="mb-6 max-w-md px-4 text-center">
								<p className="text-red-400">{error}</p>
							</div>
						) : null}
						{micDenied ? (
							<p className="mb-4 max-w-md px-4 text-center text-amber-400">
								Please allow microphone access to continue
							</p>
						) : null}

						<div
							id="visualizer"
							className={`relative flex h-[450px] w-full items-center justify-center ${visualClass}`}
						>
							<div className="ring ring-1" />
							<div className="ring ring-2" />
							<div className="ring ring-3" />
							<div id="voice-circle" />
						</div>

						<p
							className={`mt-6 min-h-[2rem] text-center text-2xl font-black ${speakerLabelClass(phase)}`}
						>
							{speakerLabelText(phase)}
						</p>

						<div className="mt-4 flex min-h-[5rem] flex-col items-center justify-center gap-3">
							{showStart ? (
								<button
									type="button"
									className="btn-neon flex items-center gap-2 rounded-full px-10 py-4 text-sm font-bold tracking-widest uppercase"
									onClick={handleStartInterview}
								>
									<Mic className="h-5 w-5" />
									Start Interview
								</button>
							) : null}
							{showStop ? (
								<button
									type="button"
									className="btn-neon rounded-full px-10 py-4 text-sm font-bold tracking-widest uppercase"
									onClick={onStopSpeaking}
								>
									Stop Speaking
								</button>
							) : null}
						</div>
					</div>
				</div>

				<div
					className={`absolute bottom-12 left-0 flex w-full flex-col items-center transition-opacity duration-700 ${
						showProgress ? 'pointer-events-none opacity-100' : 'opacity-0'
					}`}
				>
					<div className="mb-3 flex items-center gap-4">
						{Array.from({ length: MAX_QUESTIONS }).map((_, i) => {
							const done = i < currentQuestionIndex
							const current = i === currentQuestionIndex && phase !== 'complete'
							if (done) {
								return <div key={i} className="h-3 w-3 rounded-full bg-[#FF0055]" />
							}
							if (current) {
								return (
									<div
										key={i}
										className="h-4 w-4 rounded-full bg-[#FF0055] shadow-[0_0_12px_#FF0055]"
									/>
								)
							}
							return (
								<div
									key={i}
									className="h-3 w-3 rounded-full border-2 border-gray-600 bg-transparent"
								/>
							)
						})}
					</div>
					<p className="text-[11px] font-bold tracking-widest text-gray-500 uppercase">
						Question {currentQuestionIndex + 1} of {MAX_QUESTIONS}
					</p>
				</div>
			</div>
		</>
	)
}
