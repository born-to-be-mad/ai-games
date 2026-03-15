import React, { useState } from 'react';
import { predict } from '../services/ApiService';
import { useAsyncAction } from '../hooks/useAsyncAction';

const MODES = [
  { value: 'NARRATIVE_PREDICTION', label: 'Narrative (LLM)',         desc: 'Qualitative trend analysis via LLM' },
  { value: 'LINEAR_REGRESSION',    label: 'Linear Regression',       desc: 'Deterministic numeric extrapolation (R²-based)' },
  { value: 'HYBRID',               label: 'Hybrid (LR + Narrative)', desc: 'Statistical range + LLM narrative' },
];

function PredictionPanel() {
  const [ticker, setTicker] = useState('NVDA');
  const [mode, setMode] = useState('NARRATIVE_PREDICTION');
  const [outlook, setOutlook] = useState(null);
  const { loading, error, run } = useAsyncAction();

  const handlePredict = async () => {
    setOutlook(null);
    const result = await run(
      () => predict(ticker, mode),
      err => err.response?.status === 400
        ? 'No metrics stored for this ticker. Extract metrics first.'
        : (err.response?.data?.message || err.message)
    );
    if (result) setOutlook(result.data);
  };

  return (
    <div>
      <div className="panel">
        <h2>Financial Prediction</h2>
        <p className="panel-description">
          Generates a forward-looking financial outlook based on stored historical metrics.
          Requires at least one period of extracted metrics.
        </p>

        <div className="form-row">
          <label>
            Ticker
            <input
              type="text"
              value={ticker}
              onChange={e => setTicker(e.target.value.toUpperCase())}
            />
          </label>
        </div>

        <div className="prediction-mode-section">
          <div className="prediction-mode-label">Prediction Mode</div>
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
                  <span className="mode-desc">— {m.desc}</span>
                </span>
              </label>
            ))}
          </div>
        </div>

        <button className="btn" onClick={handlePredict} disabled={loading}>
          {loading ? 'Predicting…' : 'Generate Outlook'}
        </button>
        {error && <p className="error">{error}</p>}
      </div>

      {outlook && (
        <div className="panel">
          <h3 className="outlook-title">
            {ticker} Financial Outlook
            <span className="methodology-badge">{outlook.methodology}</span>
          </h3>

          <div className="outlook">
            <div className="outlook-field">
              <label>Trend Analysis</label>
              <div className="value">{outlook.trend}</div>
            </div>

            <div className="outlook-field">
              <label>Predicted Revenue Range</label>
              <div className="value value--highlighted">{outlook.predictedRevenueRange}</div>
            </div>

            <div className="outlook-field">
              <label>Confidence</label>
              <div className="confidence-row">
                <div className="confidence-bar">
                  <div
                    className="confidence-fill"
                    style={{ width: `${Math.round((outlook.confidence || 0) * 100)}%` }}
                  />
                </div>
                <span className="confidence-pct">
                  {Math.round((outlook.confidence || 0) * 100)}%
                </span>
              </div>
            </div>

            {outlook.risks?.length > 0 && (
              <div className="outlook-field">
                <label>Key Risk Factors</label>
                <ul className="risk-list">
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
