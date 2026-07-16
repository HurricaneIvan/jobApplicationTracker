import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';

const SORT_OPTIONS = [
  { value: 'dateApplied', label: 'Date applied' },
  { value: 'title', label: 'Title' },
  { value: 'status', label: 'Status' },
];

// refreshKey: bump to force a refetch after board mutations.
// The sidebar endpoint already excludes archived tiles — do not re-add them.
export default function Sidebar({ collapsed, onToggle, refreshKey }) {
  const [items, setItems] = useState([]);
  const [sort, setSort] = useState('dateApplied');
  const [order, setOrder] = useState('desc');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getSidebar(sort, order);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || 'Failed to load list.');
    } finally {
      setLoading(false);
    }
  }, [sort, order]);

  useEffect(() => {
    if (!collapsed) load();
  }, [load, collapsed, refreshKey]);

  return (
    <aside className={`sidebar${collapsed ? ' sidebar-collapsed' : ''}`}>
      <button
        className="sidebar-toggle"
        onClick={onToggle}
        title={collapsed ? 'Expand list' : 'Collapse list'}
      >
        {collapsed ? '‹' : '›'}
      </button>

      {!collapsed && (
        <div className="sidebar-body">
          <h2 className="sidebar-title">All applications</h2>

          <div className="sidebar-controls">
            <label>
              <span>Sort</span>
              <select value={sort} onChange={(e) => setSort(e.target.value)}>
                {SORT_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="btn btn-small"
              onClick={() => setOrder((o) => (o === 'asc' ? 'desc' : 'asc'))}
              title="Toggle sort direction"
            >
              {order === 'asc' ? 'Asc ↑' : 'Desc ↓'}
            </button>
          </div>

          {loading && <div className="muted">Loading…</div>}
          {error && <div className="auth-error">{error}</div>}
          {!loading && !error && items.length === 0 && (
            <div className="muted">No applications yet.</div>
          )}

          <ul className="sidebar-list">
            {items.map((it) => (
              <li key={it.applicationId} className="sidebar-item">
                <div className="si-title">{it.jobTitle || 'Untitled role'}</div>
                <div className="si-company">{it.company || 'Unknown company'}</div>
                <div className="si-meta">
                  <span className={`chip chip-${it.bucket}`}>{it.bucket}</span>
                  {it.portalName && <span className="si-portal">{it.portalName}</span>}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </aside>
  );
}
