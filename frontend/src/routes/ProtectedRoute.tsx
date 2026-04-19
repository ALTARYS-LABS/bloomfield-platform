import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import type { Role } from '../auth/types';

interface ProtectedRouteProps {
  children: React.ReactNode;
  // Si défini, l'utilisateur doit posséder au moins un de ces rôles.
  requiredRoles?: Role[];
}

// Garde de route. Pendant le silent refresh initial on affiche un état neutre
// pour éviter un flash vers /login juste avant que la session soit restaurée.
export function ProtectedRoute({ children, requiredRoles }: ProtectedRouteProps) {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-landing-bg text-text-secondary text-sm">
        Chargement de la session…
      </div>
    );
  }

  if (!user) {
    // On mémorise la destination initiale pour rebondir après authentification.
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (requiredRoles && !requiredRoles.some((role) => user.roles.includes(role))) {
    // Rôle insuffisant : on redirige sur le terminal avec un signal dans le state
    // que la page de destination peut transformer en toast si elle le souhaite.
    return <Navigate to="/terminal" state={{ forbidden: true }} replace />;
  }

  return <>{children}</>;
}
