import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { api, getTokens, isAuthenticated, setUnauthorizedHandler, syncExtensionAuth } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [tokens, setTokensState] = useState(() => getTokens());

  // When the client hits an unrecoverable 401 it clears storage and calls this.
  useEffect(() => {
    setUnauthorizedHandler(() => setTokensState(null));
    return () => setUnauthorizedHandler(null);
  }, []);

  // On load, mirror an existing session to the extension. A plain page load fires no
  // setTokens/clearTokens, so without this an already-authenticated user would have to
  // log out and back in before the extension receives the token.
  useEffect(() => {
    syncExtensionAuth();
  }, []);

  const value = useMemo(() => {
    const user = tokens
      ? { userId: tokens.userId, email: tokens.email }
      : null;

    return {
      user,
      isAuthed: !!(tokens && tokens.accessToken),
      async login(email, password) {
        const data = await api.login(email, password);
        setTokensState(data);
        return data;
      },
      async signup(email, password) {
        const data = await api.signup(email, password);
        setTokensState(data);
        return data;
      },
      logout() {
        api.logout();
        setTokensState(null);
      },
    };
  }, [tokens]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}

export { isAuthenticated };
