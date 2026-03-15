import React, { useState } from 'react';
import { predict } from '../services/ApiService';

const MODES = [
  { value: 'NARRATIVE_PREDICTION', label: 'Narrative (LLM)',         desc: 'Qualitative trend analysis via LLM' },
  { value: 'LINEAR_REGRESSION',    label: 'Linear Regression',       desc: 'Deterministic numeric extrapolation (R²-based)' },
  { value: 'HYBRID',               label: 'Hybrid (LR + Narrative)', desc: 'Statistical range + LLM narrative' },
];

function PredictionPanel() {
  const [ticker, setTicker] = useState('NVDA');
  const [mode, setMode] = useState('NARRATIVE_PREDICTION');
  const [outlook, setOutlook] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handlePredict = async () => {
    setLoading(true);
    setError(null);
    setOutlook(null);
    try {
      const { data } = await predict(ticker, mode);
      setOutlook(data);
    } catch (err) {
      setError(
        err.response?.status === 400
          ? 'No metrics stored for this ticker. Extract metrics first.'
          : (err.response?.data?.message || err.message)
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="panel">
        <h2>Financial Prediction</h2>
        <p style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '1rem' }}>
          Generates a forward-looking financial outlook based on stored historical metrics.
          Requires at least one period of extracted metrics.
        </p>

        <div className="form-row" style={{ marginBottom: '0.75rem' }}>
          <label>
            Ticker
            <input
              type="text"
              value={ticker}
              onChange={e => setTicker(e.target.value.toUpperCase())}
            />
          </label>
        </div>

        <div style={{ marginBottom: '1rem' }}>
          <div style={{ fontSize: '0.8rem', fontWeight: 500, color: '#64748b', marginBottom: 8 }}>
            Prediction Mode
          </div>
          <div className="radio-group">
            {MODES.map(m => (
              <label key={m.value}>
                <input
                  type="radio"
                  name="mode"
                  value={m.value}
                  checked={mode === m.value}
                  onChange={() => setMode(m.value)}
                />
                <span>
                  <strong>{m.label}</strong>
                  <span style={{ fontSize: '0.75rem', color: '#94a3b8', marginLeft: 4 }}>— {m.desc}</span>
                </span>
              </label>
            ))}
          </div>
        </div>

        <button className="btn" onClick={handlePredict} disabled={loading}>
          {loading ? 'Predicting…' : 'Generate Outlook'}
        </button>
        {error && <p className="error" style={{ marginTop: 8 }}>{error}</p>}
      </div>

      {outlook && (
        <div className="panel">
          <h3 style={{ marginBottom: '1rem' }}>
            {ticker} Financial Outlook
            <span className="methodology-badge" style={{ marginLeft: 10 }}>
              {outlook.methodology}
            </span>
          </h3>

          <div className="outlook">
            <div className="outlook-field">
              <label>Trend Analysis</label>
              <div className="value">{outlook.trend}</div>
            </div>

            <div className="outlook-field">
              <label>Predicted Revenue Range</label>
              <div className="value" style={{ fontSize: '1.15rem', fontWeight: 600, color: '#4f46e5' }}>
                {outlook.predictedRevenueRange}
              </div>
            </div>

            <div className="outlook-field">
              <label>Confidence</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4 }}>
                <div className="confidence-bar" style={{ flex: 1, maxWidth: 300 }}>
                  <div
                    className="confidence-fill"
                    style={{ width: `${Math.round((outlook.confidence || 0) * 100)}%` }}
                  />
                </div>
                <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>
                  {Math.round((outlook.confidence || 0) * 100)}%
                </span>
              </div>
            </div>

            {outlook.risks && outlook.risks.length > 0 && (
              <div className="outlook-field">
                <label>Key Risk Factors</label>
                <ul className="risk-list" style={{ marginTop: 6 }}>
                  {outlook.risks.map((risk, i) => <li key={i}>{risk}</li>)}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default PredictionPanel;
