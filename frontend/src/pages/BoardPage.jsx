import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useAuth } from '../hooks/useAuth.jsx';
import Tile from '../components/Tile.jsx';
import Sidebar from '../components/Sidebar.jsx';

const COLUMNS = [
  { key: 'applied', title: 'Applied', bucket: 'applied' },
  { key: 'inProgress', title: 'In Progress', bucket: 'in_progress' },
  { key: 'complete', title: 'Complete', bucket: 'complete' },
  { key: 'archived', title: 'Archived', bucket: 'archived' },
];

const EMPTY_BOARD = { applied: [], inProgress: [], complete: [], archived: [] };

export default function BoardPage() {
  const { user, logout } = useAuth();

  const [board, setBoard] = useState(EMPTY_BOARD);
  const [portals, setPortals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [banner, setBanner] = useState(null); // transient error from tile actions
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [portalFilter, setPortalFilter] = useState('all');
  const [showCreate, setShowCreate] = useState(false);
  const [dragOverKey, setDragOverKey] = useState(null);

  const loadBoard = useCallback(async () => {
    setError(null);
    try {
      const data = await api.getBoard(true);
      setBoard({ ...EMPTY_BOARD, ...(data || {}) });
    } catch (err) {
      setError(err.message || 'Failed to load board.');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPortals = useCallback(async () => {
    try {
      const data = await api.listPortals();
      setPortals(Array.isArray(data) ? data : []);
    } catch {
      setPortals([]); // non-fatal
    }
  }, []);

  useEffect(() => {
    loadBoard();
    loadPortals();
  }, [loadBoard, loadPortals]);

  // Refetch board + sidebar after any mutation so tiles move columns.
  const refetch = useCallback(() => {
    loadBoard();
    setRefreshKey((k) => k + 1);
  }, [loadBoard]);

  const handleTileError = useCallback((msg) => {
    setBanner(msg);
    window.clearTimeout(handleTileError._t);
    handleTileError._t = window.setTimeout(() => setBanner(null), 5000);
  }, []);

  // Drag-and-drop: dropping a tile on a column moves it to that column's bucket.
  const handleDrop = useCallback(
    async (e, targetBucket) => {
      e.preventDefault();
      setDragOverKey(null);
      const id =
        e.dataTransfer.getData('application/x-app-id') || e.dataTransfer.getData('text/plain');
      const from = e.dataTransfer.getData('application/x-bucket');
      if (!id || from === targetBucket) return;
      try {
        await api.updateStatus(id, targetBucket);
        refetch();
      } catch (err) {
        handleTileError(err.message || 'Could not move the application.');
      }
    },
    [refetch, handleTileError],
  );

  // Portal filter options derived from the portal registry plus whatever
  // portal names actually appear on tiles (in case a tile references an
  // unlisted portal).
  const portalOptions = useMemo(() => {
    const names = new Set();
    for (const col of Object.values(board)) {
      for (const t of col || []) {
        if (t.portalName) names.add(t.portalName);
      }
    }
    for (const p of portals) {
      if (p.displayName) names.add(p.displayName);
    }
    return Array.from(names).sort();
  }, [board, portals]);

  function filterTiles(tiles) {
    if (portalFilter === 'all') return tiles;
    return (tiles || []).filter((t) => t.portalName === portalFilter);
  }

  return (
    <div className={`app-shell${sidebarCollapsed ? ' sidebar-is-collapsed' : ''}`}>
      <main className="board-main">
        <header className="topbar">
          <div className="topbar-left">
            <h1 className="brand">Job Application Tracker</h1>
          </div>
          <div className="topbar-right">
            <label className="portal-filter">
              <span>Portal</span>
              <select value={portalFilter} onChange={(e) => setPortalFilter(e.target.value)}>
                <option value="all">All portals</option>
                {portalOptions.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
            <button className="btn btn-primary" onClick={() => setShowCreate((s) => !s)}>
              {showCreate ? 'Close' : '+ New'}
            </button>
            <Link className="btn" to="/profile">
              Profile
            </Link>
            <span className="user-email">{user && user.email}</span>
            <button className="btn" onClick={logout}>
              Log out
            </button>
          </div>
        </header>

        {showCreate && (
          <CreateForm
            portals={portals}
            onCancel={() => setShowCreate(false)}
            onCreated={() => {
              setShowCreate(false);
              refetch();
            }}
            onError={handleTileError}
          />
        )}

        {banner && <div className="banner banner-error">{banner}</div>}

        {loading && <div className="board-status">Loading board…</div>}
        {error && !loading && (
          <div className="board-status">
            <div className="auth-error">{error}</div>
            <button className="btn" onClick={loadBoard}>
              Retry
            </button>
          </div>
        )}

        {!loading && !error && (
          <div className="board-columns">
            {COLUMNS.map((col) => {
              const tiles = filterTiles(board[col.key]);
              return (
                <section
                  key={col.key}
                  className={`column${dragOverKey === col.key ? ' column-dragover' : ''}`}
                  onDragOver={(e) => {
                    e.preventDefault();
                    e.dataTransfer.dropEffect = 'move';
                    if (dragOverKey !== col.key) setDragOverKey(col.key);
                  }}
                  onDragLeave={(e) => {
                    // Only clear when the pointer actually leaves the column, not its children.
                    if (!e.currentTarget.contains(e.relatedTarget)) setDragOverKey(null);
                  }}
                  onDrop={(e) => handleDrop(e, col.bucket)}
                >
                  <header className="column-head">
                    <h2>{col.title}</h2>
                    <span className="count">{tiles.length}</span>
                  </header>
                  <div className="column-body">
                    {tiles.length === 0 ? (
                      <div className="column-empty">Drag applications here</div>
                    ) : (
                      tiles.map((tile) => (
                        <Tile
                          key={tile.applicationId}
                          tile={tile}
                          onChanged={refetch}
                          onError={handleTileError}
                        />
                      ))
                    )}
                  </div>
                </section>
              );
            })}
          </div>
        )}
      </main>

      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed((c) => !c)}
        refreshKey={refreshKey}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Inline create-application form
// ---------------------------------------------------------------------------

function CreateForm({ portals, onCancel, onCreated, onError }) {
  const [jobTitle, setJobTitle] = useState('');
  const [company, setCompany] = useState('');
  const [jobUrl, setJobUrl] = useState('');
  const [externalJobId, setExternalJobId] = useState('');
  const [portalId, setPortalId] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    // Backend requires portalId, portalName, jobUrl, jobTitle, company (all @NotBlank).
    const portal = portals.find((p) => p.portalId === portalId);
    if (!portal) {
      onError('Please select a portal.');
      return;
    }
    if (!jobUrl.trim()) {
      onError('Job URL is required.');
      return;
    }
    setBusy(true);
    try {
      const payload = {
        jobTitle: jobTitle.trim(),
        company: company.trim(),
        jobUrl: jobUrl.trim(),
        externalJobId: externalJobId.trim() || null,
        portalId: portal.portalId,
        portalName: portal.displayName || portal.domain,
      };
      await api.create(payload);
      onCreated();
    } catch (err) {
      if (err instanceof ApiError && err.isConflict) {
        onError(`This application already exists${err.data && err.data.jobTitle ? `: ${err.data.jobTitle}` : ''}.`);
      } else {
        onError(err.message || 'Could not create application.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="create-form" onSubmit={submit}>
      <div className="create-grid">
        <label className="field">
          <span>Job title *</span>
          <input value={jobTitle} onChange={(e) => setJobTitle(e.target.value)} required />
        </label>
        <label className="field">
          <span>Company *</span>
          <input value={company} onChange={(e) => setCompany(e.target.value)} required />
        </label>
        <label className="field">
          <span>Job URL *</span>
          <input value={jobUrl} onChange={(e) => setJobUrl(e.target.value)} placeholder="https://…" required />
        </label>
        <label className="field">
          <span>Job ID (portal posting id)</span>
          <input value={externalJobId} onChange={(e) => setExternalJobId(e.target.value)} />
        </label>
        <label className="field">
          <span>Portal *</span>
          <select value={portalId} onChange={(e) => setPortalId(e.target.value)} required>
            <option value="">— select a portal —</option>
            {portals.map((p) => (
              <option key={p.portalId} value={p.portalId}>
                {p.displayName || p.domain}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="create-actions">
        <button className="btn btn-primary" type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create application'}
        </button>
        <button className="btn" type="button" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  );
}
