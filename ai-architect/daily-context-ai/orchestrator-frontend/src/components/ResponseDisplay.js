import React from 'react';

export default function ResponseDisplay({ message }) {
  const isUser = message.role === 'USER';
  const time = message.timestamp
    ? new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : '';

  return (
    <div className={`message ${isUser ? 'user-message' : 'assistant-message'}`}>
      <div className="message-content">{message.content}</div>
      {time && <div className="message-time">{time}</div>}
    </div>
  );
}
