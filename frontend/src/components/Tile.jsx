import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import { buildPostingPdfBlob } from '../lib/pdf';

const STATUS_OPTIONS = [
  { value: 'applied', label: 'Applied' },
  { value: 'in_progress', label: 'In Progress' },
  { value: 'complete', label: 'Complete' },
  { value: 'archived', label: 'Archived' },
];

const STAGE_OPTIONS = [
  { value: '', label: 'No sub-stage' },
  { value: 'assessment', label: 'Assessment' },
  { value: 'interviewing', label: 'Interviewing' },
];

const STAGE_LABELS = { assessment: 'Assessment', interviewing: 'Interviewing' };

function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  return d.toLocaleDateString();
}

// tile: full application object. onChanged: () => void (triggers board refetch).
export default function Tile({ tile, onChanged, onError }) {
  const [expanded, setExpanded] = useState(false);
  const [notes, setNotes] = useState(tile.notes || '');
  const [notesDirty, setNotesDirty] = useState(false);
  const [savingNotes, setSavingNotes] = useState(false);
  const [companyDesc, setCompanyDesc] = useState(tile.companyDescription || '');
  const [descDirty, setDescDirty] = useState(false);
  const [savingDesc, setSavingDesc] = useState(false);
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef(null);

  // Keep local editable state in sync when the tile is refetched from the server.
  useEffect(() => {
    setNotes(tile.notes || '');
    setNotesDirty(false);
  }, [tile.applicationId, tile.notes]);

  useEffect(() => {
    setCompanyDesc(tile.companyDescription || '');
    setDescDirty(false);
  }, [tile.applicationId, tile.companyDescription]);

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

  async function saveDescription() {
    setSavingDesc(true);
    try {
      await api.updateGeneral(tile.applicationId, { companyDescription: companyDesc });
      setDescDirty(false);
      if (onChanged) onChanged();
    } catch (err) {
      reportError(err);
    } finally {
      setSavingDesc(false);
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

  async function changeStage(stage) {
    if ((stage || '') === (tile.stage || '')) return;
    setBusy(true);
    try {
      await api.updateStage(tile.applicationId, stage || null);
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

  // Generate a PDF copy of the posting and store it via the existing S3 document flow.
  async function handleSavePdf() {
    setBusy(true);
    try {
      const blob = buildPostingPdfBlob(tile);
      const presign = await api.requestUploadUrl(tile.applicationId, 'application/pdf');
      const putUrl = presign && (presign.uploadUrl || presign.url);
      if (!putUrl) throw new Error('No upload URL returned.');
      await api.uploadFile(putUrl, blob, 'application/pdf');
      if (onChanged) onChanged();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  const showStage = tile.bucket === 'in_progress';
  const stageBadge = showStage && tile.stage ? STAGE_LABELS[tile.stage] : null;

  // Clicking the tile toggles expand — but never when the click lands on an interactive
  // control (buttons, links, form fields), so those keep working normally.
  function onCardClick(e) {
    if (e.target.closest('button, a, select, textarea, input, label')) return;
    setExpanded((v) => !v);
  }

  return (
    <article
      className={`tile tile-clickable${busy ? ' tile-busy' : ''}${expanded ? ' tile-expanded' : ''}`}
      onClick={onCardClick}
      title={expanded ? 'Click to collapse' : 'Click to expand'}
    >
      <header className="tile-head">
        <h3 className="tile-title">{tile.jobTitle || 'Untitled role'}</h3>
        {expanded ? (
          <button className="icon-btn" title="Close" onClick={() => setExpanded(false)} disabled={busy}>
            ×
          </button>
        ) : (
          stageBadge && <span className="chip chip-stage">{stageBadge}</span>
        )}
      </header>

      <div className="tile-company">
        {tile.company || 'Unknown company'}
        {tile.portalName && <span className="portal-badge">{tile.portalName}</span>}
      </div>

      <div className="tile-meta">
        <span>Job id: {tile.externalJobId || '—'}</span>
        <span>Applied: {formatDate(tile.dateApplied)}</span>
        {tile.hasDocument && <span className="chip chip-doc">Doc</span>}
        {tile.archived && <span className="chip chip-archived">Archived</span>}
      </div>

      <div className="tile-row">
        <label className="tile-status">
          <span>Status</span>
          <select value={tile.bucket} onChange={(e) => changeStatus(e.target.value)} disabled={busy}>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        {showStage && (
          <label className="tile-status">
            <span>Stage</span>
            <select value={tile.stage || ''} onChange={(e) => changeStage(e.target.value)} disabled={busy}>
              {STAGE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {/* Company blurb only shows in the expanded view. */}
      {expanded && (
        <div className="tile-field">
          <label>
            <span>About the company</span>
            <textarea
              rows={4}
              value={companyDesc}
              placeholder="A few sentences about the company…"
              onChange={(e) => {
                setCompanyDesc(e.target.value);
                setDescDirty(true);
              }}
            />
          </label>
          <button className="btn btn-small" onClick={saveDescription} disabled={savingDesc || !descDirty}>
            {savingDesc ? 'Saving…' : 'Save description'}
          </button>
        </div>
      )}

      {/* Notes stay on the small tile (like the original); expanding just gives more room. */}
      <div className="tile-notes">
        <label>
          <span>Notes</span>
          <textarea
            rows={expanded ? 6 : 2}
            value={notes}
            placeholder="Add notes…"
            onChange={(e) => {
              setNotes(e.target.value);
              setNotesDirty(true);
            }}
          />
        </label>
        <button className="btn btn-small" onClick={saveNotes} disabled={savingNotes || !notesDirty}>
          {savingNotes ? 'Saving…' : 'Save notes'}
        </button>
      </div>

      <footer className="tile-actions">
        {tile.jobUrl && (
          <a className="btn btn-small btn-link" href={tile.jobUrl} target="_blank" rel="noopener noreferrer">
            View posting
          </a>
        )}
        {expanded && (
          <>
            {tile.hasDocument ? (
              <button className="btn btn-small" onClick={handleDownload} disabled={busy}>
                Download doc
              </button>
            ) : (
              <button className="btn btn-small" onClick={triggerUpload} disabled={busy}>
                Upload doc
              </button>
            )}
            <button className="btn btn-small" onClick={handleSavePdf} disabled={busy} title="Generate a PDF copy and attach it to this tile">
              Save PDF copy
            </button>
            <button className="btn btn-small btn-danger" onClick={handleDelete} disabled={busy}>
              Delete
            </button>
          </>
        )}
        <input ref={fileInputRef} type="file" hidden onChange={handleFileSelected} />
      </footer>
    </article>
  );
}
