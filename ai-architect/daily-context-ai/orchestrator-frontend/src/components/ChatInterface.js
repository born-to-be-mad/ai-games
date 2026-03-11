import React, { useEffect, useRef } from 'react';
import ResponseDisplay from './ResponseDisplay';

const WEATHER_OPTIONS = ['openmeteo', 'weatherapi', 'openweathermap'];
const NEWS_OPTIONS = ['thenewsapi', 'gnews', 'newsapi'];

export default function ChatInterface({
  messages, loading, query, weatherProviders, newsSources,
  onQueryChange, onWeatherChange, onNewsChange, onSend
}) {
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!loading && query.trim()) onSend();
    }
  };

  const toggleWeather = (provider) => {
    const next = weatherProviders.includes(provider)
      ? weatherProviders.filter(p => p !== provider)
      : [...weatherProviders, provider];
    onWeatherChange(next);
  };

  const toggleNews = (source) => {
    const next = newsSources.includes(source)
      ? newsSources.filter(s => s !== source)
      : [...newsSources, source];
    onNewsChange(next);
  };

  return (
    <div className="main">
      <div className="messages-container">
        {messages.length === 0 && (
          <div className="messages-empty">Ask about weather or news to get started.</div>
        )}
        {messages.map((m, i) => (
          <ResponseDisplay key={m.id || i} message={m} />
        ))}
        {loading && (
          <div className="message assistant-message">
            <div className="message-content loading-dots">Thinking...</div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="input-area">
        <div className="filter-row">
          <span className="filter-label">Weather:</span>
          {WEATHER_OPTIONS.map(p => (
            <label key={p} className="filter-checkbox">
              <input
                type="checkbox"
                checked={weatherProviders.includes(p)}
                onChange={() => toggleWeather(p)}
              />
              {p}
            </label>
          ))}
          <span className="filter-separator" />
          <span className="filter-label">News:</span>
          {NEWS_OPTIONS.map(s => (
            <label key={s} className="filter-checkbox">
              <input
                type="checkbox"
                checked={newsSources.includes(s)}
                onChange={() => toggleNews(s)}
              />
              {s}
            </label>
          ))}
        </div>

        <div className="input-row">
          <textarea
            className="query-input"
            rows={2}
            placeholder="Ask about weather or news… (Enter to send, Shift+Enter for newline)"
            value={query}
            onChange={e => onQueryChange(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={loading}
          />
          <button
            className="send-btn"
            onClick={onSend}
            disabled={loading || !query.trim()}
          >
            {loading ? '…' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  );
}
