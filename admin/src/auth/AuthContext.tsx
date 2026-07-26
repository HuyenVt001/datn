import { signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { adminLogin } from '../api/auth.api';
import { EMAIL_KEY, TOKEN_KEY, UID_KEY } from '../api/client';
import { firebaseAuth } from './firebase';

interface AuthState {
  /** JWT admin cua server (null = chua dang nhap). */
  token: string | null;
  /** Email admin dang dang nhap (hien tren header). */
  email: string | null;
  /** uid cua admin dang dang nhap — de chan thao tac len chinh minh o trang Users. */
  uid: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Doc uid tu payload JWT (claim `sub`) — fallback cho phien dang nhap CU
 * (truoc khi co UID_KEY trong localStorage): thieu uid la mat lop chan
 * "thao tac len chinh minh" o trang Users.
 */
function uidFromToken(token: string | null): string | null {
  if (!token) {
    return null;
  }
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(localStorage.getItem(TOKEN_KEY));
  const [email, setEmail] = useState<string | null>(localStorage.getItem(EMAIL_KEY));
  const [uid, setUid] = useState<string | null>(
    () => localStorage.getItem(UID_KEY) ?? uidFromToken(localStorage.getItem(TOKEN_KEY)),
  );

  const login = useCallback(async (emailInput: string, password: string) => {
    // Buoc 1: dang nhap Firebase (email/password) de lay ID token
    const cred = await signInWithEmailAndPassword(firebaseAuth, emailInput, password);
    const idToken = await cred.user.getIdToken();
    // Buoc 2: doi ID token lay JWT admin cua server (server check claim admin).
    // Fail (khong co quyen admin...) -> signOut Firebase de khong treo phien SDK.
    let result;
    try {
      result = await adminLogin(idToken);
    } catch (err) {
      await signOut(firebaseAuth).catch(() => undefined);
      throw err;
    }
    const resolvedUid = result.uid ?? uidFromToken(result.accessToken);
    localStorage.setItem(TOKEN_KEY, result.accessToken);
    localStorage.setItem(EMAIL_KEY, result.email ?? emailInput);
    if (resolvedUid) {
      localStorage.setItem(UID_KEY, resolvedUid);
    }
    setToken(result.accessToken);
    setEmail(result.email ?? emailInput);
    setUid(resolvedUid);
  }, []);

  const logout = useCallback(async () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(UID_KEY);
    setToken(null);
    setEmail(null);
    setUid(null);
    // signOut Firebase chi de don phien; loi cung khong sao
    await signOut(firebaseAuth).catch(() => undefined);
  }, []);

  const value = useMemo(
    () => ({ token, email, uid, login, logout }),
    [token, email, uid, login, logout],
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
