import { useNavigate } from 'react-router-dom'
import { AlertTriangle, ArrowRight, CheckCircle, ShieldCheck, HeartHandshake, Mic } from 'lucide-react'
import logoImg from '../assets/download.png'
import robotImg from '../assets/robot.png'
import statImg from '../assets/statistique.png'
import stat2Img from '../assets/statistique2.png'
import roadImg from '../assets/road.png'

const S = {
  // reusable style atoms matching Tailwind CDN exactly
  font: "'Plus Jakarta Sans', sans-serif",
  glass: { background: 'rgba(255,255,255,0.03)', backdropFilter: 'blur(12px)', border: '1px solid rgba(255,255,255,0.05)' },
}

export default function HomePage() {
  const navigate = useNavigate()

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;600;800;900&display=swap');
        body { font-family: 'Plus Jakarta Sans', sans-serif !important; background-color: #08090c !important; color: #fff !important; overflow-x: hidden; }
        .hp-tg { background: linear-gradient(90deg,#FF0055,#FF5500); -webkit-background-clip:text; background-clip:text; -webkit-text-fill-color:transparent; }
        .hp-btn { background:linear-gradient(90deg,#FF0055,#d40046); box-shadow:0 0 20px rgba(255,0,85,0.4); transition:all 0.3s ease; border:none; cursor:pointer; font-family:'Plus Jakarta Sans',sans-serif; color:#fff; text-decoration:none; display:inline-flex; align-items:center; }
        .hp-btn:hover { box-shadow:0 0 35px rgba(255,0,85,0.7); transform:translateY(-2px); }
        .hp-nl { color:#9ca3af; text-decoration:none; transition:color 0.2s; font-family:'Plus Jakarta Sans',sans-serif; font-size:0.875rem; font-weight:500; }
        .hp-nl:hover { color:#fff; }
        .hp-ll { color:#fff; text-decoration:none; font-size:0.875rem; font-weight:600; font-family:'Plus Jakarta Sans',sans-serif; transition:color 0.2s; }
        .hp-ll:hover { color:#ef4444; }
        .hp-gi { filter:grayscale(1); transition:filter 0.5s; width:100%; border-radius:1rem; position:relative; z-index:10; border:1px solid #374151; box-shadow:0 25px 50px -12px rgba(0,0,0,0.5); }
        .hp-gi:hover { filter:grayscale(0); }
        .hp-card:hover .hp-co { background:transparent !important; }
        .hp-card:hover .hp-ci { filter:grayscale(0) !important; transform:scale(1.1) !important; }
        .hp-co { position:absolute; inset:0; z-index:10; transition:background 0.3s; }
        .hp-ci { width:100%; height:100%; object-fit:cover; filter:grayscale(1); transition:filter 0.5s, transform 0.5s; }
        @media(max-width:767px){ .hp-nd{display:none!important} }
        @media(min-width:768px){
          .hp-hs { padding-left:5rem!important; padding-right:5rem!important; }
          .hp-hw { width:50%!important; opacity:0.4!important; }
          .hp-h1 { font-size:4.5rem!important; }
          .hp-hp { font-size:1.25rem!important; }
          .hp-ps { padding-left:5rem!important; padding-right:5rem!important; }
          .hp-pg { grid-template-columns:repeat(3,1fr)!important; }
          .hp-gs { padding-left:5rem!important; padding-right:5rem!important; }
          .hp-gi2 { flex-direction:row!important; padding:4rem!important; }
          .hp-g3 { font-size:3rem!important; }
          .hp-rs { padding-left:5rem!important; padding-right:5rem!important; }
          .hp-es { padding-left:5rem!important; padding-right:5rem!important; }
          .hp-ei { padding:4rem!important; }
          .hp-ch { font-size:3.75rem!important; }
          .hp-dh { font-size:3rem!important; }
        }
      `}</style>

      <div style={{ backgroundColor: '#08090c', color: '#fff', fontFamily: S.font, overflowX: 'hidden' }}>

        {/* ── NAV ── */}
        <nav style={{ ...S.glass, position: 'fixed', top: 0, left: 0, width: '100%', zIndex: 50, padding: '1rem 2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <img src={logoImg} alt="CareerBridge Logo" style={{ width: '4rem', height: '4rem', objectFit: 'contain' }} />
            <span style={{ fontSize: '1.25rem', fontWeight: 700, letterSpacing: '-0.025em' }}>CareerBridge <span style={{ color: '#ef4444' }}>AI</span></span>
          </div>
          <div className="hp-nd" style={{ display: 'flex', gap: '2rem' }}>
            <a href="#problem" className="hp-nl">The Problem</a>
            <a href="#guild" className="hp-nl">The Guild</a>
            <a href="#ethics" className="hp-nl">Ethics</a>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <a href="#" onClick={e => { e.preventDefault(); navigate('/login') }} className="hp-ll hp-nd">Login</a>
            <button onClick={() => navigate('/register')} className="hp-btn" style={{ padding: '0.5rem 1.5rem', borderRadius: '9999px', fontSize: '0.875rem', fontWeight: 700, letterSpacing: '0.025em' }}>Launch Demo</button>
          </div>
        </nav>

        {/* ── HERO ── */}
        <section className="hp-hs" style={{ position: 'relative', minHeight: '100vh', display: 'flex', alignItems: 'center', paddingTop: '5rem', paddingLeft: '2rem', paddingRight: '2rem', overflow: 'hidden' }}>
          <div style={{ position: 'relative', zIndex: 10, maxWidth: '56rem' }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.375rem 1rem', marginBottom: '1.5rem', border: '1px solid rgba(239,68,68,0.3)', background: 'rgba(239,68,68,0.1)', borderRadius: '9999px', color: '#f87171', fontSize: '0.875rem', fontWeight: 700 }}>
              <AlertTriangle style={{ width: '1rem', height: '1rem' }} />
              80% of local factory jobs are being automated now.
            </div>
            <h1 className="hp-h1" style={{ fontSize: '3rem', fontWeight: 900, lineHeight: 1.25, marginBottom: '1.5rem', color: '#fff', fontFamily: S.font, letterSpacing: '-0.02em' }}>
              Welcome to the <br />
              <span className="hp-tg">Future Skills Guild</span>
            </h1>
            <p className="hp-hp" style={{ color: '#9ca3af', fontSize: '1.125rem', maxWidth: '42rem', marginBottom: '2.5rem', lineHeight: '1.625', fontFamily: S.font }}>
              Automation is displacing workers faster than ever. We don't just retrain; we rebuild futures by creating a dedicated ecosystem that transforms job loss into a new career path using Artificial Intelligence.
            </p>
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <a href="#roadmap" onClick={() => navigate('/register')} className="hp-btn" style={{ padding: '1rem 2rem', borderRadius: '0.75rem', fontWeight: 700, fontSize: '1.125rem', letterSpacing: '0.025em', gap: '0.5rem' }}>
                Start Your Journey <ArrowRight style={{ width: '1.25rem', height: '1.25rem' }} />
              </a>
            </div>
          </div>
          <div className="hp-hw" style={{ position: 'absolute', right: 0, top: 0, height: '100%', width: '100%', opacity: 0.2, maskImage: 'linear-gradient(to left, black 30%, transparent 100%)', WebkitMaskImage: 'linear-gradient(to left, black 30%, transparent 100%)' }}>
            <img src="https://images.unsplash.com/photo-1518432031352-d6fc5c10da5a?q=80&w=2000" alt="Automation Risk" style={{ height: '100%', width: '100%', objectFit: 'cover', filter: 'grayscale(1)' }} />
          </div>
        </section>

        {/* ── PROBLEM ── */}
        <section id="problem" className="hp-ps" style={{ padding: '6rem 2rem', backgroundColor: '#0c0d12' }}>
          <div style={{ textAlign: 'center', marginBottom: '4rem' }}>
            <h2 style={{ fontSize: '2.25rem', fontWeight: 900, marginBottom: '1rem', color: '#fff', fontFamily: S.font }}>The Human Cost of Automation</h2>
            <p style={{ color: '#9ca3af', maxWidth: '42rem', margin: '0 auto', fontFamily: S.font }}>This is not a hypothetical scenario. We rely on current global economic data to define the urgency of this industrial transition.</p>
          </div>
          <div className="hp-pg" style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '2rem' }}>

            <div className="hp-card" style={{ ...S.glass, borderRadius: '1rem', overflow: 'hidden' }}>
              <div style={{ height: '12rem', overflow: 'hidden', position: 'relative' }}>
                <div className="hp-co" style={{ background: 'rgba(127,29,29,0.4)' }} />
                <img src={robotImg} alt="Robotic arm in factory" className="hp-ci" />
              </div>
              <div style={{ padding: '2rem' }}>
                <span style={{ color: '#ef4444', fontWeight: 900, fontSize: '2.25rem', display: 'block', marginBottom: '0.5rem', fontFamily: S.font }}>-20 Million</span>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '0.75rem', color: '#fff', fontFamily: S.font }}>The Manufacturing Threat</h3>
                <p style={{ color: '#9ca3af', fontSize: '0.875rem', marginBottom: '1rem', lineHeight: 1.6, fontFamily: S.font }}>By 2030, up to 20 million manufacturing jobs worldwide will be displaced by industrial robots. 1.7 million have already disappeared since 2000.</p>
                <p style={{ fontSize: '0.75rem', color: '#4b5563', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', fontFamily: S.font }}>Source: Oxford Economics</p>
              </div>
            </div>

            <div className="hp-card" style={{ ...S.glass, borderRadius: '1rem', overflow: 'hidden', border: '1px solid rgba(239,68,68,0.3)' }}>
              <div style={{ height: '12rem', overflow: 'hidden', position: 'relative' }}>
                <div className="hp-co" style={{ background: 'rgba(69,10,10,0.6)' }} />
                <img src={statImg} alt="Manufacturing employment statistics" className="hp-ci" />
              </div>
              <div style={{ padding: '2rem' }}>
                <span style={{ color: '#f87171', fontWeight: 900, fontSize: '1.875rem', display: 'block', marginBottom: '0.5rem', textTransform: 'uppercase', fontFamily: S.font }}>-35% Jobs</span>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '0.75rem', color: '#fff', fontFamily: S.font }}>The "Rust Belt" Collapse</h3>
                <p style={{ color: '#9ca3af', fontSize: '0.875rem', marginBottom: '1rem', lineHeight: 1.6, fontFamily: S.font }}>U.S. manufacturing employment dropped from 18.9 million to 12.2 million (over a 35% decline) between 1980 and 2014, driven by automation and structural industry shifts.</p>
                <p style={{ fontSize: '0.75rem', color: '#4b5563', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', fontFamily: S.font }}>Source: Brookings Institution</p>
              </div>
            </div>

            <div className="hp-card" style={{ ...S.glass, borderRadius: '1rem', overflow: 'hidden' }}>
              <div style={{ height: '12rem', overflow: 'hidden', position: 'relative' }}>
                <div className="hp-co" style={{ background: 'rgba(17,24,39,0.6)' }} />
                <img src={stat2Img} alt="Advanced technology training" className="hp-ci" />
              </div>
              <div style={{ padding: '2rem' }}>
                <span style={{ color: '#d1d5db', fontWeight: 900, fontSize: '2.25rem', display: 'block', marginBottom: '0.5rem', fontFamily: S.font }}>+170 Million</span>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '0.75rem', color: '#fff', fontFamily: S.font }}>The Skills Paradox</h3>
                <p style={{ color: '#9ca3af', fontSize: '0.875rem', marginBottom: '1rem', lineHeight: 1.6, fontFamily: S.font }}>While millions of roles disappear, 170 million new types of jobs will emerge by 2030. Our technology is the mandatory bridge between these two realities.</p>
                <p style={{ fontSize: '0.75rem', color: '#4b5563', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', fontFamily: S.font }}>Source: World Economic Forum 2025</p>
              </div>
            </div>

          </div>
        </section>

        {/* ── GUILD ── */}
        <section id="guild" className="hp-gs" style={{ padding: '6rem 2rem', position: 'relative' }}>
          <div className="hp-gi2" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '3rem', background: 'linear-gradient(to right,#12141c,#0c0d12)', border: '1px solid #1f2937', padding: '2.5rem', borderRadius: '1.5rem' }}>
            <div style={{ flex: 1 }}>
              <p style={{ fontSize: '0.875rem', fontWeight: 700, color: '#ef4444', letterSpacing: '0.1em', textTransform: 'uppercase', marginBottom: '0.5rem', fontFamily: S.font }}>Our X-Factor / The Creative Solution</p>
              <h3 className="hp-g3" style={{ fontSize: '2.25rem', fontWeight: 900, marginBottom: '1.5rem', color: '#fff', fontFamily: S.font }}>Learning 2.0</h3>
              <p style={{ color: '#d1d5db', fontSize: '1.125rem', marginBottom: '1.5rem', lineHeight: '1.625', fontFamily: S.font }}>We help workers transition into new careers using AI. But this is not a boring corporate training seminar.</p>
              <p style={{ color: '#9ca3af', marginBottom: '2rem', fontFamily: S.font, lineHeight: '1.625' }}>We created a <strong>Guild</strong>. A motivating, community-driven, and immersive ecosystem where every worker finds a strong identity, personalized coaching, and a new sense of professional pride.</p>
              <ul style={{ padding: 0, margin: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '1rem', fontSize: '0.875rem', color: '#d1d5db', fontFamily: S.font }}>
                <li style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}><CheckCircle style={{ color: '#22c55e', width: '1.25rem', height: '1.25rem', flexShrink: 0 }} /> Personalized AI-driven skill analysis</li>
                <li style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}><CheckCircle style={{ color: '#22c55e', width: '1.25rem', height: '1.25rem', flexShrink: 0 }} /> Community-driven peer support system</li>
                <li style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}><CheckCircle style={{ color: '#22c55e', width: '1.25rem', height: '1.25rem', flexShrink: 0 }} /> Realistic immersive job simulations</li>
              </ul>
            </div>
            <div style={{ flex: 1, width: '100%', position: 'relative' }}>
              <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top right,#ef4444,#f97316)', borderRadius: '1rem', filter: 'blur(40px)', opacity: 0.1 }} />
              <img src="https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=800" alt="Guild Collaboration" className="hp-gi" />
            </div>
          </div>
        </section>

        {/* ── ROADMAP ── */}
        <section id="roadmap" className="hp-rs" style={{ padding: '6rem 2rem', backgroundColor: '#0c0d12' }}>
          <h2 style={{ textAlign: 'center', fontSize: '2.25rem', fontWeight: 900, marginBottom: '5rem', color: '#fff', fontFamily: S.font }}>Your Requalification Pathway</h2>
          <div style={{ maxWidth: '64rem', margin: '0 auto', display: 'flex', justifyContent: 'center' }}>
            <img src={roadImg} alt="Transition Roadmap Steps" style={{ width: '100%', height: 'auto', objectFit: 'contain', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)', borderRadius: '1rem', border: '1px solid rgba(255,255,255,0.05)' }} />
          </div>
        </section>

        {/* ── ETHICS ── */}
        <section id="ethics" className="hp-es" style={{ padding: '6rem 2rem', backgroundColor: '#08090c' }}>
          <div className="hp-ei" style={{ ...S.glass, maxWidth: '56rem', margin: '0 auto', padding: '2.5rem', borderRadius: '1.5rem', borderLeft: '4px solid #ef4444' }}>
            <h2 style={{ fontSize: '1.875rem', fontWeight: 900, marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '1rem', color: '#f87171', fontFamily: S.font }}>
              <ShieldCheck style={{ color: '#ef4444', width: '2rem', height: '2rem', flexShrink: 0 }} /> Our Ethical Commitment
            </h2>
            <p style={{ color: '#d1d5db', fontSize: '1.125rem', marginBottom: '1.5rem', lineHeight: '1.625', fontFamily: S.font }}>As engineers, we acknowledge the limitations of our technology. Artificial Intelligence is a powerful analysis tool, but <strong>it can make errors or exhibit biases.</strong></p>
            <p style={{ color: '#9ca3af', lineHeight: '1.625', marginBottom: '1.5rem', fontFamily: S.font }}>That is why CareerBridge is designed based on the "Human-in-the-loop" principle. The AI proposes, but <strong>the user retains absolute control</strong> at every stage. No decision is automated without human validation. Data privacy is strictly protected, and the user can modify their preferences at any time.</p>
          </div>
        </section>

        {/* ── SECOND CHANCE ── */}
        <section style={{ padding: '8rem 2rem', textAlign: 'center', position: 'relative', overflow: 'hidden' }}>
          <div style={{ position: 'absolute', inset: 0, backgroundImage: "url('https://images.unsplash.com/photo-1531482615713-2afd69097998?q=80&w=2000')", opacity: 0.05, backgroundSize: 'cover', backgroundPosition: 'center' }} />
          <div style={{ position: 'relative', zIndex: 10, maxWidth: '48rem', margin: '0 auto' }}>
            <HeartHandshake style={{ width: '4rem', height: '4rem', color: '#ef4444', display: 'block', margin: '0 auto 2rem' }} />
            <h2 className="hp-ch" style={{ fontSize: '2.25rem', fontWeight: 900, marginBottom: '2rem', lineHeight: 1.25, color: '#fff', fontFamily: S.font }}>
              This is not just technology. <br />
              <span className="hp-tg">It's a second chance.</span>
            </h2>
            <p style={{ fontSize: '1.25rem', color: '#d1d5db', marginBottom: '3rem', fontFamily: S.font }}>Restoring dignity and social utility to workers in a world dominated by automation. This is the true power of responsible engineering.</p>
          </div>
        </section>

        {/* ── DEMO ── */}
        <section id="demo" style={{ padding: '5rem 2rem', textAlign: 'center', background: 'linear-gradient(to bottom,#0c0d12,#160a0a)', borderTop: '1px solid rgba(239,68,68,0.2)', borderBottom: '1px solid rgba(239,68,68,0.2)' }}>
          <h2 className="hp-dh" style={{ fontSize: '1.875rem', fontWeight: 900, marginBottom: '1.5rem', color: '#fff', fontFamily: S.font }}>Go Practical Now</h2>
          <p style={{ color: '#9ca3af', maxWidth: '36rem', margin: '0 auto 2.5rem', fontFamily: S.font }}>Discover how the interactive agent dialogues with workers to map their future.</p>
          <button onClick={() => navigate('/register')} className="hp-btn" style={{ padding: '1.25rem 2.5rem', borderRadius: '0.75rem', fontWeight: 900, fontSize: '1.25rem', textTransform: 'uppercase', letterSpacing: '0.1em', gap: '0.75rem', margin: '0 auto' }}>
            <Mic style={{ width: '1.5rem', height: '1.5rem' }} /> Start Simulation
          </button>
        </section>

        {/* ── FOOTER ── */}
        <footer style={{ padding: '2rem', borderTop: '1px solid #1f2937', textAlign: 'center', color: '#4b5563', fontSize: '0.875rem', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1rem', fontFamily: S.font }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700, color: 'rgba(255,255,255,0.5)' }}>
            <img src={logoImg} alt="CareerBridge Logo" style={{ width: '4rem', height: '4rem', objectFit: 'contain', opacity: 0.5 }} />
            CareerBridge AI
          </div>
          <p>Engineering Project - Group 3: The Ecosystem of Transition to Automation</p>
        </footer>

      </div>
    </>
  )
}
