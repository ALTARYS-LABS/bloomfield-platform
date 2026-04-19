// Types alignes sur les DTOs backend (com.bloomfield.terminal.alerts.api.dto).
// Les montants cote backend sont des BigDecimal serialises en string pour preserver
// la precision ; on les garde en string cote TypeScript et on les parse uniquement
// pour l'affichage.

export type ThresholdOperator = 'ABOVE' | 'BELOW' | 'CROSSES_UP' | 'CROSSES_DOWN';

export interface AlertRule {
  id: string;
  ticker: string;
  operator: ThresholdOperator;
  threshold: string;
  enabled: boolean;
  createdAt: string;
}

export interface AlertRuleRequest {
  ticker: string;
  operator: ThresholdOperator;
  threshold: string;
}

export interface AlertEvent {
  id: string;
  ticker: string;
  price: string;
  triggeredAt: string;
  read: boolean;
}

// Libelles FR des operateurs pour les selects / listes.
export const OPERATOR_LABEL: Record<ThresholdOperator, string> = {
  ABOVE: 'Au-dessus de',
  BELOW: 'En-dessous de',
  CROSSES_UP: 'Croise a la hausse',
  CROSSES_DOWN: 'Croise a la baisse',
};
