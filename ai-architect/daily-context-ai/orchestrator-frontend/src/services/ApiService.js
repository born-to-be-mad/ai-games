import axios from 'axios';

const api = axios.create();

export const sendChat = (query, conversationId, weatherProviders, newsSources) =>
  api.post('/api/chat', { query, conversationId, weatherProviders, newsSources }).then(r => r.data);

export const getConversations = () =>
  api.get('/api/conversations').then(r => r.data);

export const getConversation = (id) =>
  api.get(`/api/conversations/${id}`).then(r => r.data);

export const deleteConversation = (id) =>
  api.delete(`/api/conversations/${id}`);

export const getProviders = () =>
  api.get('/api/config/providers').then(r => r.data);

export const getPreferences = (clientId) =>
  api.get(`/api/preferences/${clientId}`).then(r => r.data);

export const savePreferences = (clientId, prefs) =>
  api.put(`/api/preferences/${clientId}`, prefs).then(r => r.data);

export const exportConversationUrl = (id, format) =>
  `/api/conversations/${id}/export/${format}`;
