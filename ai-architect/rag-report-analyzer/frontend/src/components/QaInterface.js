import React, { useState } from 'react';
import { askQuestion } from '../services/ApiService';

function QaInterface() {
  const [question, setQuestion] = useState('');
  const [ticker, setTicker] = useState('NVDA');
  const [year, setYear] = useState(2025);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async e => {
    e.preventDefault();
    if (!question.trim()) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const { data } = await askQuestion(question, ticker, year);
      setResult(data);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Request failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="panel">
        <h2>Financial Q&amp;A</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <label>
              Ticker
              <input
                type="text"
                value={ticker}
                onChange={e => setTicker(e.target.value.toUpperCase())}
              />
            </label>
            <label>
              Fiscal Year
              <input
                type="number"
                value={year}
                onChange={e => setYear(Number(e.target.value))}
                style={{ width: 100 }}
              />
            </label>
          </div>
          <label style={{ marginBottom: '0.75rem', display: 'block' }}>
            Question
            <textarea
              rows={3}
              value={question}
              onChange={e => setQuestion(e.target.value)}
              placeholder="What was the total revenue for fiscal year 2025?"
              style={{ marginTop: 4 }}
            />
          </label>
          <button className="btn" type="submit" disabled={loading || !question.trim()}>
            {loading ? 'Asking…' : 'Ask'}
          </button>
        </form>
      </div>

      {error && <div className="panel"><p className="error">{error}</p></div>}

      {result && (
        <div className="panel">
          <h3>Answer</h3>
          <div className="answer-box" style={{ marginBottom: '1rem' }}>{result.answer}</div>

          {result.sources && result.sources.length > 0 && (
            <>
              <h3 style={{ marginBottom: '0.5rem' }}>
                Sources ({result.sources.length} chunk{result.sources.length !== 1 ? 's' : ''})
              </h3>
              {result.sources.map((src, i) => (
                <details key={src.chunkId || i} className="source-chunk">
                  <summary>Chunk {i + 1} — page {src.pageNumber}</summary>
                  <p>{src.text}</p>
                </details>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}

export default QaInterface;
