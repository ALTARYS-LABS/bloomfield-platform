export interface Quote {
  ticker: string;
  name: string;
  sector: string;
  price: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  change: number;
  changePercent: number;
  timestamp: number;
}

export interface OrderBookLevel {
  price: number;
  quantity: number;
}

export interface OrderBookEntry {
  ticker: string;
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
}

export interface MarketIndex {
  name: string;
  value: number;
  change: number;
  changePercent: number;
  sparklineData: number[];
}

export interface CandleData {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface EmitterInfo {
  ticker: string;
  name: string;
  sector: string;
  marketCap: number;
  per: number;
  dividendYield: number;
}
