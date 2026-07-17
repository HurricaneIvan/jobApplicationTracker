import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth.jsx';
import { ApiError } from '../api/client';

export default function AuthPage() {
  const { login, signup } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState('login'); // 'login' | 'signup'
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const isLogin = mode === 'login';

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (isLogin) {
        await login(email.trim(), password);
      } else {
        await signup(email.trim(), password);
      }
      navigate('/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setError('That email is already registered. Try logging in.');
        } else if (err.status === 401) {
          setError('Invalid email or password.');
        } else {
          setError(err.message);
        }
      } else {
        setError('Could not reach the server. Is the gateway running?');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <form className="auth-card" onSubmit={handleSubmit}>
        <img className="auth-logo" src="/squirrel.svg" alt="" width="56" height="56" />
        <h1 className="auth-title">Job Application Tracker</h1>
        <p className="auth-sub">{isLogin ? 'Log in to your account' : 'Create an account'}</p>

        <label className="field">
          <span>Email</span>
          <input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>

        <label className="field">
          <span>Password</span>
          <input
            type="password"
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={isLogin ? undefined : 8}
            required
          />
        </label>

        {error && <div className="auth-error">{error}</div>}

        <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
          {busy ? 'Please wait…' : isLogin ? 'Log in' : 'Sign up'}
        </button>

        <button
          type="button"
          className="link-btn"
          onClick={() => {
            setError(null);
            setMode(isLogin ? 'signup' : 'login');
          }}
        >
          {isLogin ? "Don't have an account? Sign up" : 'Already have an account? Log in'}
        </button>
      </form>
    </div>
  );
}
