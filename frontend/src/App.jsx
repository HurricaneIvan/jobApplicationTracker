import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './hooks/useAuth.jsx';
import AuthPage from './pages/AuthPage.jsx';
import BoardPage from './pages/BoardPage.jsx';

function RequireAuth({ children }) {
  const { isAuthed } = useAuth();
  if (!isAuthed) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  const { isAuthed } = useAuth();

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthed ? <Navigate to="/" replace /> : <AuthPage />}
      />
      <Route
        path="/"
        element={
          <RequireAuth>
            <BoardPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
