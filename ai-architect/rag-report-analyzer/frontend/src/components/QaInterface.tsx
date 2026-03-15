import React from 'react';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { askQuestion } from '../services/ApiService';
import type { QaResponse } from '../types/api';

const INPUT = 'px-3 py-1.5 border border-slate-300 rounded-md text-sm outline-none focus:border-indigo-500 transition-colors';

interface QaFormValues {
  question: string;
  ticker: string;
  year: number;
}

export default function QaInterface() {
  const { register, handleSubmit, watch } = useForm<QaFormValues>({
    defaultValues: { ticker: 'NVDA', year: 2025, question: '' },
  });

  const question = watch('question');

  const mutation = useMutation({
    mutationFn: (values: QaFormValues) =>
      askQuestion(values.question, values.ticker, values.year).then(r => r.data),
  });

  const result: QaResponse | undefined = mutation.data;

  const onSubmit = handleSubmit(data => mutation.mutate(data));

  return (
    <div>
      <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
        <h2 className="text-lg font-semibold mb-4 text-slate-800">Financial Q&amp;A</h2>
        <form onSubmit={onSubmit}>
          <div className="flex gap-3 flex-wrap items-end mb-4">
            <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
              Ticker
              <input
                type="text"
                className={INPUT + ' w-28'}
                {...register('ticker', { required: true, setValueAs: (v: string) => v.toUpperCase() })}
              />
            </label>
            <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
              Fiscal Year
              <input
                type="number"
                className={INPUT + ' w-24'}
                {...register('year', { required: true, valueAsNumber: true })}
              />
            </label>
          </div>
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1 mb-3">
            Question
            <textarea
              rows={3}
              placeholder="What was the total revenue for fiscal year 2025?"
              className="mt-1 w-full px-3 py-2 border border-slate-300 rounded-md text-sm resize-y outline-none focus:border-indigo-500 transition-colors font-sans"
              {...register('question', { required: true })}
            />
          </label>
          <button
            type="submit"
            disabled={mutation.isPending || !question?.trim()}
            className="px-5 py-1.5 bg-indigo-600 text-white rounded-md text-sm font-medium transition-colors hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {mutation.isPending ? 'Asking…' : 'Ask'}
          </button>
        </form>
      </div>

      {mutation.isError && (
        <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
          <p className="text-red-600 text-sm">
            {(mutation.error as Error & { response?: { data?: { message?: string } } }).response?.data?.message
              ?? (mutation.error as Error).message
              ?? 'Request failed.'}
          </p>
        </div>
      )}

      {result && (
        <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
          <h3 className="text-[0.95rem] font-medium mb-2 text-slate-700">Answer</h3>
          <div className="bg-slate-100 border-l-4 border-indigo-600 p-4 rounded-md whitespace-pre-wrap text-sm leading-relaxed mb-4">
            {result.answer}
          </div>

          {result.sources?.length > 0 && (
            <>
              <h3 className="text-[0.95rem] font-medium mb-2 text-slate-700">
                Sources ({result.sources.length} chunk{result.sources.length !== 1 ? 's' : ''})
              </h3>
              {result.sources.map((src, i) => (
                <details key={src.chunkId || i} className="border border-slate-200 rounded-md mt-2 overflow-hidden">
                  <summary className="p-2 px-3 bg-slate-50 cursor-pointer text-xs text-slate-600">
                    Chunk {i + 1} — page {src.pageNumber}
                  </summary>
                  <p className="p-3 text-xs text-slate-700 leading-relaxed whitespace-pre-wrap">{src.text}</p>
                </details>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}
