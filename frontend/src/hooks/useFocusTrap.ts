import { useEffect, type RefObject } from 'react';

// Selecteur volontairement limite aux controles interactifs rencontres dans le
// slide-over alertes (boutons, liens, inputs, elements tabbables explicites).
// Les cas exotiques (contenteditable, iframe, video[controls]...) sont hors scope
// tant qu'aucun dialog ne les utilise.
const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

/**
 * Piege le focus clavier a l'interieur de `containerRef` tant que `active` vaut true.
 *
 * Comportement :
 * - A l'activation, focalise le premier element focusable du conteneur, ou le
 *   conteneur lui-meme s'il a `tabindex="-1"` et aucun enfant focusable.
 * - Tab / Shift+Tab bouclent sur le premier / dernier element focusable. Si le
 *   focus est hors du conteneur (backdrop, body...), Tab le ramene au premier
 *   element pour eviter les fuites.
 * - Escape declenche `onEscape` (typiquement la fermeture du panneau).
 * - A la desactivation, restaure le focus sur `returnFocusRef` si disponible,
 *   sinon sur l'element qui avait le focus avant l'activation.
 *
 * Note : passer une callback `onEscape` stable (useCallback) evite un teardown
 * de l'effet a chaque rendu parent, ce qui volerait le focus mi-interaction.
 */
export function useFocusTrap(
  active: boolean,
  containerRef: RefObject<HTMLElement | null>,
  onEscape: () => void,
  returnFocusRef?: RefObject<HTMLElement | null>,
): void {
  useEffect(() => {
    if (!active) return;
    const container = containerRef.current;
    if (!container) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    // Capture la cible de restauration maintenant : la recuperer depuis le ref
    // au cleanup est fragile (le noeud peut avoir ete demonte entre-temps) et
    // declenche react-hooks/exhaustive-deps.
    const returnTarget = returnFocusRef?.current ?? previouslyFocused;

    const focusables = () =>
      Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));

    const first = focusables()[0];
    // Fallback sur le conteneur lui-meme (necessite tabIndex={-1}) si aucun
    // enfant focusable n'est encore monte (contenu asynchrone).
    (first ?? container).focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onEscape();
        return;
      }
      if (e.key !== 'Tab') return;
      const items = focusables();
      if (items.length === 0) return;
      const firstItem = items[0];
      const lastItem = items[items.length - 1];
      const activeEl = document.activeElement as HTMLElement | null;
      const insideTrap = activeEl !== null && container.contains(activeEl);
      // Focus hors du conteneur (backdrop, body apres clic) : on le ramene
      // au premier element pour empecher la fuite du piege.
      if (!insideTrap) {
        e.preventDefault();
        firstItem.focus();
        return;
      }
      if (e.shiftKey && activeEl === firstItem) {
        e.preventDefault();
        lastItem.focus();
        return;
      }
      if (!e.shiftKey && activeEl === lastItem) {
        e.preventDefault();
        firstItem.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      returnTarget?.focus();
    };
  }, [active, containerRef, onEscape, returnFocusRef]);
}
