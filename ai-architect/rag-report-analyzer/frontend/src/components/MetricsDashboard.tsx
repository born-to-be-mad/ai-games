import React, { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { getMetrics, extractMetrics } from '../services/ApiService';
import type { FinancialMetrics, Quarter } from '../types/api';

const METRIC_DEFS: Array<{ key: keyof FinancialMetrics; label: string; unit: string }> = [
  { key: 'revenue',           label: 'Revenue',             unit: '$M' },
  { key: 'netIncome',         label: 'Net Income',          unit: '$M' },
  { key: 'eps',               label: 'EPS (diluted)',       unit: '$'  },
  { key: 'operatingCashFlow', label: 'Operating Cash Flow', unit: '$M' },
  { key: 'debtToEquity',      label: 'Debt / Equity',       unit: 'x'  },
];

const INPUT = 'px-3 py-1.5 border border-slate-300 rounded-md text-sm outline-none focus:border-indigo-500 transition-colors';

interface MetricsCardProps { label: string; value: number | string | undefined; unit: string; }
function MetricsCard({ label, value, unit }: MetricsCardProps) {
  return (
    <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 text-center">
      <div className="text-xs text-slate-500 mb-1">{label}</div>
      <div className="text-2xl font-semibold text-slate-800">
        {value != null ? Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 }) : '—'}
      </div>
      <div className="text-[0.7rem] text-slate-400">{unit}</div>
    </div>
  );
}

export default function MetricsDashboard() {
  const [ticker, setTicker] = useState('NVDA');
  const [year, setYear] = useState(2025);
  const [quarter, setQuarter] = useState<Quarter>('Annual');
  const queryClient = useQueryClient();

  const metricsQuery = useQuery({
    queryKey: ['metrics', ticker, year, quarter] as const,
    queryFn: () => getMetrics(ticker, year, quarter).then(r => r.data),
    enabled: false,
  });

  const extractMutation = useMutation({
    mutationFn: () => extractMetrics(ticker, year, quarter).then(r => r.data),
    onSuccess: data => {
      queryClient.setQueryData(['metrics', ticker, year, quarter], data);
    },
  });

  const metrics: FinancialMetrics | undefined = metricsQuery.data ?? extractMutation.data;
  const isLoading = metricsQuery.isFetching;
  const isExtracting = extractMutation.isPending;
  const error = metricsQuery.error
    ? ((metricsQuery.error as { response?: { status?: number } }).response?.status === 404
        ? 'No metrics found. Try extracting them first.'
        : (metricsQuery.error as Error).message)
    : extractMutation.isError
    ? ((extractMutation.error as Error & { response?: { data?: { message?: string } } }).response?.data?.message
        ?? (extractMutation.error as Error).message)
    : null;

  const barData = useMemo(
    () => metrics
      ? METRIC_DEFS
          .filter(d => d.unit === '$M' && metrics[d.key] != null)
          .map(d => ({ name: d.label, value: metrics[d.key] as number }))
      : [],
    [metrics],
  );

  return (
    <div>
      <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
        <h2 className="text-lg font-semibold mb-4 text-slate-800">Financial Metrics</h2>
        <div className="flex gap-3 flex-wrap items-end mb-4">
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
            Ticker
            <input type="text" value={ticker} className={INPUT + ' w-28'} onChange={e => setTicker(e.target.value.toUpperCase())} />
          </label>
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
            Year
            <input type="number" value={year} className={INPUT + ' w-24'} onChange={e => setYear(Number(e.target.value))} />
          </label>
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
            Quarter
            <select value={quarter} className={INPUT + ' w-28'} onChange={e => setQuarter(e.target.value as Quarter)}>
              <option value="Annual">Annual</option>
              <option value="Q1">Q1</option>
              <option value="Q2">Q2</option>
              <option value="Q3">Q3</option>
              <option value="Q4">Q4</option>
            </select>
          </label>
          <button
            onClick={() => metricsQuery.refetch()}
            disabled={isLoading}
            className="px-5 py-1.5 bg-indigo-600 text-white rounded-md text-sm font-medium transition-colors hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isLoading ? 'Loading…' : 'Load Metrics'}
          </button>
          <button
            onClick={() => extractMutation.mutate()}
            disabled={isExtracting}
            className="px-5 py-1.5 bg-slate-200 text-slate-700 rounded-md text-sm font-medium transition-colors hover:bg-slate-300 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isExtracting ? 'Extracting…' : 'Extract via LLM'}
          </button>
        </div>
        {error && <p className="text-red-600 text-sm mt-2">{error}</p>}
      </div>

      {metrics && (
        <>
          <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
            <h3 className="text-[0.95rem] font-medium mb-4 text-slate-700">
              {metrics.ticker} — {metrics.year} {metrics.quarter}
            </h3>
            <div className="grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-4">
              {METRIC_DEFS.map(def => (
                <MetricsCard key={def.key} label={def.label} value={metrics[def.key] as number | undefined} unit={def.unit} />
              ))}
            </div>
          </div>

          {barData.length > 0 && (
            <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
              <h3 className="text-[0.95rem] font-medium mb-4 text-slate-700">Revenue Metrics ($ millions)</h3>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={barData} margin={{ top: 8, right: 16, left: 16, bottom: 24 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} tickFormatter={(v: number) => `$${(v / 1000).toFixed(0)}B`} />
                  <Tooltip formatter={(v: number) => [`$${Number(v).toLocaleString()}M`, 'Value']} />
                  <Bar dataKey="value" fill="#4f46e5" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </>
      )}
    </div>
  );
}
