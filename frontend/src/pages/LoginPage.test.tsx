import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthContext, type AuthContextValue } from '../auth/authContextValue';
import { AuthApiError } from '../auth/authApi';
import LoginPage from './LoginPage';

// Tests UI : on vérifie le rendu, la soumission (qui délègue à context.login) et
// la gestion d'un 401 sans fuir d'information au-delà d'un message générique.

function renderLogin(login: AuthContextValue['login']) {
  const value: AuthContextValue = {
    user: null,
    accessToken: null,
    isLoading: false,
    login,
    register: async () => {},
    logout: async () => {},
  };
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/terminal" element={<div>page-terminal</div>} />
          <Route path="/register" element={<div>page-register</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe('LoginPage', () => {
  it('rend le formulaire de connexion', () => {
    renderLogin(vi.fn());
    expect(screen.getByRole('heading', { name: /Connexion/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Se connecter/ })).toBeInTheDocument();
  });

  it('appelle login() puis redirige vers /terminal en cas de succès', async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderLogin(login);

    await user.type(screen.getByLabelText(/Email/), 'alice@example.com');
    await user.type(screen.getByLabelText(/Mot de passe/), 'password123');
    await user.click(screen.getByRole('button', { name: /Se connecter/ }));

    expect(login).toHaveBeenCalledWith('alice@example.com', 'password123');
    expect(await screen.findByText('page-terminal')).toBeInTheDocument();
  });

  it('affiche un message générique sur 401 sans révéler quel champ est invalide', async () => {
    const login = vi.fn().mockRejectedValue(new AuthApiError(401, 'bad creds'));
    const user = userEvent.setup();
    renderLogin(login);

    await user.type(screen.getByLabelText(/Email/), 'alice@example.com');
    await user.type(screen.getByLabelText(/Mot de passe/), 'wrongpass');
    await user.click(screen.getByRole('button', { name: /Se connecter/ }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/Identifiants invalides/);
  });
});
