import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiJson, apiFetch } from '../api/client';
import { useAuth } from '../auth/useAuth';
import type { Role, UserSummary } from '../auth/types';

const ROLES: Role[] = ['ADMIN', 'ANALYST', 'VIEWER'];

export default function AdminUsersPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [users, setUsers] = useState<UserSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiJson<UserSummary[]>('/admin/users');
      setUsers(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erreur inconnue');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const toggleEnabled = async (target: UserSummary) => {
    await apiFetch(`/admin/users/${target.id}/enabled`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled: !target.enabled }),
    });
    await load();
  };

  const toggleRole = async (target: UserSummary, role: Role) => {
    const nextRoles = target.roles.includes(role)
      ? target.roles.filter((r) => r !== role)
      : [...target.roles, role];
    if (nextRoles.length === 0) {
      // Le backend exige @NotEmpty : on refuse silencieusement côté UI.
      return;
    }
    await apiFetch(`/admin/users/${target.id}/roles`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ roles: nextRoles }),
    });
    await load();
  };

  return (
    <div className="min-h-screen bg-landing-bg text-text-primary px-4 md:px-8 py-6">
      <header className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl md:text-2xl font-semibold">Administration des utilisateurs</h1>
          <p className="text-xs md:text-sm text-text-secondary">
            Connecté en tant que {user?.email}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate('/terminal')}
            className="px-3 py-1.5 text-sm border border-border rounded hover:border-accent cursor-pointer"
          >
            Terminal
          </button>
          <button
            onClick={async () => {
              await logout();
              navigate('/login', { replace: true });
            }}
            className="px-3 py-1.5 text-sm border border-border rounded hover:border-accent cursor-pointer"
          >
            Se déconnecter
          </button>
        </div>
      </header>

      {loading && <p className="text-text-secondary text-sm">Chargement…</p>}
      {error && (
        <p role="alert" className="text-red-400 text-sm">
          {error}
        </p>
      )}

      {!loading && !error && (
        <div className="overflow-x-auto border border-border rounded-lg">
          <table className="w-full text-sm">
            <thead className="bg-bg-widget text-text-secondary text-left">
              <tr>
                <th className="px-3 py-2">Email</th>
                <th className="px-3 py-2">Nom</th>
                <th className="px-3 py-2">Rôles</th>
                <th className="px-3 py-2">Actif</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id} className="border-t border-border">
                  <td className="px-3 py-2 font-mono">{u.email}</td>
                  <td className="px-3 py-2">{u.fullName}</td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      {ROLES.map((role) => {
                        const active = u.roles.includes(role);
                        return (
                          <button
                            key={role}
                            onClick={() => toggleRole(u, role)}
                            className={`px-2 py-0.5 text-xs rounded border cursor-pointer ${
                              active
                                ? 'bg-accent/20 border-accent text-accent'
                                : 'border-border text-text-secondary hover:border-accent'
                            }`}
                          >
                            {role}
                          </button>
                        );
                      })}
                    </div>
                  </td>
                  <td className="px-3 py-2">
                    <button
                      onClick={() => toggleEnabled(u)}
                      className={`px-2 py-0.5 text-xs rounded border cursor-pointer ${
                        u.enabled
                          ? 'bg-green-500/10 border-green-500/40 text-green-400'
                          : 'bg-red-500/10 border-red-500/40 text-red-400'
                      }`}
                    >
                      {u.enabled ? 'Actif' : 'Désactivé'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
