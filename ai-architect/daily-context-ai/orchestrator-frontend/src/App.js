import React, { useState, useEffect, useCallback } from 'react';
import ConversationHistory from './components/ConversationHistory';
import ChatInterface from './components/ChatInterface';
import { sendChat, getConversations, getConversation, deleteConversation, getProviders } from './services/ApiService';

export default function App() {
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [aiProviders, setAiProviders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState('');
  const [weatherProviders, setWeatherProviders] = useState([]);
  const [newsSources, setNewsSources] = useState([]);
  const [error, setError] = useState(null);

  const loadConversations = useCallback(async () => {
    try {
      const data = await getConversations();
      setConversations(data);
    } catch (e) {
      console.error('Failed to load conversations', e);
    }
  }, []);

  useEffect(() => {
    loadConversations();
    getProviders()
      .then(setAiProviders)
      .catch(e => console.error('Failed to load providers', e));
  }, [loadConversations]);

  const handleSelectConversation = async (id) => {
    try {
      const conv = await getConversation(id);
      setActiveConversationId(id);
      setMessages(conv.messages || []);
      setError(null);
    } catch (e) {
      console.error('Failed to load conversation', e);
      setError('Failed to load conversation.');
    }
  };

  const handleNewChat = () => {
    setActiveConversationId(null);
    setMessages([]);
    setQuery('');
    setError(null);
  };

  const handleDelete = async (id) => {
    try {
      await deleteConversation(id);
      if (id === activeConversationId) {
        setActiveConversationId(null);
        setMessages([]);
      }
      await loadConversations();
    } catch (e) {
      console.error('Failed to delete conversation', e);
      setError('Failed to delete conversation.');
    }
  };

  const handleSend = async () => {
    if (!query.trim() || loading) return;
    const userText = query.trim();
    setLoading(true);
    setError(null);

    const userMsg = { role: 'USER', content: userText, timestamp: new Date().toISOString() };
    setMessages(prev => [...prev, userMsg]);
    setQuery('');

    try {
      const response = await sendChat(userText, activeConversationId, weatherProviders, newsSources);
      setActiveConversationId(response.conversationId);
      const assistantMsg = { role: 'ASSISTANT', content: response.answer, timestamp: new Date().toISOString() };
      setMessages(prev => [...prev, assistantMsg]);
      await loadConversations();
    } catch (e) {
      console.error('Chat request failed', e);
      setError('Request failed. Is the backend running?');
      setMessages(prev => prev.slice(0, -1)); // remove optimistic user message
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-container">
      <ConversationHistory
        conversations={conversations}
        activeId={activeConversationId}
        onSelect={handleSelectConversation}
        onDelete={handleDelete}
        onNewChat={handleNewChat}
        aiProviders={aiProviders}
      />
      <div className="main-wrapper">
        {error && <div className="error-banner">{error}</div>}
        <ChatInterface
          messages={messages}
          loading={loading}
          query={query}
          weatherProviders={weatherProviders}
          newsSources={newsSources}
          onQueryChange={setQuery}
          onWeatherChange={setWeatherProviders}
          onNewsChange={setNewsSources}
          onSend={handleSend}
        />
      </div>
    </div>
  );
}
