import { useEffect, useMemo, useRef } from 'react';
import { createChart, CandlestickSeries, HistogramSeries, type IChartApi, type ISeriesApi, type CandlestickData, type HistogramData, type Time } from 'lightweight-charts';
import type { CandleData, Quote } from '../types/market';
import { computeWindowLabel } from './chartWindowLabel';

interface Props {
  ticker: string;
  history: CandleData[];
  latestQuote?: Quote;
}

export default function CandlestickChart({ ticker, history, latestQuote }: Props) {
  // Libellé dérivé exclusivement de la charge utile : en mode simulé sans seeder historique,
  // toutes les bougies tombent le même jour et l'en-tête affiche « Intraday » au lieu de « 30J ».
  const windowLabel = useMemo(() => computeWindowLabel(history), [history]);
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const candleSeriesRef = useRef<ISeriesApi<any> | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const volumeSeriesRef = useRef<ISeriesApi<any> | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { color: '#161B22' },
        textColor: '#8B949E',
      },
      grid: {
        vertLines: { color: '#1C2333' },
        horzLines: { color: '#1C2333' },
      },
      crosshair: { mode: 0 },
      rightPriceScale: { borderColor: '#30363D' },
      timeScale: { borderColor: '#30363D', timeVisible: false },
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#22C55E',
      downColor: '#EF4444',
      borderDownColor: '#EF4444',
      borderUpColor: '#22C55E',
      wickDownColor: '#EF4444',
      wickUpColor: '#22C55E',
    });

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
    });

    chart.priceScale('volume').applyOptions({
      scaleMargins: { top: 0.8, bottom: 0 },
    });

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;

    const ro = new ResizeObserver(entries => {
      const { width, height } = entries[0].contentRect;
      chart.applyOptions({ width, height });
    });
    ro.observe(containerRef.current);

    return () => {
      ro.disconnect();
      chart.remove();
    };
  }, []);

  useEffect(() => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current || history.length === 0) return;

    const candles: CandlestickData<Time>[] = history.map(h => ({
      time: h.time as Time,
      open: h.open,
      high: h.high,
      low: h.low,
      close: h.close,
    }));

    const volumes: HistogramData<Time>[] = history.map(h => ({
      time: h.time as Time,
      value: h.volume,
      color: h.close >= h.open ? 'rgba(34,197,94,0.3)' : 'rgba(239,68,68,0.3)',
    }));

    candleSeriesRef.current.setData(candles);
    volumeSeriesRef.current.setData(volumes);
    chartRef.current?.timeScale().fitContent();
  }, [history]);

  useEffect(() => {
    if (!candleSeriesRef.current || !latestQuote) return;
    const now = Math.floor(Date.now() / 1000) as Time;
    candleSeriesRef.current.update({
      time: now,
      open: latestQuote.open,
      high: latestQuote.high,
      low: latestQuote.low,
      close: latestQuote.price,
    });
  }, [latestQuote]);

  return (
    <div className="h-full flex flex-col">
      <div className="drag-handle bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border rounded-t-lg flex justify-between cursor-grab">
        <span>Graphique — {ticker}</span>
        <span className="text-text-secondary font-mono">{windowLabel}</span>
      </div>
      <div ref={containerRef} className="flex-1 min-h-0" />
    </div>
  );
}
