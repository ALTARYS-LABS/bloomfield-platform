import { createContext } from 'react';
import type { AuthUser } from './types';

// Valeur exposée par le AuthProvider. Séparée du composant pour que le plugin
// react-refresh (Fast Refresh) n'impose pas une exportation purement composant.
export interface AuthContextValue {
  user: AuthUser | null;
  accessToken: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
