import React, { useState } from 'react';
import { exportConversationUrl } from '../services/ApiService';

export default function ConversationHistory({ conversations, activeId, onSelect, onDelete, onNewChat, aiProviders }) {
  const [exportMenuId, setExportMenuId] = useState(null);

  const handleExport = (e, id, format) => {
    e.stopPropagation();
    setExportMenuId(null);
    window.location.href = exportConversationUrl(id, format);
  };

  return (
    <div className="sidebar">
      <div className="sidebar-header">
        <span className="sidebar-title">Daily Context AI</span>
        <button className="new-chat-btn" onClick={onNewChat}>+ New Chat</button>
      </div>

      <div className="conv-list">
        {conversations.length === 0 && (
          <div className="conv-empty">No conversations yet</div>
        )}
        {conversations.map(c => (
          <div
            key={c.id}
            className={`conv-item ${c.id === activeId ? 'active' : ''}`}
            onClick={() => onSelect(c.id)}
          >
            <div className="conv-topic">{c.topic || 'New conversation'}</div>
            <div className="conv-meta">
              {new Date(c.timestamp).toLocaleDateString([], { month: 'short', day: 'numeric' })}
            </div>
            <div className="conv-actions" onClick={e => e.stopPropagation()}>
              <button
                className="conv-export-btn"
                title="Export"
                onClick={() => setExportMenuId(exportMenuId === c.id ? null : c.id)}
              >↓</button>
              {exportMenuId === c.id && (
                <div className="export-menu">
                  <button onClick={e => handleExport(e, c.id, 'json')}>Export JSON</button>
                  <button onClick={e => handleExport(e, c.id, 'pdf')}>Export PDF</button>
                </div>
              )}
              <button
                className="conv-delete"
                title="Delete"
                onClick={e => { e.stopPropagation(); onDelete(c.id); }}
              >×</button>
            </div>
          </div>
        ))}
      </div>

      {aiProviders.length > 0 && (
        <div className="sidebar-footer">
          Active providers: {aiProviders.join(', ')}
        </div>
      )}
    </div>
  );
}
