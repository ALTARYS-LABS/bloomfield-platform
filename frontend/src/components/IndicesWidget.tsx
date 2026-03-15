import { LineChart, Line, ResponsiveContainer } from 'recharts';
import type { MarketIndex } from '../types/market';

interface Props {
  indices: MarketIndex[];
}

export default function IndicesWidget({ indices }: Props) {
  return (
    <div className="h-full flex flex-col">
      <div className="drag-handle bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border rounded-t-lg cursor-grab">
        Indices BRVM
      </div>
      <div className="flex-1 overflow-auto p-3 flex flex-col gap-4">
        {indices.map(idx => (
          <div key={idx.name} className="flex items-center gap-4">
            <div className="flex-1">
              <div className="text-sm font-semibold text-text-primary">{idx.name}</div>
              <div className="flex items-baseline gap-2 mt-1">
                <span className="text-lg font-mono font-bold">{idx.value.toFixed(2)}</span>
                <span className={`text-sm font-mono font-semibold ${idx.changePercent >= 0 ? 'text-gain' : 'text-loss'}`}>
                  {idx.changePercent >= 0 ? '+' : ''}{idx.changePercent.toFixed(2)}%
                </span>
              </div>
            </div>
            <div className="w-24 h-10">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={idx.sparklineData.map((v, i) => ({ i, v }))}>
                  <Line
                    type="monotone"
                    dataKey="v"
                    stroke={idx.changePercent >= 0 ? '#22C55E' : '#EF4444'}
                    strokeWidth={1.5}
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        ))}
        {indices.length === 0 && (
          <div className="text-text-secondary text-sm text-center py-4">En attente de données...</div>
        )}
      </div>
    </div>
  );
}
