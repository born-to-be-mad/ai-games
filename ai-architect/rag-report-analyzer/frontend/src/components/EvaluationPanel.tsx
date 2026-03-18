import React from 'react';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { runEvaluation, runEvaluationMatrix } from '../services/ApiService';
import type { EvalReport } from '../types/api';

const INPUT = 'px-3 py-1.5 border border-slate-300 rounded-md text-sm outline-none focus:border-indigo-500 transition-colors';

interface EvalFormValues {
  topK: number;
}

function fmtPct(v: number) {
  return `${(v * 100).toFixed(1)}%`;
}

function SummaryCards({ report }: { report: EvalReport }) {
  const cards = [
    ['Overall', report.avgOverall],
    ['Context Precision', report.avgContextPrecision],
    ['Context Recall', report.avgContextRecall],
    ['Faithfulness', report.avgFaithfulness],
    ['Answer Relevance', report.avgAnswerRelevance],
  ] as const;

  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-4 mt-4">
      {cards.map(([label, value]) => (
        <div key={label} className="bg-slate-50 border border-slate-200 rounded-lg p-4 text-center">
          <div className="text-xs text-slate-500 mb-1">{label}</div>
          <div className="text-2xl font-semibold text-slate-800">{fmtPct(value)}</div>
        </div>
      ))}
    </div>
  );
}

export default function EvaluationPanel() {
  const { register, handleSubmit, watch } = useForm<EvalFormValues>({
    defaultValues: { topK: 5 },
  });

  const topK = watch('topK');

  const singleMutation = useMutation({
    mutationFn: (values: EvalFormValues) => runEvaluation(values.topK).then(r => r.data),
  });

  const matrixMutation = useMutation({
    mutationFn: () => runEvaluationMatrix().then(r => r.data),
  });

  const onRunTopK = handleSubmit(values => {
    matrixMutation.reset();
    singleMutation.mutate(values);
  });

  const onRunMatrix = () => {
    singleMutation.reset();
    matrixMutation.mutate();
  };

  const error = singleMutation.isError
    ? (singleMutation.error as Error & { response?: { data?: { message?: string } } }).response?.data?.message
      ?? (singleMutation.error as Error).message
    : matrixMutation.isError
    ? (matrixMutation.error as Error & { response?: { data?: { message?: string } } }).response?.data?.message
      ?? (matrixMutation.error as Error).message
    : null;

  const single = singleMutation.data;
  const matrix = matrixMutation.data;

  return (
    <div>
      <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
        <h2 className="text-lg font-semibold mb-2 text-slate-800">Evaluation</h2>
        <p className="text-sm text-slate-500 mb-4">
          Run RAG evaluation against the golden dataset. Use a single TopK run or matrix run (TopK=3,5,10).
        </p>

        <div className="flex gap-3 flex-wrap items-end mb-2">
          <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
            TopK
            <input
              type="number"
              min={1}
              max={50}
              className={INPUT + ' w-24'}
              {...register('topK', { required: true, valueAsNumber: true, min: 1, max: 50 })}
            />
          </label>

          <button
            type="button"
            onClick={onRunTopK}
            disabled={singleMutation.isPending || matrixMutation.isPending || !Number.isFinite(topK)}
            className="px-5 py-1.5 bg-indigo-600 text-white rounded-md text-sm font-medium transition-colors hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {singleMutation.isPending ? 'Running TopK…' : 'Run TopK'}
          </button>

          <button
            type="button"
            onClick={onRunMatrix}
            disabled={singleMutation.isPending || matrixMutation.isPending}
            className="px-5 py-1.5 bg-slate-200 text-slate-700 rounded-md text-sm font-medium transition-colors hover:bg-slate-300 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {matrixMutation.isPending ? 'Running Matrix…' : 'Run Matrix'}
          </button>
        </div>

        <p className="text-xs text-slate-500">
          These calls can be expensive and may take a few minutes depending on provider latency.
        </p>
        {error && <p className="text-red-600 text-sm mt-3">{error}</p>}
      </div>

      {single && (
        <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
          <h3 className="text-[0.95rem] font-medium text-slate-700">
            TopK = {single.topK} • {single.results.length} questions
          </h3>
          <p className="text-xs text-slate-500 mt-1">
            Evaluated at {new Date(single.evaluatedAt).toLocaleString()}
          </p>
          <SummaryCards report={single} />

          <details className="mt-5 border border-slate-200 rounded-md overflow-hidden">
            <summary className="cursor-pointer bg-slate-50 px-3 py-2 text-sm text-slate-700">
              Show first 5 per-question results
            </summary>
            <div className="p-3 space-y-3">
              {single.results.slice(0, 5).map((r, idx) => (
                <div key={idx} className="border border-slate-200 rounded p-3">
                  <p className="text-sm font-medium text-slate-700 mb-2">{r.question}</p>
                  <p className="text-xs text-slate-500 mb-2">Retrieved chunks: {r.retrievedChunksCount}</p>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div>Precision: <span className="font-medium">{fmtPct(r.scores.contextPrecision)}</span></div>
                    <div>Recall: <span className="font-medium">{fmtPct(r.scores.contextRecall)}</span></div>
                    <div>Faithfulness: <span className="font-medium">{fmtPct(r.scores.faithfulness)}</span></div>
                    <div>Relevance: <span className="font-medium">{fmtPct(r.scores.answerRelevance)}</span></div>
                  </div>
                </div>
              ))}
            </div>
          </details>
        </div>
      )}

      {matrix && (
        <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
          <h3 className="text-[0.95rem] font-medium text-slate-700 mb-3">Matrix Results</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm border border-slate-200 rounded-lg overflow-hidden">
              <thead className="bg-slate-50">
                <tr className="text-slate-600">
                  <th className="text-left px-3 py-2 border-b border-slate-200">TopK</th>
                  <th className="text-left px-3 py-2 border-b border-slate-200">Overall</th>
                  <th className="text-left px-3 py-2 border-b border-slate-200">Precision</th>
                  <th className="text-left px-3 py-2 border-b border-slate-200">Recall</th>
                  <th className="text-left px-3 py-2 border-b border-slate-200">Faithfulness</th>
                  <th className="text-left px-3 py-2 border-b border-slate-200">Relevance</th>
                </tr>
              </thead>
              <tbody>
                {[...matrix]
                  .sort((a, b) => a.topK - b.topK)
                  .map(report => (
                    <tr key={report.topK} className="border-b border-slate-100 last:border-b-0">
                      <td className="px-3 py-2 font-medium text-slate-700">{report.topK}</td>
                      <td className="px-3 py-2">{fmtPct(report.avgOverall)}</td>
                      <td className="px-3 py-2">{fmtPct(report.avgContextPrecision)}</td>
                      <td className="px-3 py-2">{fmtPct(report.avgContextRecall)}</td>
                      <td className="px-3 py-2">{fmtPct(report.avgFaithfulness)}</td>
                      <td className="px-3 py-2">{fmtPct(report.avgAnswerRelevance)}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
