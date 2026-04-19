import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PortfolioWidget from './PortfolioWidget';
import type { PortfolioSummary } from '../types/portfolio';

// On mocke usePortfolio pour isoler le composant : pas de WebSocket ni de fetch réel.
const mockSubmitTrade = vi.fn();
const mockHook = vi.fn();

vi.mock('../hooks/usePortfolio', () => ({
  usePortfolio: () => mockHook(),
}));

const summary: PortfolioSummary = {
  id: 'pf-1',
  name: 'Principal',
  positions: [
    {
      ticker: 'SNTS',
      quantity: '10',
      avgCost: '1000',
      currentPrice: '1100',
      marketValue: '11000',
      unrealizedPnl: '1000',
      unrealizedPnlPercent: '10',
    },
  ],
  totalValue: '11000',
  totalCost: '10000',
  unrealizedPnl: '1000',
  realizedPnl: '0',
  unrealizedPnlPercent: '10',
};

describe('PortfolioWidget', () => {
  beforeEach(() => {
    mockSubmitTrade.mockReset();
    mockHook.mockReset();
    mockHook.mockReturnValue({
      connected: true,
      summary,
      loading: false,
      error: null,
      submitTrade: mockSubmitTrade,
    });
  });

  it('affiche les totaux et les positions', () => {
    render(<PortfolioWidget availableTickers={['SNTS', 'BICC']} />);
    // Le titre du widget.
    expect(screen.getByText('Portefeuille')).toBeInTheDocument();
    // Le ticker apparaît à la fois dans la ligne de position et dans le sélecteur du
    // formulaire d'ordre : au moins une occurrence suffit pour la vérification.
    expect(screen.getAllByText('SNTS').length).toBeGreaterThan(0);
    // Le P&L positif est affiché au format français (virgule comme séparateur décimal).
    expect(screen.getAllByText(/1[\s\u202f]000,00/).length).toBeGreaterThan(0);
  });

  it('soumet un trade quand on clique sur Exécuter', async () => {
    mockSubmitTrade.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<PortfolioWidget availableTickers={['SNTS', 'BICC']} />);

    await user.clear(screen.getByLabelText(/Qté/i));
    await user.type(screen.getByLabelText(/Qté/i), '5');
    await user.click(screen.getByRole('button', { name: /Exécuter/i }));

    expect(mockSubmitTrade).toHaveBeenCalledWith({
      ticker: 'SNTS',
      side: 'BUY',
      quantity: '5',
    });
  });

  it('affiche le message d\'erreur renvoyé par submitTrade', async () => {
    mockSubmitTrade.mockRejectedValue(new Error('Ticker inconnu : ZZZZ'));
    const user = userEvent.setup();
    render(<PortfolioWidget availableTickers={['SNTS']} />);

    await user.click(screen.getByRole('button', { name: /Exécuter/i }));

    expect(await screen.findByText(/Ticker inconnu/)).toBeInTheDocument();
  });

  it('indique un état vide quand le portefeuille n\'a aucune position', () => {
    mockHook.mockReturnValueOnce({
      connected: true,
      summary: { ...summary, positions: [] },
      loading: false,
      error: null,
      submitTrade: mockSubmitTrade,
    });
    render(<PortfolioWidget availableTickers={['SNTS']} />);
    expect(screen.getByText(/Aucune position/)).toBeInTheDocument();
  });
});
