import { useEffect, useState } from 'react'
import { getProfilePhoto } from '../api/api'

function initialsFromNames(firstName, lastName) {
	const a = (firstName?.[0] || '').toUpperCase()
	const b = (lastName?.[0] || '').toUpperCase()
	const s = `${a}${b}`
	return s || '?'
}

export default function UserAvatar({
	firstName,
	lastName,
	imagePath,
	refreshKey = 0,
	size = 'w-10 h-10',
	className = '',
	imgClassName = '',
}) {
	const [blobUrl, setBlobUrl] = useState(null)

	useEffect(() => {
		if (!imagePath) {
			setBlobUrl((prev) => {
				if (prev) URL.revokeObjectURL(prev)
				return null
			})
			return undefined
		}
		let cancelled = false
		;(async () => {
			try {
				const r = await getProfilePhoto()
				if (cancelled) return
				const ct = r.headers['content-type'] || 'image/jpeg'
				const blob = new Blob([r.data], { type: ct })
				const url = URL.createObjectURL(blob)
				setBlobUrl((prev) => {
					if (prev) URL.revokeObjectURL(prev)
					return url
				})
			} catch {
				if (!cancelled) {
					setBlobUrl((prev) => {
						if (prev) URL.revokeObjectURL(prev)
						return null
					})
				}
			}
		})()
		return () => {
			cancelled = true
			setBlobUrl((prev) => {
				if (prev) URL.revokeObjectURL(prev)
				return null
			})
		}
	}, [imagePath, refreshKey])

	const initials = initialsFromNames(firstName, lastName)
	const textClass = String(size).includes('h-28') ? 'text-2xl font-black leading-none' : 'text-xs font-bold leading-none'

	if (blobUrl) {
		return (
			<img
				src={blobUrl}
				alt=""
				className={`${size} rounded-full border-2 border-[#FF0055] object-cover shadow-[0_0_10px_rgba(255,0,85,0.4)] ${className} ${imgClassName}`}
			/>
		)
	}

	return (
		<div
			className={`flex ${size} items-center justify-center rounded-full border-2 border-[#FF0055] bg-[#1a1a2e] text-center font-bold text-white shadow-[0_0_10px_rgba(255,0,85,0.4)] ${className}`}
			aria-hidden
		>
			<span className={textClass}>{initials}</span>
		</div>
	)
}
