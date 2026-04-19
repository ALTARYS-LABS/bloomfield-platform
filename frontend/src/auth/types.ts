// Types partagés côté auth. Miroir des DTO Java de user.api.dto.
// Les rôles doivent rester synchronisés avec backend/.../user/api/Role.java.

export type Role = 'ADMIN' | 'ANALYST' | 'VIEWER';

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  roles: Role[];
}

export interface TokenResponse {
  accessToken: string;
  // Durée de vie en secondes, utilisée pour planifier un refresh préventif.
  expiresIn: number;
}

export interface UserSummary extends AuthUser {
  enabled: boolean;
  createdAt: string;
}
