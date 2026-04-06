export default function UserAvatar({
	firstName,
	lastName,
	imagePath,
	refreshKey = 0,
	size = 'w-10 h-10',
	className = '',
	imgClassName = '',
}) {
	void firstName
	void lastName
	void refreshKey
	return (
		<img
			src={imagePath || '/default-avatar.png'}
			alt=""
			className={`${size} rounded-full border-2 border-[#FF0055] object-cover shadow-[0_0_10px_rgba(255,0,85,0.4)] ${className} ${imgClassName}`}
		/>
	)
}
