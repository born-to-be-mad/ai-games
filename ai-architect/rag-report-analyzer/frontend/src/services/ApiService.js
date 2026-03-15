import axios from 'axios';

const api = axios.create({ baseURL: '/api/v1' });

/** Ingest a PDF report. onProgress(percent) called during upload. */
export const ingestReport = (file, ticker, year, quarter, onProgress) => {
  const form = new FormData();
  form.append('file', file);
  form.append('ticker', ticker);
  form.append('year', year);
  form.append('quarter', quarter);
  return api.post('/ingest', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: e =>
      onProgress && onProgress(Math.round((e.loaded * 100) / (e.total || 1))),
  });
};

/** Ask a financial question scoped to a ticker + year. */
export const askQuestion = (question, ticker, year) =>
  api.get('/qa', { params: { question, ticker, year } });

/** Trigger LLM extraction for a reporting period. */
export const extractMetrics = (ticker, year, quarter) =>
  api.post('/metrics/extract', null, { params: { ticker, year, quarter } });

/** Retrieve stored metrics for a specific period. */
export const getMetrics = (ticker, year, quarter) =>
  api.get(`/metrics/${ticker}/${year}/${quarter}`);

/** Retrieve D3-ready knowledge graph nodes + edges for a ticker. */
export const getMetricsGraph = ticker =>
  api.get(`/metrics/graph/${ticker}`);

/** Run financial prediction. mode: NARRATIVE_PREDICTION | LINEAR_REGRESSION | HYBRID */
export const predict = (ticker, mode) =>
  api.get(`/analysis/${ticker}`, { params: { mode } });
