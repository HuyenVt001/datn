import { signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { adminLogin } from '../api/auth.api';
import { EMAIL_KEY, TOKEN_KEY } from '../api/client';
import { firebaseAuth } from './firebase';

interface AuthState {
  /** JWT admin cua server (null = chua dang nhap). */
  token: string | null;
  /** Email admin dang dang nhap (hien tren header). */
  email: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(localStorage.getItem(TOKEN_KEY));
  const [email, setEmail] = useState<string | null>(localStorage.getItem(EMAIL_KEY));

  const login = useCallback(async (emailInput: string, password: string) => {
    // Buoc 1: dang nhap Firebase (email/password) de lay ID token
    const cred = await signInWithEmailAndPassword(firebaseAuth, emailInput, password);
    const idToken = await cred.user.getIdToken();
    // Buoc 2: doi ID token lay JWT admin cua server (server check claim admin)
    const result = await adminLogin(idToken);
    localStorage.setItem(TOKEN_KEY, result.accessToken);
    localStorage.setItem(EMAIL_KEY, result.email ?? emailInput);
    setToken(result.accessToken);
    setEmail(result.email ?? emailInput);
  }, []);

  const logout = useCallback(async () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    setToken(null);
    setEmail(null);
    // signOut Firebase chi de don phien; loi cung khong sao
    await signOut(firebaseAuth).catch(() => undefined);
  }, []);

  const value = useMemo(
    () => ({ token, email, login, logout }),
    [token, email, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth phai dung ben trong AuthProvider');
  }
  return ctx;
}
