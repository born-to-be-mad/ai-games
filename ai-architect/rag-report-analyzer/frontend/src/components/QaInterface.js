import React, { useState } from 'react';
import { askQuestion } from '../services/ApiService';
import { useAsyncAction } from '../hooks/useAsyncAction';

function QaInterface() {
  const [question, setQuestion] = useState('');
  const [ticker, setTicker] = useState('NVDA');
  const [year, setYear] = useState(2025);
  const [result, setResult] = useState(null);
  const { loading, error, run } = useAsyncAction();

  const handleSubmit = async e => {
    e.preventDefault();
    if (!question.trim()) return;
    setResult(null);
    const res = await run(() => askQuestion(question, ticker, year));
    if (res) setResult(res.data);
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
              />
            </label>
          </div>
          <label className="qa-question-label">
            Question
            <textarea
              rows={3}
              value={question}
              onChange={e => setQuestion(e.target.value)}
              placeholder="What was the total revenue for fiscal year 2025?"
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
          <div className="answer-box answer-box--spaced">{result.answer}</div>

          {result.sources?.length > 0 && (
            <>
              <h3 className="sources-title">
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
