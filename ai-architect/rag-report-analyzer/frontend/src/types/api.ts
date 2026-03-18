import type { SimulationNodeDatum, SimulationLinkDatum } from 'd3';

export interface SourceChunk {
  chunkId: string;
  text: string;
  pageNumber: number;
}

export interface QaResponse {
  answer: string;
  sources: SourceChunk[];
}

export interface FinancialMetrics {
  ticker: string;
  year: number;
  quarter: string;
  period?: string;
  revenue?: number;
  netIncome?: number;
  eps?: number;
  operatingCashFlow?: number;
  debtToEquity?: number;
}

export interface GraphNode extends SimulationNodeDatum {
  id: string;
  type: 'Company' | 'Report' | 'Metric';
  data?: Record<string, unknown>;
}

export interface GraphEdge extends SimulationLinkDatum<GraphNode> {
  label?: string;
}

export interface MetricsGraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface FinancialOutlook {
  trend: string;
  risks: string[];
  predictedRevenueRange: string;
  confidence: number;
  methodology: string;
}

export interface EvalScores {
  contextPrecision: number;
  contextRecall: number;
  faithfulness: number;
  answerRelevance: number;
}

export interface QuestionEvalResult {
  question: string;
  expectedAnswer: string;
  generatedAnswer: string;
  retrievedChunksCount: number;
  scores: EvalScores;
}

export interface EvalReport {
  topK: number;
  results: QuestionEvalResult[];
  avgContextPrecision: number;
  avgContextRecall: number;
  avgFaithfulness: number;
  avgAnswerRelevance: number;
  avgOverall: number;
  evaluatedAt: string;
}

export type PredictionMode = 'NARRATIVE_PREDICTION' | 'LINEAR_REGRESSION' | 'HYBRID';
export type Quarter = 'Annual' | 'Q1' | 'Q2' | 'Q3' | 'Q4';
