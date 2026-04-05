import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import {
  changePassword, getCurrentInterview, getDashboardStats,
  getPostInterviewMessages, getProfile, getProfilePhoto,
  updateProfile, uploadProfilePhoto,
} from '../api/api'
import { useAuth } from '../context/AuthContext'
import UserAvatar from '../components/UserAvatar'
import logoImg from '../assets/download.png'

const F = "'Plus Jakarta Sans', sans-serif"
const GL = { background: 'rgba(255,255,255,0.03)', backdropFilter: 'blur(12px)', WebkitBackdropFilter: 'blur(12px)', border: '1px solid rgba(255,255,255,0.05)' }
const NE = '#FF0055'

function getApiError(err) {
  const d = err.response?.data
  if (typeof d === 'string') return d
  if (d?.error) return d.error
  if (d?.message) return d.message
  return err.message || 'Request failed'
}

const RANK_LABEL = { APPRENTI: 'Apprenti', ARTISAN: 'Artisan', COMPAGNON: 'Compagnon', MAITRE: 'Maître' }

function formatFormationStatus(s) {
  switch (s) {
    case 'NOT_SELECTED': return 'Not selected'
    case 'IN_PROGRESS': return 'In Progress'
    case 'COMPLETED': return 'Completed'
    default: return s || '—'
  }
}

function parseMessagePreview(content) {
  if (content == null || content === '') return '—'
  const trimmed = String(content).trim()
  try { const j = JSON.parse(trimmed); if (j && typeof j === 'object' && j.message) return String(j.message) } catch { /* not JSON */ }
  return trimmed.length > 180 ? `${trimmed.slice(0, 180)}…` : trimmed
}

function formatTime(iso) {
  if (!iso) return ''
  try { return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) } catch { return '' }
}

function formatRelativeTime(iso) {
  if (!iso) return ''
  try {
    const d = new Date(iso), now = new Date(), hours = (now - d) / 3600000
    if (hours < 24) return `${Math.max(1, Math.floor(hours))} hours ago`
    const days = Math.floor(hours / 24)
    if (days === 1) return 'Yesterday'
    if (days < 7) return `${days} days ago`
    return formatTime(iso)
  } catch { return '' }
}

function nextActionFromStats(stats) {
  if (!stats) return null
  if (stats.acceptedCandidatures >= 1) return { title: 'Your Next Step', body: 'Congratulations! You have been accepted — open your inbox for details and next steps.', button: 'Open Messages', to: '/messages' }
  if (stats.formationStatus === 'IN_PROGRESS') return { title: 'Your Next Step', body: 'Your training is in progress. Check your inbox for updates and tasks.', button: 'Open Messages', to: '/messages' }
  if (stats.interviewStatus === 'COMPLETED' && stats.formationStatus === 'NOT_SELECTED') return { title: 'Your Next Step', body: 'Your interview is complete. Check your message inbox for your skill analysis and career recommendations.', button: 'Open My Inbox', to: '/messages' }
  if (stats.interviewStatus === 'IN_PROGRESS') return { title: 'Your Next Step', body: 'Continue your voice interview where you left off.', button: 'Continue Interview', to: '/interview' }
  if (stats.interviewStatus === 'NOT_STARTED') return { title: 'Your Next Step', body: 'Start your voice interview to build your skill profile and unlock recommendations.', button: 'Start your voice interview', to: '/interview' }
  return { title: 'Your Next Step', body: 'Explore the interview and messages sections to move forward.', button: 'Go to Messages', to: '/messages' }
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { logout, updateUser, user } = useAuth()

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [profile, setProfile] = useState(null)
  const [stats, setStats] = useState(null)
  const [recentMessages, setRecentMessages] = useState([])
  const [photoKey, setPhotoKey] = useState(0)
  const [photoTimestamp, setPhotoTimestamp] = useState(0)
  const [photoUrl, setPhotoUrl] = useState(null)
  const [photoUploading, setPhotoUploading] = useState(false)
  const [photoSuccess, setPhotoSuccess] = useState('')
  const [photoError, setPhotoError] = useState('')

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [formMessage, setFormMessage] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true); setError('')
      try {
        const [profRes, statsRes, curRes] = await Promise.all([getProfile(), getDashboardStats(), getCurrentInterview()])
        if (cancelled) return
        const p = profRes.data; setProfile(p); setFirstName(p.firstName || ''); setLastName(p.lastName || ''); setStats(statsRes.data)
        const iid = curRes.data?.interviewId
        if (iid) { try { const msgRes = await getPostInterviewMessages(iid); if (!cancelled) setRecentMessages((msgRes.data || []).slice(-3).reverse()) } catch { setRecentMessages([]) } }
        else setRecentMessages([])
      } catch (e) { if (!cancelled) setError(getApiError(e)) } finally { if (!cancelled) setLoading(false) }
    }
    load()
    return () => { cancelled = true }
  }, [])

  const loadProfilePhoto = useCallback(async () => {
    if (!profile?.imagePath) { setPhotoUrl(p => { if (p) URL.revokeObjectURL(p); return null }); return }
    try {
      const res = await getProfilePhoto()
      const blob = new Blob([res.data], { type: res.headers['content-type'] || 'image/jpeg' })
      const url = URL.createObjectURL(blob)
      setPhotoUrl(p => { if (p) URL.revokeObjectURL(p); return url })
    } catch { setPhotoUrl(p => { if (p) URL.revokeObjectURL(p); return null }) }
  }, [profile?.imagePath])

  useEffect(() => {
    if (!profile?.imagePath) { setPhotoUrl(p => { if (p) URL.revokeObjectURL(p); return null }); return }
    loadProfilePhoto()
  }, [profile?.imagePath, photoTimestamp, loadProfilePhoto])

  async function handleSave(e) {
    e.preventDefault(); setFormMessage(''); setFormError(''); setSaving(true)
    try {
      const nameChanged = profile && (firstName.trim() !== profile.firstName || lastName.trim() !== profile.lastName)
      if (nameChanged) { await updateProfile({ firstName: firstName.trim(), lastName: lastName.trim() }); setProfile(p => p ? { ...p, firstName: firstName.trim(), lastName: lastName.trim() } : p); updateUser({ firstName: firstName.trim(), lastName: lastName.trim() }) }
      if (currentPassword && newPassword) { await changePassword({ currentPassword, newPassword }); setCurrentPassword(''); setNewPassword('') }
      if (!nameChanged && !(currentPassword && newPassword)) setFormError('Nothing to save.')
      else { setFormMessage('Saved successfully.'); setFormError('') }
    } catch (err) { setFormError(getApiError(err)) } finally { setSaving(false) }
  }

  async function handlePhotoUpload(event) {
    const file = event.target.files?.[0]; if (event.target) event.target.value = ''; if (!file) return
    setPhotoError(''); setPhotoSuccess('')
    try {
      setPhotoUploading(true); const formData = new FormData(); formData.append('file', file)
      const res = await uploadProfilePhoto(formData); const up = res.data; setProfile(up); setPhotoKey(k => k + 1); setPhotoTimestamp(Date.now())
      updateUser({ ...user, firstName: up.firstName, lastName: up.lastName, imagePath: up.imagePath })
      setPhotoSuccess('Photo updated successfully!'); setTimeout(() => setPhotoSuccess(''), 3000)
    } catch { setPhotoError('Failed to upload photo. Please try again.') } finally { setPhotoUploading(false) }
  }

  const next = nextActionFromStats(stats)
  const rankLabel = stats ? RANK_LABEL[stats.guildRank] || stats.guildRank : ''
  const interviewCount = stats?.interviewsCount ?? 0
  const interviewCardValue = interviewCount > 0 ? `${interviewCount} Session${interviewCount > 1 ? 's' : ''}` : 'Not Started'
  const interviewPillDone = interviewCount > 0
  const displayName = profile ? `${profile.firstName} ${profile.lastName}` : '—'
  const navUsername = user ? `${(user.firstName?.[0] || '').toUpperCase()}.${(user.lastName || '').toUpperCase()}` : ''

  const inputSt = { backgroundColor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.1)', color: '#fff', transition: 'all 0.3s ease', width: '100%', borderRadius: '0.75rem', padding: '0.625rem 1rem', fontSize: '0.875rem', fontFamily: F, outline: 'none' }
  const labelSt = { display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#9ca3af', marginBottom: '0.25rem', textTransform: 'uppercase', fontFamily: F }
  const pillBase = { marginTop: '1rem', display: 'inline-block', padding: '0.25rem 0.75rem', borderRadius: '9999px', fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }

  const navLinkSt = (to) => {
    const active = location.pathname === to
    return { fontSize: '0.875rem', fontWeight: 700, color: active ? '#fff' : '#d1d5db', borderBottom: active ? `2px solid ${NE}` : 'none', paddingBottom: active ? '4px' : 0, background: 'none', border: active ? undefined : 'none', cursor: 'pointer', transition: 'color 0.2s', fontFamily: F, textDecoration: 'none' }
  }

  const msgBtnSt = { border: `1px solid rgba(255,0,85,0.5)`, color: NE, background: 'transparent', padding: '0.5rem 1rem', borderRadius: '0.5rem', fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', cursor: 'pointer', transition: 'all 0.2s', fontFamily: F }
  const msgBtnAltSt = { ...msgBtnSt, border: '1px solid rgba(255,255,255,0.2)', color: '#d1d5db' }

  function badgeSt(status) {
    if (status === 'COMPLETED') return { ...pillBase, background: 'rgba(34,197,94,0.1)', border: '1px solid rgba(34,197,94,0.3)', color: '#4ade80' }
    if (status === 'IN_PROGRESS') return { ...pillBase, background: 'rgba(249,115,22,0.1)', border: '1px solid rgba(249,115,22,0.3)', color: '#fb923c' }
    return { ...pillBase, background: 'rgba(96,165,250,0.1)', border: '1px solid rgba(96,165,250,0.3)', color: '#60a5fa' }
  }

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;600;700;800;900&display=swap');
        body { font-family: 'Plus Jakarta Sans', sans-serif !important; background-color: #08090c !important; color: #fff !important; }
        .dp-input:focus { border-color: #FF0055 !important; box-shadow: 0 0 10px rgba(255,0,85,0.2) !important; }
        .dp-input::placeholder { color: #6b7280; }
        .dp-btn { background:linear-gradient(90deg,#FF0055,#d40046); box-shadow:0 0 20px rgba(255,0,85,0.4); transition:all 0.3s ease; border:none; cursor:pointer; color:#fff; font-family:'Plus Jakarta Sans',sans-serif; }
        .dp-btn:hover:not(:disabled) { box-shadow:0 0 35px rgba(255,0,85,0.7); transform:translateY(-2px); }
        .dp-btn:disabled { opacity:0.6; cursor:not-allowed; }
        .dp-tg { background:linear-gradient(90deg,#FF0055,#FF5500); -webkit-background-clip:text; background-clip:text; -webkit-text-fill-color:transparent; }
        .dp-card:hover { background: rgba(255,255,255,0.05) !important; }
        .dp-msg:hover { border-color: rgba(255,0,85,0.3) !important; }
        .dp-mbtn:hover { background: #FF0055 !important; color: #fff !important; }
        .dp-mbtn2:hover { background: rgba(255,255,255,0.1) !important; }
        .dp-photo-label:hover .dp-photo-img { opacity: 0.4 !important; }
        .dp-photo-label:hover .dp-photo-overlay { opacity: 1 !important; }
        .dp-logout:hover { color: #FF0055 !important; }
        .dp-viewall:hover { color: #fff !important; }
        @media(max-width:767px) { .dp-nd { display:none!important; } }
        @media(min-width:1024px) { .dp-layout { flex-direction:row!important; } .dp-left { width:30%!important; } .dp-right { width:70%!important; } }
        @media(min-width:768px) { .dp-stats { grid-template-columns:repeat(3,1fr)!important; } .dp-next { flex-direction:row!important; align-items:center!important; padding:2.5rem!important; } .dp-h1 { font-size:3rem!important; } }
      `}</style>

      <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: '#08090c', color: '#fff', fontFamily: F, overflowX: 'hidden' }}>

        {/* NAV */}
        <nav style={{ ...GL, position: 'fixed', top: 0, left: 0, width: '100%', zIndex: 50, padding: '1rem 2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <img src={logoImg} alt="CareerBridge Logo" style={{ width: '2.5rem', height: '2.5rem', objectFit: 'contain' }} />
            <span style={{ fontSize: '1.25rem', fontWeight: 700, letterSpacing: '-0.025em', fontFamily: F }}>CareerBridge <span style={{ color: NE }}>AI</span></span>
          </div>
          <div className="dp-nd" style={{ display: 'flex', gap: '2rem' }}>
            <button type="button" onClick={() => navigate('/dashboard')} style={navLinkSt('/dashboard')}>Dashboard</button>
            <button type="button" onClick={() => navigate('/interview')} style={navLinkSt('/interview')}>Interview</button>
            <button type="button" onClick={() => navigate('/messages')} style={navLinkSt('/messages')}>Messages</button>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <div className="dp-nd" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <span style={{ fontSize: '0.875rem', fontWeight: 600, fontFamily: F }}>{navUsername}</span>
              <UserAvatar firstName={profile?.firstName ?? firstName} lastName={profile?.lastName ?? lastName} imagePath={profile?.imagePath ?? null} refreshKey={photoTimestamp} imgClassName="border-2 border-neon bg-gray-800 object-cover" />
            </div>
            <button type="button" onClick={() => logout()} className="dp-logout" style={{ fontSize: '0.875rem', fontWeight: 700, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.1em', background: 'none', border: 'none', cursor: 'pointer', transition: 'color 0.2s', fontFamily: F }}>Logout</button>
          </div>
        </nav>

        {/* CONTENT */}
        <div className="dp-layout" style={{ flex: 1, width: '100%', maxWidth: '1536px', margin: '0 auto', paddingTop: '100px', padding: '100px 2rem 2rem', display: 'flex', flexDirection: 'column', gap: '2rem' }}>

          {/* LEFT SIDEBAR */}
          <div className="dp-left" style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '2rem' }}>
            <div style={{ ...GL, padding: '2rem', borderRadius: '1.5rem', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>

              {/* PHOTO */}
              <div style={{ position: 'relative', cursor: 'pointer', marginBottom: '1rem' }}>
                <label htmlFor={`profile-upload-${photoKey}`} className="dp-photo-label" style={{ cursor: 'pointer', position: 'relative', display: 'block', borderRadius: '9999px', overflow: 'hidden', border: `4px solid ${NE}`, boxShadow: `0 0 15px rgba(255,0,85,0.4)`, backgroundColor: '#1f2937', opacity: photoUploading ? 0.6 : 1, pointerEvents: photoUploading ? 'none' : 'auto' }}>
                  {photoUrl ? (
                    <img src={photoUrl} alt={displayName} className="dp-photo-img" style={{ width: '7rem', height: '7rem', objectFit: 'cover', transition: 'opacity 0.3s' }} />
                  ) : (
                    <div className="dp-photo-img" style={{ width: '7rem', height: '7rem', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#1f2937', transition: 'opacity 0.3s' }}>
                      <span style={{ fontSize: '1.875rem', fontWeight: 900, color: '#fff' }}>{(profile?.firstName?.[0] || '').toUpperCase()}{(profile?.lastName?.[0] || '').toUpperCase()}</span>
                    </div>
                  )}
                  <div className="dp-photo-overlay" style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0, transition: 'opacity 0.3s', background: 'rgba(0,0,0,0.4)' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 900, color: NE, textTransform: 'uppercase', letterSpacing: '0.1em', padding: '0.25rem 0.5rem' }}>Update</span>
                  </div>
                </label>
                <input id={`profile-upload-${photoKey}`} type="file" style={{ display: 'none' }} accept="image/*" disabled={photoUploading} onChange={handlePhotoUpload} />
              </div>

              <h2 style={{ fontSize: '1.5rem', fontWeight: 900, letterSpacing: '-0.025em', fontFamily: F, color: '#fff' }}>{displayName}</h2>

              {photoError && <p style={{ marginTop: '0.5rem', textAlign: 'center', fontSize: '0.875rem', color: '#f87171' }}>{photoError}</p>}
              {photoSuccess && <p style={{ marginTop: '0.5rem', textAlign: 'center', fontSize: '0.875rem', color: '#4ade80' }}>{photoSuccess}</p>}

              {stats && (
                <div style={{ marginTop: '0.75rem', background: `rgba(255,0,85,0.1)`, border: `1px solid rgba(255,0,85,0.5)`, color: NE, padding: '0.375rem 1.25rem', borderRadius: '9999px', fontSize: '0.75rem', fontWeight: 900, textTransform: 'uppercase', letterSpacing: '0.1em', fontFamily: F }}>{rankLabel}</div>
              )}

              {stats && (
                <div style={{ width: '100%', marginTop: '2rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '10px', fontWeight: 700, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.5rem', fontFamily: F }}>
                    <span>Guild Progress</span>
                    <span style={{ color: NE }}>{stats.guildProgressPercent}%</span>
                  </div>
                  <div style={{ width: '100%', height: '0.5rem', background: 'rgba(255,255,255,0.05)', borderRadius: '9999px', overflow: 'hidden' }}>
                    <div style={{ height: '100%', background: NE, width: `${stats.guildProgressPercent}%`, boxShadow: '0 0 10px rgba(255,0,85,0.8)', borderRadius: '9999px' }} />
                  </div>
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', fontSize: '10px', fontWeight: 700, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em', marginTop: '1rem', fontFamily: F }}>
                <span style={{ color: '#fff' }}>Apprenti</span><span>→</span><span>Artisan</span><span>→</span><span>Compagnon</span><span>→</span><span>Maître</span>
              </div>

              <hr style={{ width: '100%', border: 'none', borderTop: '1px solid rgba(255,255,255,0.05)', margin: '2rem 0' }} />

              <form onSubmit={handleSave} style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <h3 style={{ fontSize: '0.875rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: '#d1d5db', marginBottom: '0.5rem', fontFamily: F }}>Edit Profile</h3>
                <div style={{ display: 'flex', gap: '1rem' }}>
                  <div style={{ flex: 1 }}>
                    <label style={labelSt}>First Name</label>
                    <input type="text" value={firstName} onChange={e => setFirstName(e.target.value)} className="dp-input" style={inputSt} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label style={labelSt}>Last Name</label>
                    <input type="text" value={lastName} onChange={e => setLastName(e.target.value)} className="dp-input" style={inputSt} />
                  </div>
                </div>
                <div><label style={labelSt}>Current Password</label><input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} placeholder="••••••••" className="dp-input" style={inputSt} /></div>
                <div><label style={labelSt}>New Password</label><input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="••••••••" className="dp-input" style={inputSt} /></div>
                {formError && <p style={{ textAlign: 'center', fontSize: '0.875rem', color: '#f87171' }}>{formError}</p>}
                {formMessage && <p style={{ textAlign: 'center', fontSize: '0.875rem', color: '#4ade80' }}>{formMessage}</p>}
                <button type="submit" disabled={saving} className="dp-btn" style={{ width: '100%', padding: '0.75rem', borderRadius: '0.75rem', fontSize: '0.875rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.1em', marginTop: '0.5rem' }}>{saving ? 'Saving…' : 'Save Changes'}</button>
              </form>
            </div>
          </div>

          {/* RIGHT MAIN */}
          <div className="dp-right" style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '2rem' }}>
            {loading ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
                <div style={{ height: '3rem', width: '66%', borderRadius: '0.5rem', background: 'rgba(255,255,255,0.1)' }} />
                <div className="dp-stats" style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '1.5rem' }}>
                  <div style={{ height: '9rem', borderRadius: '1.5rem', background: 'rgba(255,255,255,0.05)' }} />
                  <div style={{ height: '9rem', borderRadius: '1.5rem', background: 'rgba(255,255,255,0.05)' }} />
                  <div style={{ height: '9rem', borderRadius: '1.5rem', background: 'rgba(255,255,255,0.05)' }} />
                </div>
                <div style={{ height: '10rem', borderRadius: '1.5rem', background: 'rgba(255,255,255,0.05)' }} />
              </div>
            ) : error ? (
              <p style={{ color: '#f87171' }}>{error}</p>
            ) : (
              <>
                {/* WELCOME */}
                <div>
                  <h1 className="dp-h1" style={{ fontSize: '2.25rem', fontWeight: 900, letterSpacing: '-0.025em', fontFamily: F, color: '#fff' }}>Welcome back, <span className="dp-tg">{profile?.firstName || '—'}</span></h1>
                  <p style={{ color: '#9ca3af', fontSize: '1.125rem', marginTop: '0.5rem', fontFamily: F }}>Here's your transition journey overview</p>
                </div>

                {/* STATS CARDS */}
                <div className="dp-stats" style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '1.5rem' }}>
                  <div className="dp-card" style={{ ...GL, padding: '1.5rem', borderRadius: '1.5rem', borderTop: `2px solid ${NE}`, transition: 'background 0.2s' }}>
                    <p style={{ fontSize: '0.75rem', color: '#9ca3af', textTransform: 'uppercase', fontWeight: 700, letterSpacing: '0.1em', fontFamily: F }}>Voice Interviews</p>
                    <p style={{ fontSize: '1.5rem', fontWeight: 900, marginTop: '0.5rem', fontFamily: F }}>{interviewCardValue}</p>
                    <div style={interviewPillDone ? { ...pillBase, background: 'rgba(34,197,94,0.1)', border: '1px solid rgba(34,197,94,0.3)', color: '#4ade80' } : { ...pillBase, background: 'rgba(107,114,128,0.1)', border: '1px solid rgba(107,114,128,0.3)', color: '#9ca3af' }}>{interviewPillDone ? 'Done' : 'Pending'}</div>
                  </div>
                  <div className="dp-card" style={{ ...GL, padding: '1.5rem', borderRadius: '1.5rem', borderTop: `2px solid ${NE}`, transition: 'background 0.2s' }}>
                    <p style={{ fontSize: '0.75rem', color: '#9ca3af', textTransform: 'uppercase', fontWeight: 700, letterSpacing: '0.1em', fontFamily: F }}>Selected Training</p>
                    <p style={{ fontSize: '1.5rem', fontWeight: 900, marginTop: '0.5rem', fontFamily: F }}>{formatFormationStatus(stats?.formationStatus)}</p>
                    {stats?.formationTitle && <p style={{ marginTop: '0.5rem', fontSize: '0.875rem', color: '#9ca3af', fontFamily: F }}>{stats.formationTitle}</p>}
                    <div style={badgeSt(stats?.formationStatus)}>{stats?.formationStatus === 'COMPLETED' ? 'Done' : stats?.formationStatus === 'IN_PROGRESS' ? 'Ongoing' : 'None'}</div>
                  </div>
                  <div className="dp-card" style={{ ...GL, padding: '1.5rem', borderRadius: '1.5rem', borderTop: `2px solid ${NE}`, transition: 'background 0.2s' }}>
                    <p style={{ fontSize: '0.75rem', color: '#9ca3af', textTransform: 'uppercase', fontWeight: 700, letterSpacing: '0.1em', fontFamily: F }}>Job Applications</p>
                    <p style={{ fontSize: '1.5rem', fontWeight: 900, marginTop: '0.5rem', fontFamily: F }}>{stats?.candidaturesCount ?? 0} Sent</p>
                    <div style={{ ...pillBase, background: 'rgba(96,165,250,0.1)', border: '1px solid rgba(96,165,250,0.3)', color: '#60a5fa' }}>Active</div>
                  </div>
                </div>

                {/* NEXT STEP */}
                {next && (
                  <div className="dp-next" style={{ ...GL, padding: '2rem', borderRadius: '1.5rem', borderLeft: `4px solid ${NE}`, background: `linear-gradient(to right, rgba(255,0,85,0.05), transparent)`, display: 'flex', flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'space-between', gap: '2rem' }}>
                    <div>
                      <h3 style={{ fontSize: '1.5rem', fontWeight: 900, marginBottom: '0.5rem', fontFamily: F, color: '#fff' }}>{next.title}</h3>
                      <p style={{ color: '#d1d5db', lineHeight: 1.625, maxWidth: '36rem', fontFamily: F }}>{next.body}</p>
                    </div>
                    <button type="button" onClick={() => navigate(next.to)} className="dp-btn" style={{ padding: '1rem 2rem', borderRadius: '0.75rem', fontSize: '0.875rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.1em', whiteSpace: 'nowrap' }}>{next.button}</button>
                  </div>
                )}

                {/* RECENT MESSAGES */}
                <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '0.5rem' }}>
                    <h3 style={{ fontSize: '1.25rem', fontWeight: 900, fontFamily: F, color: '#fff' }}>Recent Messages</h3>
                    <Link to="/messages" className="dp-viewall" style={{ color: NE, fontSize: '0.875rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', textDecoration: 'none', transition: 'color 0.2s', fontFamily: F }}>View All</Link>
                  </div>

                  {stats?.interviewStatus === 'NOT_STARTED' ? (
                    <div style={{ ...GL, borderRadius: '1rem', padding: '1.5rem', fontSize: '0.875rem', color: '#9ca3af' }}>Complete your interview to see messages</div>
                  ) : recentMessages.length === 0 ? (
                    <div style={{ ...GL, borderRadius: '1rem', padding: '1.5rem', fontSize: '0.875rem', color: '#9ca3af' }}>No recent messages yet.</div>
                  ) : recentMessages.map((m, mi) => {
                    const rel = formatRelativeTime(m.createdAt ?? m.sentAt)
                    const preview = parseMessagePreview(m.content)
                    const variant = mi % 3
                    return (
                      <div key={m.id ?? mi} className="dp-msg" style={{ ...GL, padding: '1.5rem', borderRadius: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem', transition: 'border-color 0.2s' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span style={{ color: NE, fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.1em', fontFamily: F }}>{m.sender === 'BOT' ? 'CareerBridge AI' : 'You'}</span>
                          <span style={{ color: '#6b7280', fontSize: '0.75rem', fontWeight: 600, fontFamily: F }}>{rel || formatTime(m.createdAt)}</span>
                        </div>
                        <p style={{ fontSize: '0.875rem', lineHeight: 1.625, color: '#d1d5db', fontFamily: F }}>{preview}</p>
                        {variant === 0 && (
                          <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.75rem' }}>
                            <button type="button" onClick={() => navigate('/messages')} className="dp-mbtn" style={msgBtnSt}>View Full Analysis</button>
                          </div>
                        )}
                        {variant === 1 && (
                          <div style={{ marginTop: '0.5rem', display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
                            <button type="button" onClick={() => navigate('/messages')} className="dp-mbtn" style={msgBtnSt}>View Sectors</button>
                            <button type="button" onClick={() => navigate('/messages')} className="dp-mbtn2" style={msgBtnAltSt}>Refine Preferences</button>
                          </div>
                        )}
                        {variant === 2 && (
                          <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.75rem' }}>
                            <button type="button" onClick={() => navigate('/messages')} className="dp-mbtn" style={msgBtnSt}>Track Application</button>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
