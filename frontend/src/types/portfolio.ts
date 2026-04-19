// Types côté client pour le module Portefeuille. Tous les montants sont sérialisés
// en string côté backend (@JsonSerialize ToStringSerializer) afin d'éviter la perte
// de précision liée aux nombres JavaScript. On manipule donc des strings dans le
// state et on ne les convertit en Number que pour l'affichage.

export type TradeSide = 'BUY' | 'SELL';

export interface PositionView {
  ticker: string;
  quantity: string;
  avgCost: string;
  currentPrice: string;
  marketValue: string;
  unrealizedPnl: string;
  unrealizedPnlPercent: string;
}

export interface PortfolioSummary {
  id: string;
  name: string;
  positions: PositionView[];
  totalValue: string;
  totalCost: string;
  unrealizedPnl: string;
  realizedPnl: string;
  unrealizedPnlPercent: string;
}

export interface TradeView {
  id: string;
  ticker: string;
  side: TradeSide;
  quantity: string;
  price: string;
  executedAt: string;
  realizedPnl: string | null;
}

export interface SubmitTradeRequest {
  ticker: string;
  side: TradeSide;
  quantity: string;
}
