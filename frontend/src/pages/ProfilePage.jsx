import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../hooks/useAuth.jsx';
import ThemeSelect from '../components/ThemeSelect.jsx';
import Brand from '../components/Brand.jsx';

const FIELDS = [
  { key: 'firstName', label: 'First name' },
  { key: 'lastName', label: 'Last name' },
  { key: 'phone', label: 'Phone' },
  { key: 'location', label: 'Location' },
  { key: 'headline', label: 'Headline', full: true },
];

const EMPTY = { firstName: '', lastName: '', phone: '', location: '', headline: '' };

export default function ProfilePage() {
  const { logout } = useAuth();
  const [form, setForm] = useState(EMPTY);
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const p = await api.getProfile();
        if (!alive) return;
        setEmail(p.email || '');
        setForm({
          firstName: p.firstName || '',
          lastName: p.lastName || '',
          phone: p.phone || '',
          location: p.location || '',
          headline: p.headline || '',
        });
      } catch (err) {
        if (alive) setError(err.message || 'Failed to load profile.');
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  function setField(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
    setSaved(false);
  }

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const p = await api.updateProfile(form);
      setForm({
        firstName: p.firstName || '',
        lastName: p.lastName || '',
        phone: p.phone || '',
        location: p.location || '',
        headline: p.headline || '',
      });
      setSaved(true);
    } catch (err) {
      setError(err.message || 'Could not save profile.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="app-shell">
      <main className="board-main">
        <header className="topbar">
          <div className="topbar-left">
            <Brand title="Profile" />
          </div>
          <div className="topbar-right">
            <ThemeSelect />
            <Link className="btn" to="/">
              ← Board
            </Link>
            <span className="user-email">{email}</span>
            <button className="btn" onClick={logout}>
              Log out
            </button>
          </div>
        </header>

        {loading ? (
          <div className="board-status">Loading profile…</div>
        ) : (
          <form className="profile-form" onSubmit={submit}>
            {error && <div className="auth-error">{error}</div>}
            {saved && <div className="banner banner-ok">Profile saved.</div>}

            <label className="field">
              <span>Email (read-only)</span>
              <input value={email} readOnly disabled />
            </label>

            <div className="profile-grid">
              {FIELDS.map((f) => (
                <label key={f.key} className={`field${f.full ? ' field-full' : ''}`}>
                  <span>{f.label}</span>
                  <input
                    value={form[f.key]}
                    onChange={(e) => setField(f.key, e.target.value)}
                    placeholder={f.label}
                  />
                </label>
              ))}
            </div>

            <div className="create-actions">
              <button className="btn btn-primary" type="submit" disabled={saving}>
                {saving ? 'Saving…' : 'Save profile'}
              </button>
            </div>
          </form>
        )}
      </main>
    </div>
  );
}
