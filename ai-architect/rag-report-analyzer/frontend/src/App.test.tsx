import { vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import App from './App';

vi.mock('./services/ApiService', () => ({
  ingestReport:    vi.fn(),
  askQuestion:     vi.fn(),
  extractMetrics:  vi.fn(),
  getMetrics:      vi.fn(),
  getMetricsGraph: vi.fn(),
  predict:         vi.fn(),
}));

vi.mock('./components/KnowledgeGraph', () => ({
  default: function MockKnowledgeGraph() {
    return <div data-testid="knowledge-graph">Knowledge Graph</div>;
  },
}));

test('renders app header', () => {
  render(<App />);
  expect(screen.getByText(/RAG Financial Analyzer/i)).toBeInTheDocument();
});

test('renders all navigation tabs', () => {
  render(<App />);
  expect(screen.getByText('Upload')).toBeInTheDocument();
  expect(screen.getByText('Q&A')).toBeInTheDocument();
  expect(screen.getByText('Metrics')).toBeInTheDocument();
  expect(screen.getByText('Knowledge Graph')).toBeInTheDocument();
  expect(screen.getByText('Prediction')).toBeInTheDocument();
});

test('Upload tab is active by default', () => {
  render(<App />);
  const uploadBtn = screen.getByText('Upload');
  expect(uploadBtn.className).toContain('bg-white');
});

test('clicking Q&A tab switches to Q&A screen', () => {
  render(<App />);
  fireEvent.click(screen.getByText('Q&A'));
  expect(screen.getByPlaceholderText(/What was the total revenue/i)).toBeInTheDocument();
});

test('clicking Prediction tab shows prediction panel', () => {
  render(<App />);
  fireEvent.click(screen.getByText('Prediction'));
  expect(screen.getByText(/Generate Outlook/i)).toBeInTheDocument();
});
