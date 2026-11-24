import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext.jsx';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login(email, password);
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page form-page">
      <h1 className="page-title">Log in</h1>
      <form onSubmit={submit} className="form">
        <label>Email
           <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </label>
        <label>Password
           <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        {error && <p className="notice notice-error">{error}</p>}
        <button className="btn btn-solid" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
        <p className="muted">New here? <Link to="/register" state={location.state}>Create an account</Link></p>
      </form>

       <aside className="demo-box">
         <strong>Demo accounts</strong>
         <button type="button" onClick={() => { setEmail('student@booknest.com'); setPassword('student123'); }}>
            Student <small>student@booknest.com / student123</small>
         </button>
         <button type="button" onClick={() => { setEmail('admin@booknest.com'); setPassword('admin123'); }}>
            Admin <small>admin@booknest.com / admin123</small>
         </button>
       </aside>
     </div>
  );
}
