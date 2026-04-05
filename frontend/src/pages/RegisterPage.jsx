import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, CheckCircle, Loader2, Lock, Mail, Rocket, UserPlus } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import downloadImg from '../assets/download.png'

const F = "'Plus Jakarta Sans', sans-serif"
const BG = 'https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?q=80&w=1200&auto=format&fit=crop'
const GL = { background: 'rgba(255,255,255,0.03)', backdropFilter: 'blur(12px)', border: '1px solid rgba(255,255,255,0.05)' }

function getApiError(err) {
  const d = err.response?.data
  if (typeof d === 'string') return d
  if (d?.error) return d.error
  if (d?.message) return d.message
  return err.message || 'Registration failed'
}

export default function RegisterPage() {
  const { register, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => { if (isAuthenticated) navigate('/dashboard', { replace: true }) }, [isAuthenticated, navigate])

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (password !== confirmPassword) { setError('Passwords do not match.'); return }
    setLoading(true)
    try {
      await register(firstName.trim(), lastName.trim(), email.trim(), password)
      navigate('/dashboard', { replace: true })
    } catch (err) { setError(getApiError(err)) } finally { setLoading(false) }
  }

  const inputStyle = { backgroundColor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.1)', color: '#fff', transition: 'all 0.3s ease', width: '100%', borderRadius: '0.75rem', padding: '0.75rem 1rem', fontSize: '0.875rem', fontFamily: F, outline: 'none' }
  const inputIconStyle = { ...inputStyle, paddingLeft: '2.5rem' }
  const labelStyle = { display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.5rem', fontFamily: F }
  const iconPos = { position: 'absolute', left: '1rem', top: '50%', transform: 'translateY(-50%)', width: '1rem', height: '1rem', color: '#6b7280' }

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;600;800;900&display=swap');
        body { font-family: 'Plus Jakarta Sans', sans-serif !important; background-color: #08090c !important; color: #fff !important; }
        .rp-input:focus { border-color: #FF0055 !important; box-shadow: 0 0 10px rgba(255,0,85,0.2) !important; }
        .rp-input::placeholder { color: #6b7280; }
        .rp-btn { background:linear-gradient(90deg,#FF0055,#d40046); box-shadow:0 0 20px rgba(255,0,85,0.4); transition:all 0.3s ease; border:none; cursor:pointer; color:#fff; font-family:'Plus Jakarta Sans',sans-serif; }
        .rp-btn:hover:not(:disabled) { box-shadow:0 0 35px rgba(255,0,85,0.7); transform:translateY(-2px); }
        .rp-btn:disabled { opacity:0.6; cursor:not-allowed; transform:none; }
        .rp-link { color:#f87171; font-weight:700; text-decoration:none; transition:color 0.2s; }
        .rp-link:hover { color:#fca5a5; }
        .rp-back { color:#9ca3af; text-decoration:none; font-size:0.875rem; font-weight:500; display:flex; align-items:center; gap:0.5rem; transition:color 0.2s; font-family:'Plus Jakarta Sans',sans-serif; }
        .rp-back:hover { color:#fff; }
        .rp-tg { background:linear-gradient(90deg,#FF0055,#FF5500); -webkit-background-clip:text; background-clip:text; -webkit-text-fill-color:transparent; }
        @keyframes spin { to { transform: rotate(360deg); } }
        .rp-spin { animation: spin 1s linear infinite; }
        @media(max-width:1023px) { .rp-left { display:none!important; } .rp-right { width:100%!important; } }
      `}</style>

      <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: '#08090c', color: '#fff', fontFamily: F, overflowX: 'hidden' }}>

        {/* NAV */}
        <nav style={{ ...GL, position: 'fixed', top: 0, left: 0, width: '100%', zIndex: 50, padding: '1rem 2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <img src={downloadImg} alt="CareerBridge Logo" style={{ width: '2.5rem', height: '2.5rem', objectFit: 'contain' }} />
            <span style={{ fontSize: '1.25rem', fontWeight: 700, letterSpacing: '-0.025em', fontFamily: F }}>CareerBridge <span style={{ color: '#ef4444' }}>AI</span></span>
          </div>
          <Link to="/" className="rp-back"><ArrowLeft style={{ width: '1rem', height: '1rem' }} /> Back to Home</Link>
        </nav>

        {/* CONTENT */}
        <div style={{ display: 'flex', flex: 1, paddingTop: '72px' }}>

          {/* LEFT PANEL */}
          <div className="rp-left" style={{ display: 'flex', width: '50%', position: 'relative', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', borderRight: '1px solid rgba(255,255,255,0.05)' }}>
            <div style={{ position: 'absolute', inset: 0, backgroundColor: 'rgba(8,9,12,0.7)', zIndex: 10 }} />
            <img src={BG} alt="Future of Work" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', filter: 'grayscale(1)', opacity: 0.6 }} />
            <div style={{ position: 'relative', zIndex: 20, textAlign: 'center', padding: '0 3rem' }}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.375rem 1rem', marginBottom: '1.5rem', border: '1px solid rgba(239,68,68,0.3)', background: 'rgba(239,68,68,0.1)', borderRadius: '9999px', color: '#f87171', fontSize: '0.875rem', fontWeight: 700, fontFamily: F }}>
                <Rocket style={{ width: '1rem', height: '1rem' }} /> Start Your Future Today
              </div>
              <h2 style={{ fontSize: '3rem', fontWeight: 900, lineHeight: 1.25, marginBottom: '1.5rem', color: '#fff', fontFamily: F }}>
                Welcome to the <br /><span className="rp-tg">Future Skills Guild</span>
              </h2>
              <p style={{ color: '#9ca3af', fontSize: '1.125rem', maxWidth: '28rem', margin: '0 auto', fontFamily: F }}>Join the ecosystem that transforms job loss into a new career path using Artificial Intelligence.</p>
            </div>
          </div>

          {/* RIGHT PANEL */}
          <div className="rp-right" style={{ width: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '2rem', backgroundColor: '#0c0d12', position: 'relative', overflow: 'hidden' }}>
            <div style={{ position: 'absolute', top: 0, right: 0, width: '24rem', height: '24rem', background: 'rgba(239,68,68,0.1)', filter: 'blur(100px)', borderRadius: '9999px', pointerEvents: 'none' }} />

            <div style={{ ...GL, width: '100%', maxWidth: '28rem', padding: '2.5rem', borderRadius: '1.5rem', position: 'relative', zIndex: 10 }}>
              <div style={{ textAlign: 'center', marginBottom: '2.5rem' }}>
                <img src={downloadImg} alt="CareerBridge Logo" style={{ width: '4rem', height: '4rem', objectFit: 'contain', margin: '0 auto 1rem' }} />
                <h3 style={{ fontSize: '1.875rem', fontWeight: 900, marginBottom: '0.5rem', color: '#fff', fontFamily: F }}>Create Account</h3>
                <p style={{ fontSize: '0.875rem', color: '#9ca3af', fontFamily: F }}>Join CareerBridge AI and start your transition journey.</p>
              </div>

              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                <div>
                  <label htmlFor="fullname" style={labelStyle}>FisrtName</label>
                  <input type="text" id="fullname" name="firstName" autoComplete="given-name" required value={firstName} onChange={e => setFirstName(e.target.value)} placeholder="e.g. Alex Doe" className="rp-input" style={inputStyle} />
                </div>
                <div>
                  <label htmlFor="lastname" style={labelStyle}>LastName</label>
                  <input type="text" id="lastname" name="lastName" autoComplete="family-name" required value={lastName} onChange={e => setLastName(e.target.value)} placeholder="e.g. Alex Doe" className="rp-input" style={inputStyle} />
                </div>
                <div>
                  <label htmlFor="email" style={labelStyle}>Email</label>
                  <div style={{ position: 'relative' }}>
                    <Mail style={iconPos} />
                    <input type="email" id="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="name@example.com" className="rp-input" style={inputIconStyle} />
                  </div>
                </div>
                <div>
                  <label htmlFor="password" style={labelStyle}>Password</label>
                  <div style={{ position: 'relative' }}>
                    <Lock style={iconPos} />
                    <input type="password" id="password" autoComplete="new-password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" className="rp-input" style={inputIconStyle} />
                  </div>
                </div>
                <div>
                  <label htmlFor="confirm" style={labelStyle}>Confirm Password</label>
                  <div style={{ position: 'relative' }}>
                    <CheckCircle style={iconPos} />
                    <input type="password" id="confirm" autoComplete="new-password" required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} placeholder="••••••••" className="rp-input" style={inputIconStyle} />
                  </div>
                </div>

                {error && <p style={{ textAlign: 'center', fontSize: '0.875rem', color: '#f87171', fontFamily: F }} role="alert">{error}</p>}

                <button type="submit" disabled={loading} className="rp-btn" style={{ width: '100%', padding: '1rem', borderRadius: '0.75rem', fontSize: '1.125rem', fontWeight: 700, letterSpacing: '0.025em', marginTop: '0.5rem', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem' }}>
                  {loading ? (<><Loader2 className="rp-spin" style={{ width: '1.25rem', height: '1.25rem' }} /> Creating…</>) : (<>Create Account <UserPlus style={{ width: '1.25rem', height: '1.25rem' }} /></>)}
                </button>
              </form>

              <p style={{ textAlign: 'center', fontSize: '0.875rem', color: '#9ca3af', marginTop: '2rem', fontFamily: F }}>
                Already have an account? <Link to="/login" className="rp-link">Login</Link>
              </p>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
