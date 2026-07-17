import { useCallback, useEffect, useMemo, useState } from 'react';
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

  // Advanced filter panel.
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [groupByCompany, setGroupByCompany] = useState(false);
  const [companyFilter, setCompanyFilter] = useState('all');

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

  const companies = useMemo(() => {
    const set = new Set();
    for (const it of items) set.add(it.company || 'Unknown company');
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [items]);

  const visible = useMemo(() => {
    if (companyFilter === 'all') return items;
    return items.filter((it) => (it.company || 'Unknown company') === companyFilter);
  }, [items, companyFilter]);

  // When grouping, bucket the visible items by company (preserving current sort within a group).
  const groups = useMemo(() => {
    if (!groupByCompany) return null;
    const map = new Map();
    for (const it of visible) {
      const key = it.company || 'Unknown company';
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(it);
    }
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]));
  }, [visible, groupByCompany]);

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

          <button
            className="btn btn-small sidebar-advanced-toggle"
            onClick={() => setShowAdvanced((v) => !v)}
            aria-expanded={showAdvanced}
          >
            {showAdvanced ? '▾' : '▸'} Advanced filter
          </button>

          {showAdvanced && (
            <div className="sidebar-advanced">
              <label className="adv-check">
                <input
                  type="checkbox"
                  checked={groupByCompany}
                  onChange={(e) => setGroupByCompany(e.target.checked)}
                />
                <span>Group by company</span>
              </label>
              <label>
                <span>Company</span>
                <select value={companyFilter} onChange={(e) => setCompanyFilter(e.target.value)}>
                  <option value="all">All companies</option>
                  {companies.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          )}

          {loading && <div className="muted">Loading…</div>}
          {error && <div className="auth-error">{error}</div>}
          {!loading && !error && visible.length === 0 && (
            <div className="muted">No applications yet.</div>
          )}

          {!loading && !error && groups ? (
            groups.map(([company, list]) => (
              <div key={company} className="sidebar-group">
                <div className="sidebar-group-head">
                  <span className="sg-name">{company}</span>
                  <span className="count">{list.length}</span>
                </div>
                <ul className="sidebar-list">
                  {list.map((it) => (
                    <SidebarItem key={it.applicationId} it={it} hideCompany />
                  ))}
                </ul>
              </div>
            ))
          ) : (
            <ul className="sidebar-list">
              {visible.map((it) => (
                <SidebarItem key={it.applicationId} it={it} />
              ))}
            </ul>
          )}
        </div>
      )}
    </aside>
  );
}

function SidebarItem({ it, hideCompany }) {
  return (
    <li className="sidebar-item">
      <div className="si-title">{it.jobTitle || 'Untitled role'}</div>
      {!hideCompany && <div className="si-company">{it.company || 'Unknown company'}</div>}
      <div className="si-meta">
        <span className={`chip chip-${it.bucket}`}>{it.bucket}</span>
        {it.bucket === 'in_progress' && it.stage && <span className="chip chip-stage">{it.stage}</span>}
        {it.portalName && <span className="si-portal">{it.portalName}</span>}
      </div>
    </li>
  );
}
