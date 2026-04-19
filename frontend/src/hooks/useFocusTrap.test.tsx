import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useRef, useState } from 'react';
import { useFocusTrap } from './useFocusTrap';

// Composant de test minimal qui reproduit la structure du slide-over alertes :
// un bouton declencheur (la cloche) ouvre un panneau role="dialog" contenant
// plusieurs elements focusables. On teste le piege de focus isolement du
// composant Terminal pour eviter de monter toute la chaine useAuth / STOMP.
function TrapHarness({ onEscapeSpy }: { onEscapeSpy?: () => void }) {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const handleEscape = () => {
    onEscapeSpy?.();
    setOpen(false);
  };
  useFocusTrap(open, dialogRef, handleEscape, triggerRef);

  return (
    <div>
      <button ref={triggerRef} type="button" onClick={() => setOpen(true)}>
        Ouvrir
      </button>
      {open && (
        <div ref={dialogRef} role="dialog" aria-modal="true" aria-label="Panneau test">
          <button type="button">Premier</button>
          <button type="button">Second</button>
          <button type="button" onClick={handleEscape}>
            Fermer
          </button>
        </div>
      )}
    </div>
  );
}

describe('useFocusTrap', () => {
  it('focalise le premier element au montage du dialog', async () => {
    const user = userEvent.setup();
    render(<TrapHarness />);
    await user.click(screen.getByRole('button', { name: 'Ouvrir' }));
    expect(screen.getByRole('button', { name: 'Premier' })).toHaveFocus();
  });

  it('ferme le panneau et rend le focus au declencheur sur Escape', async () => {
    const onEscapeSpy = vi.fn();
    const user = userEvent.setup();
    render(<TrapHarness onEscapeSpy={onEscapeSpy} />);
    const trigger = screen.getByRole('button', { name: 'Ouvrir' });
    await user.click(trigger);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.keyboard('{Escape}');

    expect(onEscapeSpy).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    // Le focus est restaure sur la cloche (returnFocusRef).
    expect(trigger).toHaveFocus();
  });

  it('boucle Tab depuis le dernier element vers le premier', async () => {
    const user = userEvent.setup();
    render(<TrapHarness />);
    await user.click(screen.getByRole('button', { name: 'Ouvrir' }));

    // A l'ouverture, focus sur "Premier". On avance jusqu'au dernier bouton.
    await user.tab();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Fermer' })).toHaveFocus();

    // Un Tab de plus doit boucler sur "Premier".
    await user.tab();
    expect(screen.getByRole('button', { name: 'Premier' })).toHaveFocus();
  });

  it('ramene Tab au premier element si le focus est hors du piege', async () => {
    const user = userEvent.setup();
    render(<TrapHarness />);
    await user.click(screen.getByRole('button', { name: 'Ouvrir' }));

    // Force le focus hors du dialog (sur body) puis appuie sur Tab : le piege
    // doit rattraper et replacer le focus sur le premier element.
    (document.activeElement as HTMLElement | null)?.blur();
    expect(document.body).toHaveFocus();

    await user.tab();
    expect(screen.getByRole('button', { name: 'Premier' })).toHaveFocus();
  });

  it('boucle Shift+Tab depuis le premier element vers le dernier', async () => {
    const user = userEvent.setup();
    render(<TrapHarness />);
    await user.click(screen.getByRole('button', { name: 'Ouvrir' }));
    expect(screen.getByRole('button', { name: 'Premier' })).toHaveFocus();

    await user.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'Fermer' })).toHaveFocus();
  });
});
