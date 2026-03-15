import React from 'react';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { predict } from '../services/ApiService';
import type { FinancialOutlook, PredictionMode } from '../types/api';

const MODES: Array<{ value: PredictionMode; label: string; desc: string }> = [
  { value: 'NARRATIVE_PREDICTION', label: 'Narrative (LLM)',         desc: 'Qualitative trend analysis via LLM' },
  { value: 'LINEAR_REGRESSION',    label: 'Linear Regression',       desc: 'Deterministic numeric extrapolation (R²-based)' },
  { value: 'HYBRID',               label: 'Hybrid (LR + Narrative)', desc: 'Statistical range + LLM narrative' },
];

const INPUT = 'px-3 py-1.5 border border-slate-300 rounded-md text-sm w-28 outline-none focus:border-indigo-500 transition-colors';

interface PredictionFormValues { ticker: string; mode: PredictionMode; }

export default function PredictionPanel() {
  const { register, handleSubmit } = useForm<PredictionFormValues>({
    defaultValues: { ticker: 'NVDA', mode: 'NARRATIVE_PREDICTION' },
  });

  const mutation = useMutation({
    mutationFn: (values: PredictionFormValues) => predict(values.ticker, values.mode).then(r => r.data),
  });

  const outlook: FinancialOutlook | undefined = mutation.data;

  const onSubmit = handleSubmit(data => mutation.mutate(data));

  return (
    <div>
      <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
        <h2 className="text-lg font-semibold mb-2 text-slate-800">Financial Prediction</h2>
        <p className="text-sm text-slate-500 mb-4">
          Generates a forward-looking financial outlook based on stored historical metrics.
          Requires at least one period of extracted metrics.
        </p>

        <form onSubmit={onSubmit}>
          <div className="flex gap-3 flex-wrap items-end mb-4">
            <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
              Ticker
              <input
                type="text"
                className={INPUT}
                {...register('ticker', { required: true, setValueAs: (v: string) => v.toUpperCase() })}
              />
            </label>
          </div>

          <div className="mb-4">
            <div className="text-xs font-medium text-slate-500 mb-2">Prediction Mode</div>
            <div className="flex gap-4 flex-wrap">
              {MODES.map(m => (
                <label key={m.value} className="flex items-center gap-1.5 cursor-pointer text-sm text-slate-700">
                  <input type="radio" value={m.value} {...register('mode', { required: true })} />
                  <span>
                    <strong>{m.label}</strong>
                    <span className="text-[0.75rem] text-slate-400 ml-1">— {m.desc}</span>
                  </span>
                </label>
              ))}
            </div>
          </div>

          <button
            type="submit"
            disabled={mutation.isPending}
            className="px-5 py-1.5 bg-indigo-600 text-white rounded-md text-sm font-medium transition-colors hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {mutation.isPending ? 'Predicting…' : 'Generate Outlook'}
          </button>
          {mutation.isError && (
            <p className="text-red-600 text-sm mt-2">
              {(mutation.error as Error & { response?: { status?: number; data?: { message?: string } } }).response?.status === 400
                ? 'No metrics stored for this ticker. Extract metrics first.'
                : ((mutation.error as Error & { response?: { data?: { message?: string } } }).response?.data?.message
                    ?? (mutation.error as Error).message)}
            </p>
          )}
        </form>
      </div>

      {outlook && (
        <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
          <h3 className="text-[0.95rem] font-medium mb-4 text-slate-700 flex items-center gap-2">
            Financial Outlook
            <span className="inline-block bg-violet-100 text-violet-800 text-xs px-2 py-0.5 rounded font-medium">
              {outlook.methodology}
            </span>
          </h3>

          <div className="grid gap-4">
            <div>
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Trend Analysis</p>
              <p className="mt-1 text-sm text-slate-800">{outlook.trend}</p>
            </div>

            <div>
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Predicted Revenue Range</p>
              <p className="mt-1 text-lg font-semibold text-indigo-600">{outlook.predictedRevenueRange}</p>
            </div>

            <div>
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Confidence</p>
              <div className="flex items-center gap-3 mt-1.5">
                <div className="flex-1 max-w-xs h-2 bg-slate-200 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-amber-400 to-emerald-500 transition-width"
                    style={{ width: `${Math.round((outlook.confidence ?? 0) * 100)}%` }}
                  />
                </div>
                <span className="text-sm font-semibold text-slate-800">
                  {Math.round((outlook.confidence ?? 0) * 100)}%
                </span>
              </div>
            </div>

            {outlook.risks?.length > 0 && (
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Key Risk Factors</p>
                <ul className="list-disc pl-5 mt-1.5">
                  {outlook.risks.map((risk, i) => (
                    <li key={i} className="text-sm text-slate-700 mb-1">{risk}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
