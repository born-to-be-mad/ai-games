import React, { useState } from 'react';
import { ingestReport } from '../services/ApiService';

function ReportUploader() {
  const [file, setFile] = useState(null);
  const [ticker, setTicker] = useState('NVDA');
  const [year, setYear] = useState(2025);
  const [quarter, setQuarter] = useState('Annual');
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState(null); // null | 'uploading' | 'done' | 'error'
  const [message, setMessage] = useState('');

  const handleSubmit = async e => {
    e.preventDefault();
    if (!file) return;
    setStatus('uploading');
    setProgress(0);
    setMessage('');
    try {
      await ingestReport(file, ticker, year, quarter, pct => setProgress(pct));
      setStatus('done');
      setMessage(`Report ingested successfully (${file.name}).`);
    } catch (err) {
      setStatus('error');
      setMessage(err.response?.data?.message || err.message || 'Upload failed.');
    }
  };

  return (
    <div className="panel">
      <h2>Upload Financial Report</h2>
      <p style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '1rem' }}>
        Ingests a PDF (10-K / 10-Q), chunks it, embeds the chunks, and stores them in the vector store.
      </p>
      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <label>
            PDF File
            <input
              type="file"
              accept=".pdf"
              onChange={e => { setFile(e.target.files[0]); setStatus(null); }}
              style={{ width: 'auto' }}
            />
          </label>
          <label>
            Ticker
            <input
              type="text"
              value={ticker}
              onChange={e => setTicker(e.target.value.toUpperCase())}
              placeholder="NVDA"
            />
          </label>
          <label>
            Fiscal Year
            <input
              type="number"
              value={year}
              onChange={e => setYear(Number(e.target.value))}
              min={2000}
              max={2100}
              style={{ width: 100 }}
            />
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
          <button className="btn" type="submit" disabled={!file || status === 'uploading'}>
            {status === 'uploading' ? 'Uploading…' : 'Ingest Report'}
          </button>
        </div>

        {status === 'uploading' && (
          <div>
            <div className="progress-bar">
              <div className="progress-bar-fill" style={{ width: `${progress}%` }} />
            </div>
            <p className="loading" style={{ marginTop: 6 }}>{progress}%</p>
          </div>
        )}
        {status === 'done'  && <p className="success">{message}</p>}
        {status === 'error' && <p className="error">{message}</p>}
      </form>
    </div>
  );
}

export default ReportUploader;
