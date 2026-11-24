import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext.jsx';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await register(form.name, form.email, form.password);
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
      <div className="page form-page">
        <h1 className="page-title">Create your account</h1>
        <form onSubmit={submit} className="form">
          <label>Name
             <input value={form.name} onChange={set('name')} autoComplete="name" required maxLength={60} />
          </label>
          <label>Email
             <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
          </label>
          <label>Password
             <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" minLength={6} required />
             <small className="muted">At least 6 characters</small>
          </label>
          {error && <p className="notice notice-error">{error}</p>}
          <button className="btn btn-solid" disabled={busy}>{busy ? 'Creating…' : 'Sign up'}</button>
          <p className="muted">Already have an account? <Link to="/login" state={location.state}>Log in</Link></p>
        </form>
      </div>
   );
}
