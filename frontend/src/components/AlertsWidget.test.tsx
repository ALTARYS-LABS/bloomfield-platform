import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AlertsWidget from './AlertsWidget';
import type { AlertEvent, AlertRule } from '../types/alerts';

const rule: AlertRule = {
  id: 'r1',
  ticker: 'SGBC',
  operator: 'ABOVE',
  threshold: '12000',
  enabled: true,
  createdAt: new Date().toISOString(),
};

const event: AlertEvent = {
  id: 'e1',
  ticker: 'SGBC',
  price: '12345',
  triggeredAt: new Date().toISOString(),
  read: false,
};

describe('AlertsWidget', () => {
  it('affiche la liste des regles et le compteur non-lu', () => {
    render(
      <AlertsWidget
        rules={[rule]}
        events={[event]}
        loading={false}
        error={null}
        availableTickers={['SGBC']}
        unreadCount={1}
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onMarkRead={vi.fn()}
      />,
    );
    // Le ticker apparait dans la ligne de regle et dans le selecteur du formulaire.
    expect(screen.getAllByText('SGBC').length).toBeGreaterThan(0);
    // Badge non-lu visible dans l'onglet Evenements.
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('soumet une nouvelle regle via onCreate', async () => {
    const onCreate = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <AlertsWidget
        rules={[]}
        events={[]}
        loading={false}
        error={null}
        availableTickers={['SGBC', 'BICC']}
        unreadCount={0}
        onCreate={onCreate}
        onDelete={vi.fn()}
        onMarkRead={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText(/Seuil/i), '15000');
    await user.click(screen.getByRole('button', { name: /Armer/i }));

    expect(onCreate).toHaveBeenCalledWith('SGBC', 'ABOVE', '15000');
  });

  it('marque un evenement comme lu quand on clique sur "Lu"', async () => {
    const onMarkRead = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <AlertsWidget
        rules={[]}
        events={[event]}
        loading={false}
        error={null}
        availableTickers={['SGBC']}
        unreadCount={1}
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onMarkRead={onMarkRead}
      />,
    );

    // L'onglet Evenements contient 1 badge '1' et l'onglet s'intitule "Evenements".
    await user.click(screen.getByRole('button', { name: /Evenements/i }));
    await user.click(screen.getByRole('button', { name: /^Lu$/ }));

    expect(onMarkRead).toHaveBeenCalledWith('e1');
  });

  it('affiche un etat vide quand il n\'y a aucune regle', () => {
    render(
      <AlertsWidget
        rules={[]}
        events={[]}
        loading={false}
        error={null}
        availableTickers={['SGBC']}
        unreadCount={0}
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onMarkRead={vi.fn()}
      />,
    );
    expect(screen.getByText(/Aucune regle/)).toBeInTheDocument();
  });
});
