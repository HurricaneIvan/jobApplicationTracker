import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';

const STATUS_OPTIONS = [
  { value: 'applied', label: 'Applied' },
  { value: 'in_progress', label: 'In Progress' },
  { value: 'complete', label: 'Complete' },
  { value: 'archived', label: 'Archived' },
];

function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  return d.toLocaleDateString();
}

// tile: full application object. onChanged: () => void (triggers board refetch).
export default function Tile({ tile, onChanged, onError }) {
  const [notes, setNotes] = useState(tile.notes || '');
  const [savingNotes, setSavingNotes] = useState(false);
  const [notesDirty, setNotesDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef(null);

  // Keep local notes in sync when the tile is refetched from the server.
  useEffect(() => {
    setNotes(tile.notes || '');
    setNotesDirty(false);
  }, [tile.applicationId, tile.notes]);

  function reportError(err) {
    const msg =
      err instanceof ApiError
        ? err.isConflict
          ? `Conflict: ${err.message}`
          : err.message
        : 'Something went wrong.';
    if (onError) onError(msg);
  }

  async function saveNotes() {
    setSavingNotes(true);
    try {
      await api.updateNotes(tile.applicationId, notes);
      setNotesDirty(false);
      if (onChanged) onChanged();
    } catch (err) {
      reportError(err);
    } finally {
      setSavingNotes(false);
    }
  }

  async function changeStatus(bucket) {
    if (bucket === tile.bucket) return;
    setBusy(true);
    try {
      await api.updateStatus(tile.applicationId, bucket);
      if (onChanged) onChanged();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm(`Delete "${tile.jobTitle || 'this application'}"?`)) return;
    setBusy(true);
    try {
      await api.remove(tile.applicationId);
      if (onChanged) onChanged();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  async function handleDownload() {
    setBusy(true);
    try {
      const res = await api.getDownloadUrl(tile.applicationId);
      const url = res && (res.downloadUrl || res.url);
      if (url) {
        window.open(url, '_blank', 'noopener');
      } else {
        reportError(new Error('No download URL returned.'));
      }
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  function triggerUpload() {
    if (fileInputRef.current) fileInputRef.current.click();
  }

  async function handleFileSelected(e) {
    const file = e.target.files && e.target.files[0];
    e.target.value = ''; // allow re-selecting the same file later
    if (!file) return;
    setBusy(true);
    try {
      const contentType = file.type || 'application/octet-stream';
      const presign = await api.requestUploadUrl(tile.applicationId, contentType);
      const putUrl = presign && (presign.uploadUrl || presign.url);
      if (!putUrl) throw new Error('No upload URL returned.');
      await api.uploadFile(putUrl, file, contentType);
      if (onChanged) onChanged();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className={`tile${busy ? ' tile-busy' : ''}`}>
      <header className="tile-head">
        <h3 className="tile-title">{tile.jobTitle || 'Untitled role'}</h3>
        <button
          className="icon-btn"
          title="Delete application"
          onClick={handleDelete}
          disabled={busy}
        >
          ×
        </button>
      </header>

      <div className="tile-company">
        {tile.company || 'Unknown company'}
        {tile.portalName && <span className="portal-badge">{tile.portalName}</span>}
      </div>

      <dl className="tile-ids">
        <div>
          <dt>App ID</dt>
          <dd title={tile.applicationId}>{tile.applicationId}</dd>
        </div>
        <div>
          <dt>Job ID</dt>
          <dd>{tile.externalJobId != null && tile.externalJobId !== '' ? tile.externalJobId : '—'}</dd>
        </div>
      </dl>

      <div className="tile-meta">
        <span>Applied: {formatDate(tile.dateApplied)}</span>
        {tile.archived && <span className="chip chip-archived">Archived</span>}
      </div>

      <div className="tile-row">
        <label className="tile-status">
          <span>Status</span>
          <select
            value={tile.bucket}
            onChange={(e) => changeStatus(e.target.value)}
            disabled={busy}
          >
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="tile-notes">
        <label>
          <span>Notes</span>
          <textarea
            rows={2}
            value={notes}
            placeholder="Add notes…"
            onChange={(e) => {
              setNotes(e.target.value);
              setNotesDirty(true);
            }}
          />
        </label>
        <button
          className="btn btn-small"
          onClick={saveNotes}
          disabled={savingNotes || !notesDirty}
        >
          {savingNotes ? 'Saving…' : 'Save notes'}
        </button>
      </div>

      <footer className="tile-actions">
        {tile.hasDocument ? (
          <button className="btn btn-small" onClick={handleDownload} disabled={busy}>
            Download doc
          </button>
        ) : (
          <button className="btn btn-small" onClick={triggerUpload} disabled={busy}>
            Upload doc
          </button>
        )}
        <input
          ref={fileInputRef}
          type="file"
          hidden
          onChange={handleFileSelected}
        />
        {tile.jobUrl && (
          <a
            className="btn btn-small btn-link"
            href={tile.jobUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            View posting
          </a>
        )}
      </footer>
    </article>
  );
}
