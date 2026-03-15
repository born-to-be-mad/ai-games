import React, { useState, useMemo } from 'react';
import {
  BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import { getMetrics, extractMetrics } from '../services/ApiService';

const METRIC_DEFS = [
  { key: 'revenue',           label: 'Revenue',            unit: '$M' },
  { key: 'netIncome',         label: 'Net Income',         unit: '$M' },
  { key: 'eps',               label: 'EPS (diluted)',      unit: '$'  },
  { key: 'operatingCashFlow', label: 'Operating Cash Flow', unit: '$M' },
  { key: 'debtToEquity',      label: 'Debt / Equity',      unit: 'x'  },
];

function MetricsCard({ label, value, unit }) {
  return (
    <div className="metric-card">
      <div className="metric-label">{label}</div>
      <div className="metric-value">
        {value != null ? Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 }) : '—'}
      </div>
      <div className="metric-unit">{unit}</div>
    </div>
  );
}

function MetricsDashboard() {
  const [ticker, setTicker] = useState('NVDA');
  const [year, setYear] = useState(2025);
  const [quarter, setQuarter] = useState('Annual');
  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(false);
  const [extracting, setExtracting] = useState(false);
  const [error, setError] = useState(null);
  const [chartData] = useState([]);

  const handleLoad = async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await getMetrics(ticker, year, quarter);
      setMetrics(data);
    } catch (err) {
      if (err.response?.status === 404) {
        setError('No metrics found. Try extracting them first.');
      } else {
        setError(err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleExtract = async () => {
    setExtracting(true);
    setError(null);
    try {
      const { data } = await extractMetrics(ticker, year, quarter);
      setMetrics(data);
    } catch (err) {
      setError(err.response?.data?.message || err.message);
    } finally {
      setExtracting(false);
    }
  };

  // Build chart data from loaded metrics (single period shown as bar)
  const barData = useMemo(
    () => metrics
      ? METRIC_DEFS
          .filter(d => d.unit === '$M' && metrics[d.key] != null)
          .map(d => ({ name: d.label, value: metrics[d.key] }))
      : [],
    [metrics]
  );

  return (
    <div>
      <div className="panel">
        <h2>Financial Metrics</h2>
        <div className="form-row">
          <label>
            Ticker
            <input type="text" value={ticker} onChange={e => setTicker(e.target.value.toUpperCase())} />
          </label>
          <label>
            Year
            <input type="number" value={year} onChange={e => setYear(Number(e.target.value))} style={{ width: 100 }} />
          </label>
          <label>
            Quarter
            <select value={quarter} onChange={e => setQuarter(e.target.value)}>
              <option value="Annual">Annual</option>
              <option value="Q1">Q1</option>
              <option value="Q2">Q2</option>
              <option value="Q3">Q3</option>
              <option value="Q4">Q4</option>
            </select>
          </label>
          <button className="btn" onClick={handleLoad} disabled={loading}>
            {loading ? 'Loading…' : 'Load Metrics'}
          </button>
          <button className="btn secondary" onClick={handleExtract} disabled={extracting}>
            {extracting ? 'Extracting…' : 'Extract via LLM'}
          </button>
        </div>
        {error && <p className="error">{error}</p>}
      </div>

      {metrics && (
        <>
          <div className="panel">
            <h3>{metrics.ticker} — {metrics.year} {metrics.quarter}</h3>
            <div className="metrics-grid" style={{ marginTop: '1rem' }}>
              {METRIC_DEFS.map(def => (
                <MetricsCard key={def.key} label={def.label} value={metrics[def.key]} unit={def.unit} />
              ))}
            </div>
          </div>

          {barData.length > 0 && (
            <div className="panel">
              <h3>Revenue Metrics ($ millions)</h3>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={barData} margin={{ top: 8, right: 16, left: 16, bottom: 24 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} tickFormatter={v => `$${(v / 1000).toFixed(0)}B`} />
                  <Tooltip formatter={v => [`$${Number(v).toLocaleString()}M`, 'Value']} />
                  <Bar dataKey="value" fill="#4f46e5" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {chartData.length > 1 && (
            <div className="panel">
              <h3>Revenue Trend</h3>
              <ResponsiveContainer width="100%" height={240}>
                <LineChart data={chartData} margin={{ top: 8, right: 16, left: 16, bottom: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="revenue" stroke="#4f46e5" strokeWidth={2} dot />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default MetricsDashboard;
