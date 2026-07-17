// Bridges the web-app session into the Chrome extension via `externally_connectable`.
//
// After login the extension needs the current access token to call the gateway on the
// user's behalf (save job, add portal); on logout we revoke it. The web app is the only
// origin allowed to talk to the extension (see extension/manifest.json → externally_connectable).
//
// Everything here is best-effort and MUST NOT break a plain browser session:
//   - `chrome.runtime` is only injected into the page when a matching extension is
//     installed, so it is absent in Firefox/Safari and in Chrome without the extension.
//   - VITE_EXTENSION_ID is the unpacked-extension id Chrome assigns on "Load unpacked";
//     when it is unset we simply do nothing.

const EXTENSION_ID = import.meta.env.VITE_EXTENSION_ID || '';

function canBridge() {
  return (
    !!EXTENSION_ID &&
    typeof chrome !== 'undefined' &&
    !!chrome.runtime &&
    typeof chrome.runtime.sendMessage === 'function'
  );
}

function send(message) {
  if (!canBridge()) return;
  try {
    chrome.runtime.sendMessage(EXTENSION_ID, message, () => {
      // Read (and thereby swallow) lastError. "Could not establish connection" /
      // "no receiving end" is expected and harmless when the extension isn't running.
      void chrome.runtime.lastError;
    });
  } catch {
    // sendMessage can throw synchronously if the runtime surface is partial. Ignore.
  }
}

// Push the current access token into the extension. No-op on a falsy token.
export function pushJwtToExtension(jwt) {
  if (!jwt) return;
  send({ type: 'SET_JWT', jwt });
}

// Revoke the extension's copy of the token (logout / hard 401).
export function clearJwtInExtension() {
  send({ type: 'CLEAR_JWT' });
}
