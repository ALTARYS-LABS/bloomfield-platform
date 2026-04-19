import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthContext, type AuthContextValue } from '../auth/authContextValue';
import { ProtectedRoute } from './ProtectedRoute';
import type { AuthUser } from '../auth/types';

// Plutôt que de monter le vrai AuthProvider (qui déclenche un refresh réseau au montage),
// on injecte directement un AuthContext.Provider avec l'état voulu. Ça isole la logique
// de redirection de ProtectedRoute de celle de AuthContext.

function renderWithAuth(value: AuthContextValue, initialPath = '/secret') {
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div>page-login</div>} />
          <Route path="/terminal" element={<div>page-terminal</div>} />
          <Route
            path="/secret"
            element={
              <ProtectedRoute>
                <div>page-secrete</div>
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <ProtectedRoute requiredRoles={['ADMIN']}>
                <div>page-admin</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

const baseValue = {
  login: async () => {},
  register: async () => {},
  logout: async () => {},
};

describe('ProtectedRoute', () => {
  it('redirige vers /login quand non authentifié', () => {
    renderWithAuth({ ...baseValue, user: null, accessToken: null, isLoading: false });
    expect(screen.getByText('page-login')).toBeInTheDocument();
  });

  it('affiche un état de chargement pendant le silent refresh', () => {
    renderWithAuth({ ...baseValue, user: null, accessToken: null, isLoading: true });
    expect(screen.getByText(/Chargement de la session/)).toBeInTheDocument();
  });

  it('laisse passer un utilisateur authentifié', () => {
    const user: AuthUser = {
      id: '1',
      email: 'u@x.io',
      fullName: 'U',
      roles: ['VIEWER'],
    };
    renderWithAuth({ ...baseValue, user, accessToken: 'tok', isLoading: false });
    expect(screen.getByText('page-secrete')).toBeInTheDocument();
  });

  it("redirige vers /terminal quand le rôle requis n'est pas détenu", () => {
    const user: AuthUser = {
      id: '1',
      email: 'u@x.io',
      fullName: 'U',
      roles: ['VIEWER'],
    };
    renderWithAuth(
      { ...baseValue, user, accessToken: 'tok', isLoading: false },
      '/admin',
    );
    expect(screen.getByText('page-terminal')).toBeInTheDocument();
  });

  it('autorise ADMIN sur une route réservée ADMIN', () => {
    const user: AuthUser = {
      id: '1',
      email: 'a@x.io',
      fullName: 'A',
      roles: ['ADMIN'],
    };
    renderWithAuth(
      { ...baseValue, user, accessToken: 'tok', isLoading: false },
      '/admin',
    );
    expect(screen.getByText('page-admin')).toBeInTheDocument();
  });
});
