import React, { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ReportUploader from './components/ReportUploader';
import QaInterface from './components/QaInterface';
import MetricsDashboard from './components/MetricsDashboard';
import KnowledgeGraph from './components/KnowledgeGraph';
import PredictionPanel from './components/PredictionPanel';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});

const TABS = [
  { id: 'upload',     label: 'Upload' },
  { id: 'qa',         label: 'Q&A' },
  { id: 'metrics',    label: 'Metrics' },
  { id: 'graph',      label: 'Knowledge Graph' },
  { id: 'prediction', label: 'Prediction' },
] as const;

type TabId = typeof TABS[number]['id'];

function App() {
  const [activeTab, setActiveTab] = useState<TabId>('upload');

  return (
    <QueryClientProvider client={queryClient}>
      <div className="min-h-screen flex flex-col bg-slate-50 text-slate-800">
        <header className="bg-indigo-600 text-white px-8 py-4 flex items-center gap-8 shadow-md">
          <h1 className="text-xl font-semibold whitespace-nowrap">RAG Financial Analyzer</h1>
          <nav className="flex gap-2 flex-wrap">
            {TABS.map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={
                  activeTab === tab.id
                    ? 'bg-white text-indigo-600 font-semibold px-4 py-1.5 rounded-md text-sm'
                    : 'bg-white/15 border border-white/30 text-white px-4 py-1.5 rounded-md text-sm transition-colors hover:bg-white/25'
                }
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </header>
        <main className="flex-1 p-8 max-w-5xl w-full mx-auto">
          {activeTab === 'upload'     && <ReportUploader />}
          {activeTab === 'qa'         && <QaInterface />}
          {activeTab === 'metrics'    && <MetricsDashboard />}
          {activeTab === 'graph'      && <KnowledgeGraph />}
          {activeTab === 'prediction' && <PredictionPanel />}
        </main>
      </div>
    </QueryClientProvider>
  );
}

export default App;
