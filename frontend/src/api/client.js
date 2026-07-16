// Centralized API client for the Job Application Tracker frontend.
//
// - Reads/writes JWT tokens in localStorage.
// - Attaches `Authorization: Bearer <accessToken>` to protected calls.
// - Wraps fetch, parses the shared error envelope, and surfaces 409 nicely.
// - Attempts a single refresh-token flow on a 401 before giving up.
//
// All traffic goes through the gateway at VITE_API_BASE, base path /api/v1.

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
const BASE_PATH = '/api/v1';

const TOKENS_KEY = 'jat.tokens';

// ---------------------------------------------------------------------------
// Token storage
// ---------------------------------------------------------------------------

export function getTokens() {
  try {
    const raw = localStorage.getItem(TOKENS_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function setTokens(tokens) {
  if (!tokens) {
    localStorage.removeItem(TOKENS_KEY);
    return;
  }
  localStorage.setItem(TOKENS_KEY, JSON.stringify(tokens));
}

export function clearTokens() {
  localStorage.removeItem(TOKENS_KEY);
}

export function isAuthenticated() {
  const t = getTokens();
  return !!(t && t.accessToken);
}

// Hook so the app can react to a hard 401 (e.g. redirect to /login).
// App.jsx sets this; the client calls it after a failed refresh.
let onUnauthorized = null;
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

// ---------------------------------------------------------------------------
// Error type
// ---------------------------------------------------------------------------

export class ApiError extends Error {
  constructor(status, message, envelope) {
    super(message || `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.envelope = envelope || null;
    // On a 409 the server returns the conflicting tile in `data`.
    this.data = envelope ? envelope.data : null;
    this.isConflict = status === 409;
  }
}

async function parseBody(res) {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

// ---------------------------------------------------------------------------
// Core fetch wrapper
// ---------------------------------------------------------------------------

// options: { method, body, auth (default true), isRetry, rawResponse }
async function request(path, options = {}) {
  const { method = 'GET', body, auth = true, isRetry = false } = options;

  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  if (auth) {
    const tokens = getTokens();
    if (tokens && tokens.accessToken) {
      headers['Authorization'] = `Bearer ${tokens.accessToken}`;
    }
  }

  const res = await fetch(`${API_BASE}${BASE_PATH}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.ok) {
    return parseBody(res);
  }

  // ---- 401 handling: attempt a single refresh, then retry once. ----
  if (res.status === 401 && auth && !isRetry) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      return request(path, { ...options, isRetry: true });
    }
    // Refresh failed — hard logout.
    clearTokens();
    if (onUnauthorized) onUnauthorized();
  }

  const envelope = await parseBody(res);
  const message =
    (envelope && typeof envelope === 'object' && envelope.message) ||
    (typeof envelope === 'string' && envelope) ||
    res.statusText;
  throw new ApiError(res.status, message, typeof envelope === 'object' ? envelope : null);
}

// Refresh flow — swaps the refresh token for a new token pair. Returns bool.
let refreshInFlight = null;
async function tryRefresh() {
  const tokens = getTokens();
  if (!tokens || !tokens.refreshToken) return false;

  // De-dupe concurrent refreshes.
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    try {
      const res = await fetch(`${API_BASE}${BASE_PATH}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: tokens.refreshToken }),
      });
      if (!res.ok) return false;
      const data = await parseBody(res);
      if (!data || !data.accessToken) return false;
      setTokens(data);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

// ---------------------------------------------------------------------------
// Public API surface
// ---------------------------------------------------------------------------

export const api = {
  // ---- Auth (no token) ----
  async signup(email, password) {
    const data = await request('/auth/signup', {
      method: 'POST',
      body: { email, password },
      auth: false,
    });
    setTokens(data);
    return data;
  },

  async login(email, password) {
    const data = await request('/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    });
    setTokens(data);
    return data;
  },

  async refresh() {
    return tryRefresh();
  },

  logout() {
    clearTokens();
  },

  // ---- Applications (Bearer required) ----
  getBoard(includeArchived = true) {
    return request(`/applications?includeArchived=${includeArchived ? 'true' : 'false'}`);
  },

  getSidebar(sort = 'dateApplied', order = 'desc') {
    return request(`/applications/sidebar?sort=${encodeURIComponent(sort)}&order=${encodeURIComponent(order)}`);
  },

  create(payload) {
    return request('/applications', { method: 'POST', body: payload });
  },

  getOne(applicationId) {
    return request(`/applications/${applicationId}`);
  },

  updateStatus(applicationId, bucket) {
    // bucket in applied | in_progress | complete | archived
    return request(`/applications/${applicationId}/status`, {
      method: 'PATCH',
      body: { bucket },
    });
  },

  updateNotes(applicationId, notes) {
    return request(`/applications/${applicationId}/notes`, {
      method: 'PATCH',
      body: { notes },
    });
  },

  updateGeneral(applicationId, payload) {
    return request(`/applications/${applicationId}`, {
      method: 'PATCH',
      body: payload,
    });
  },

  remove(applicationId) {
    return request(`/applications/${applicationId}`, { method: 'DELETE' });
  },

  // ---- Documents (presigned S3) ----
  requestUploadUrl(applicationId, contentType) {
    return request(`/applications/${applicationId}/document`, {
      method: 'POST',
      body: { contentType },
    });
  },

  // Direct browser PUT to S3 presigned URL. Not through the gateway.
  async uploadFile(putUrl, file, contentType) {
    const res = await fetch(putUrl, {
      method: 'PUT',
      headers: { 'Content-Type': contentType || file.type || 'application/octet-stream' },
      body: file,
    });
    if (!res.ok) {
      throw new ApiError(res.status, `Upload failed (${res.status})`);
    }
    return true;
  },

  getDownloadUrl(applicationId) {
    return request(`/applications/${applicationId}/document`);
  },

  // ---- Portals ----
  listPortals() {
    return request('/portals');
  },
};

export default api;
