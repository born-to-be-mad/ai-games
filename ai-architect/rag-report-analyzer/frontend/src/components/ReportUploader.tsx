import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ingestReport } from '../services/ApiService';
import type { Quarter } from '../types/api';

const INPUT = 'px-3 py-1.5 border border-slate-300 rounded-md text-sm outline-none focus:border-indigo-500 transition-colors';

export default function ReportUploader() {
  const [file, setFile] = useState<File | null>(null);
  const [ticker, setTicker] = useState('NVDA');
  const [year, setYear] = useState(2025);
  const [quarter, setQuarter] = useState<Quarter>('Annual');
  const [progress, setProgress] = useState(0);

  const mutation = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('No file selected');
      return ingestReport(file, ticker, year, quarter, setProgress);
    },
  });

  return (
    <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
      <h2 className="text-lg font-semibold mb-2 text-slate-800">Upload Financial Report</h2>
      <p className="text-sm text-slate-500 mb-4">
        Ingests a PDF (10-K / 10-Q), chunks it, embeds the chunks, and stores them in the vector store.
      </p>
      <form onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
        <div className="flex gap-3 flex-wrap items-end mb-4">
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
            PDF File
            <input
              type="file"
              accept=".pdf"
              className={INPUT + ' w-auto'}
              onChange={e => { setFile(e.target.files?.[0] ?? null); mutation.reset(); }}
            />
          </label>
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
            Ticker
            <input
              type="text"
              value={ticker}
              className={INPUT + ' w-28'}
              onChange={e => setTicker(e.target.value.toUpperCase())}
              placeholder="NVDA"
            />
          </label>
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
            Fiscal Year
            <input
              type="number"
              value={year}
              className={INPUT + ' w-24'}
              onChange={e => setYear(Number(e.target.value))}
              min={2000}
              max={2100}
            />
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
            type="submit"
            disabled={!file || mutation.isPending}
            className="px-5 py-1.5 bg-indigo-600 text-white rounded-md text-sm font-medium transition-colors hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {mutation.isPending ? 'Uploading…' : 'Ingest Report'}
          </button>
        </div>

        {mutation.isPending && (
          <div>
            <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden mt-3">
              <div className="h-full bg-indigo-600 transition-width" style={{ width: `${progress}%` }} />
            </div>
            <p className="text-slate-500 text-sm mt-1">{progress}%</p>
          </div>
        )}
        {mutation.isSuccess && (
          <p className="text-emerald-600 text-sm mt-2">Report ingested successfully ({file?.name}).</p>
        )}
        {mutation.isError && (
          <p className="text-red-600 text-sm mt-2">
            {(mutation.error as Error & { response?: { data?: { message?: string } } }).response?.data?.message
              ?? (mutation.error as Error).message
              ?? 'Upload failed.'}
          </p>
        )}
      </form>
    </div>
  );
}
