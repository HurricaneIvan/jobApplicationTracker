import { useState } from 'react';
import { THEMES, getTheme, applyTheme } from '../lib/theme';

// Topbar control that switches the color scheme. The actual application/persistence
// lives in lib/theme so it also runs at startup (main.jsx) regardless of route.
export default function ThemeSelect() {
  const [theme, setTheme] = useState(getTheme());

  function onChange(e) {
    const t = e.target.value;
    setTheme(t);
    applyTheme(t);
  }

  return (
    <label className="theme-select">
      <span>Theme</span>
      <select value={theme} onChange={onChange}>
        {THEMES.map((t) => (
          <option key={t.value} value={t.value}>
            {t.label}
          </option>
        ))}
      </select>
    </label>
  );
}
