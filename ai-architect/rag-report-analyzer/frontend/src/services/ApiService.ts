import axios from 'axios';
import type {
  QaResponse, FinancialMetrics, MetricsGraphData, FinancialOutlook, PredictionMode, Quarter,
} from '../types/api';

const api = axios.create({ baseURL: '/api/v1' });

export const ingestReport = (
  file: File,
  ticker: string,
  year: number,
  quarter: Quarter,
  onProgress?: (pct: number) => void,
) => {
  const form = new FormData();
  form.append('file', file);
  form.append('ticker', ticker);
  form.append('year', String(year));
  form.append('quarter', quarter);
  return api.post<{ message: string; chunksCreated: number }>('/ingest', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: e =>
      onProgress?.(Math.round((e.loaded * 100) / (e.total ?? 1))),
  });
};

export const askQuestion = (question: string, ticker: string, year: number) =>
  api.get<QaResponse>('/qa', { params: { question, ticker, year } });

export const extractMetrics = (ticker: string, year: number, quarter: Quarter) =>
  api.post<FinancialMetrics>('/metrics/extract', null, { params: { ticker, year, quarter } });

export const getMetrics = (ticker: string, year: number, quarter: Quarter) =>
  api.get<FinancialMetrics>(`/metrics/${ticker}/${year}/${quarter}`);

export const getMetricsGraph = (ticker: string) =>
  api.get<MetricsGraphData>(`/metrics/graph/${ticker}`);

export const predict = (ticker: string, mode: PredictionMode) =>
  api.get<FinancialOutlook>(`/analysis/${ticker}`, { params: { mode } });
