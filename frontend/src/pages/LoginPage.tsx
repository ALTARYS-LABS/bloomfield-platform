import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { AuthApiError } from '../auth/authApi';

interface LocationState {
  from?: string;
}

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as LocationState | null)?.from ?? '/terminal';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      // On ne révèle pas si c'est l'email ou le mot de passe qui est invalide
      // pour limiter les possibilités d'énumération de comptes.
      const message =
        err instanceof AuthApiError && err.status === 401
          ? 'Identifiants invalides.'
          : 'Connexion impossible. Réessayez dans un instant.';
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
        aria-label="Formulaire de connexion"
      >
        <div className="text-center space-y-1">
          <h1 className="text-2xl font-semibold">Connexion</h1>
          <p className="text-sm text-text-secondary">Accédez au Bloomfield Terminal</p>
        </div>

        <label className="block space-y-1">
          <span className="text-sm text-text-secondary">Email</span>
          <input
            type="email"
            required
            autoComplete="email"
            autoFocus
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-3 py-2 bg-landing-bg border border-border rounded focus:outline-none focus:border-accent"
          />
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-text-secondary">Mot de passe</span>
          <input
            type="password"
            required
            autoComplete="current-password"
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
          {submitting ? 'Connexion…' : 'Se connecter'}
        </button>

        <p className="text-center text-sm text-text-secondary">
          Pas encore de compte ?{' '}
          <Link to="/register" className="text-accent hover:underline">
            Créer un compte
          </Link>
        </p>
      </form>
    </div>
  );
}
