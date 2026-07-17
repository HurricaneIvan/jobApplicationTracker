// Color-scheme themes. Applied by setting `data-theme` on <html>; the CSS in index.css
// overrides its palette variables per theme. The choice persists in localStorage.

const KEY = 'jat.theme';

export const THEMES = [
  { value: 'light', label: 'Light' },
  { value: 'night', label: 'Night Mode' },
  { value: 'contrast', label: 'High Contrast' },
  { value: 'blue', label: 'Blue' },
  { value: 'green', label: 'Green' },
  { value: 'purple', label: 'Purple' },
  { value: 'pink', label: 'Pink' },
];

const VALID = new Set(THEMES.map((t) => t.value));

export function getTheme() {
  try {
    const t = localStorage.getItem(KEY);
    return t && VALID.has(t) ? t : 'light';
  } catch {
    return 'light';
  }
}

export function applyTheme(theme) {
  const t = VALID.has(theme) ? theme : 'light';
  // 'light' is the default in :root, so no attribute is needed for it.
  if (t === 'light') {
    document.documentElement.removeAttribute('data-theme');
  } else {
    document.documentElement.setAttribute('data-theme', t);
  }
  try {
    localStorage.setItem(KEY, t);
  } catch {
    /* ignore storage failures */
  }
}
