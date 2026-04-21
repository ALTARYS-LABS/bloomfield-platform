import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from './authContextValue';

// Hook de confort : lève si utilisé hors du AuthProvider pour détecter les erreurs de câblage tôt.
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth doit être utilisé à l'intérieur d'un <AuthProvider>");
  }
  return ctx;
}
