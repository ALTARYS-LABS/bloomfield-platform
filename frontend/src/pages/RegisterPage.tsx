import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { AuthApiError } from '../auth/authApi';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(email, password, fullName);
      navigate('/terminal', { replace: true });
    } catch (err) {
      // La contrainte min(8) sur le mot de passe est validée côté backend ; on affiche
      // le message tel quel s'il vient de l'API, sinon un message générique.
      const message =
        err instanceof AuthApiError ? err.message : 'Inscription impossible pour le moment.';
      setError(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-landing-bg text-text-primary px-4">
      <form
        onSubmit={onSubmit}
        className="w-full max-w-md bg-bg-widget border border-border rounded-lg p-6 md:p-8 space-y-5"
        aria-label="Formulaire d'inscription"
      >
        <div className="text-center space-y-1">
          <h1 className="text-2xl font-semibold">Créer un compte</h1>
          <p className="text-sm text-text-secondary">Prototype Bloomfield Terminal</p>
        </div>

        <label className="block space-y-1">
          <span className="text-sm text-text-secondary">Nom complet</span>
          <input
            type="text"
            required
            autoComplete="name"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            className="w-full px-3 py-2 bg-landing-bg border border-border rounded focus:outline-none focus:border-accent"
          />
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-text-secondary">Email</span>
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-3 py-2 bg-landing-bg border border-border rounded focus:outline-none focus:border-accent"
          />
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-text-secondary">Mot de passe (8 caractères minimum)</span>
          <input
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 bg-landing-bg border border-border rounded focus:outline-none focus:border-accent"
          />
        </label>

        {error && (
          <p role="alert" className="text-sm text-red-400">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full py-2.5 bg-accent hover:bg-accent/80 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold rounded transition-colors cursor-pointer"
        >
          {submitting ? 'Création…' : 'Créer le compte'}
        </button>

        <p className="text-center text-sm text-text-secondary">
          Déjà inscrit ?{' '}
          <Link to="/login" className="text-accent hover:underline">
            Se connecter
          </Link>
        </p>
      </form>
    </div>
  );
}
