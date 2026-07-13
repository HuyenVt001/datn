import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** Route guard: chua co JWT -> quay ve /login. */
export function RequireAuth() {
  const { token } = useAuth();
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
