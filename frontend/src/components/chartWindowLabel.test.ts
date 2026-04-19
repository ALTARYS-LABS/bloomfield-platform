import { describe, expect, it } from 'vitest';
import { computeWindowLabel } from './chartWindowLabel';
import type { CandleData } from '../types/market';

// Fabrique une bougie minimale : seul `time` compte pour le calcul du libellé.
const candle = (time: number): CandleData => ({
  time,
  open: 100,
  high: 101,
  low: 99,
  close: 100,
  volume: 1,
});

describe('computeWindowLabel', () => {
  it("retourne « 30J » par défaut lorsque l'historique est vide", () => {
    expect(computeWindowLabel([])).toBe('30J');
  });

  it('retourne « 30J » sur une unique bougie (empreinte mono-point, pas d\'info de fenêtre)', () => {
    expect(computeWindowLabel([candle(1_700_000_000)])).toBe('30J');
  });

  it('retourne « Intraday » quand toutes les bougies tombent dans la même journée', () => {
    const base = 1_700_000_000; // 14/11/2023 22:13 UTC
    // 12 bougies espacées d'une heure : durée totale < 24 h.
    const history = Array.from({ length: 12 }, (_, i) => candle(base + i * 3600));
    expect(computeWindowLabel(history)).toBe('Intraday');
  });

  it('retourne « 30J » dès que la fenêtre couvre plus de 24 heures', () => {
    const base = 1_700_000_000;
    // Deux bougies à 25 heures d'écart.
    const history = [candle(base), candle(base + 25 * 3600)];
    expect(computeWindowLabel(history)).toBe('30J');
  });

  it('est stable quand les bougies arrivent dans le désordre', () => {
    const base = 1_700_000_000;
    const history = [candle(base + 25 * 3600), candle(base)];
    expect(computeWindowLabel(history)).toBe('30J');
  });
});
