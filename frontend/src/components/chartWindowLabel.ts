import type { CandleData } from '../types/market';

/** Seuil en secondes : moins de 24 h entre la première et la dernière bougie => « intraday ». */
const INTRADAY_SECONDS = 24 * 60 * 60;

/** Libellé par défaut lorsque l'historique couvre au moins un jour calendaire. */
const DEFAULT_MULTI_DAY_LABEL = '30J';

/** Libellé affiché lorsque toutes les bougies tombent dans la même journée. */
const INTRADAY_LABEL = 'Intraday';

/**
 * Dérive le libellé de fenêtre affiché dans l'en-tête du graphique en se basant uniquement sur la
 * charge utile renvoyée par l'API. Cas traités :
 *
 * <ul>
 *   <li>historique vide ou singleton => libellé multi-jours par défaut (on ne veut pas afficher
 *       « Intraday » tant que l'API n'a rien répondu).</li>
 *   <li>tous les timestamps tombent dans une fenêtre de moins de 24 h => « Intraday ».</li>
 *   <li>sinon => libellé multi-jours par défaut.</li>
 * </ul>
 *
 * L'intérêt : quand STORY-010 (adaptateur Sikafinance) est activé, le payload couvre plusieurs
 * jours et le libellé « 30J » s'affiche sans modification supplémentaire côté client.
 */
export function computeWindowLabel(history: CandleData[]): string {
  if (history.length < 2) {
    return DEFAULT_MULTI_DAY_LABEL;
  }
  let minTime = history[0].time;
  let maxTime = history[0].time;
  for (const candle of history) {
    if (candle.time < minTime) minTime = candle.time;
    if (candle.time > maxTime) maxTime = candle.time;
  }
  return maxTime - minTime < INTRADAY_SECONDS ? INTRADAY_LABEL : DEFAULT_MULTI_DAY_LABEL;
}
