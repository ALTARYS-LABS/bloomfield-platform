import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import * as authApi from './authApi';
import { AuthApiError } from './authApi';
import type { AuthUser } from './types';
import { AuthContext, type AuthContextValue } from './authContextValue';
import { configureApiClient } from '../api/client';

// Provider d'authentification. Le token d'accès vit uniquement en mémoire React :
// volontairement pas dans localStorage ni sessionStorage pour réduire la surface d'exfiltration
// en cas de XSS. Le refresh token, lui, est dans un cookie HttpOnly côté serveur.
// Voir _kb_/web-auth-security-tutorial.md pour le raisonnement complet.

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Une ref miroir du token pour que les closures (client API, intercepteurs) lisent
  // toujours la valeur la plus récente sans dépendre du cycle de re-render.
  const tokenRef = useRef<string | null>(null);
  tokenRef.current = accessToken;

  const handleLogout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setAccessToken(null);
      setUser(null);
    }
  }, []);

  const applyTokens = useCallback(async (newAccessToken: string) => {
    // On récupère le profil juste après avoir un nouveau token pour nourrir l'UI
    // (rôles notamment, pour que ProtectedRoute puisse décider).
    setAccessToken(newAccessToken);
    const profile = await authApi.me(newAccessToken);
    setUser(profile);
  }, []);

  const handleLogin = useCallback(
    async (email: string, password: string) => {
      const tokens = await authApi.login(email, password);
      await applyTokens(tokens.accessToken);
    },
    [applyTokens],
  );

  const handleRegister = useCallback(
    async (email: string, password: string, fullName: string) => {
      await authApi.register(email, password, fullName);
      // Connexion automatique après inscription réussie.
      await handleLogin(email, password);
    },
    [handleLogin],
  );

  // Silent refresh au démarrage : si un cookie refresh_token valide est présent,
  // on obtient immédiatement un nouveau access token et on restaure la session.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tokens = await authApi.refresh();
        if (!cancelled) {
          await applyTokens(tokens.accessToken);
        }
      } catch {
        // Pas de session active : normal pour un visiteur non connecté.
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [applyTokens]);

  // On configure le client HTTP avec des accesseurs stables : ils pointent toujours
  // sur la valeur courante du token via la ref, et savent réagir à un 401 en rafraîchissant.
  useEffect(() => {
    configureApiClient({
      getAccessToken: () => tokenRef.current,
      refreshAccessToken: async () => {
        const tokens = await authApi.refresh();
        setAccessToken(tokens.accessToken);
        return tokens.accessToken;
      },
      onUnauthorized: () => {
        setAccessToken(null);
        setUser(null);
      },
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      accessToken,
      isLoading,
      login: handleLogin,
      register: handleRegister,
      logout: handleLogout,
    }),
    [user, accessToken, isLoading, handleLogin, handleRegister, handleLogout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export { AuthApiError };
