import React, { useState } from 'react';
import ReportUploader from './components/ReportUploader';
import QaInterface from './components/QaInterface';
import MetricsDashboard from './components/MetricsDashboard';
import KnowledgeGraph from './components/KnowledgeGraph';
import PredictionPanel from './components/PredictionPanel';

const TABS = [
  { id: 'upload', label: 'Upload' },
  { id: 'qa', label: 'Q&A' },
  { id: 'metrics', label: 'Metrics' },
  { id: 'graph', label: 'Knowledge Graph' },
  { id: 'prediction', label: 'Prediction' },
];

function App() {
  const [activeTab, setActiveTab] = useState('upload');

  return (
    <div className="app">
      <header>
        <h1>RAG Financial Analyzer</h1>
        <nav>
          {TABS.map(tab => (
            <button
              key={tab.id}
              className={activeTab === tab.id ? 'active' : ''}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </header>
      <main>
        {activeTab === 'upload'     && <ReportUploader />}
        {activeTab === 'qa'         && <QaInterface />}
        {activeTab === 'metrics'    && <MetricsDashboard />}
        {activeTab === 'graph'      && <KnowledgeGraph />}
        {activeTab === 'prediction' && <PredictionPanel />}
      </main>
    </div>
  );
}

export default App;
