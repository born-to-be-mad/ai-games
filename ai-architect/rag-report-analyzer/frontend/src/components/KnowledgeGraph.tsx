import React, { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import { useQuery } from '@tanstack/react-query';
import { getMetricsGraph } from '../services/ApiService';
import type { GraphNode, GraphEdge } from '../types/api';

const NODE_COLORS: Record<GraphNode['type'], string> = {
  Company: '#4f46e5',
  Report:  '#059669',
  Metric:  '#d97706',
};
const NODE_RADIUS: Record<GraphNode['type'], number> = { Company: 22, Report: 16, Metric: 10 };

const INPUT = 'px-3 py-1.5 border border-slate-300 rounded-md text-sm w-28 outline-none focus:border-indigo-500 transition-colors';

export default function KnowledgeGraph() {
  const [ticker, setTicker] = useState('NVDA');
  const [info, setInfo] = useState<GraphNode | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const simRef = useRef<d3.Simulation<GraphNode, GraphEdge> | null>(null);

  const graphQuery = useQuery({
    queryKey: ['metrics-graph', ticker] as const,
    queryFn: () => getMetricsGraph(ticker).then(r => r.data),
    enabled: false,
  });

  useEffect(() => {
    if (!graphQuery.data) return;
    drawGraph(graphQuery.data.nodes, graphQuery.data.edges);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [graphQuery.data]);

  useEffect(() => () => { simRef.current?.stop(); }, []);

  const drawGraph = (nodes: GraphNode[], edges: GraphEdge[]) => {
    if (!svgRef.current) return;
    simRef.current?.stop();

    const width = svgRef.current.clientWidth || 800;
    const height = 500;
    const svg = d3.select(svgRef.current);
    svg.selectAll('*').remove();
    svg.attr('height', height);

    svg.append('defs').append('marker')
      .attr('id', 'arrow').attr('viewBox', '0 -5 10 10')
      .attr('refX', 18).attr('refY', 0)
      .attr('markerWidth', 6).attr('markerHeight', 6)
      .attr('orient', 'auto')
      .append('path').attr('d', 'M0,-5L10,0L0,5').attr('fill', '#94a3b8');

    const g = svg.append('g');
    svg.call(d3.zoom<SVGSVGElement, unknown>().scaleExtent([0.3, 3])
      .on('zoom', e => g.attr('transform', e.transform)));

    const simulation = d3.forceSimulation<GraphNode, GraphEdge>(nodes)
      .force('link', d3.forceLink<GraphNode, GraphEdge>(edges).id(d => d.id).distance(110))
      .force('charge', d3.forceManyBody().strength(-280))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide<GraphNode>().radius(d => (NODE_RADIUS[d.type] ?? 10) + 10));
    simRef.current = simulation;

    const link = g.append('g').selectAll('line').data(edges).join('line')
      .attr('stroke', '#94a3b8').attr('stroke-width', 1.5).attr('marker-end', 'url(#arrow)');

    const linkLabel = g.append('g').selectAll('text').data(edges).join('text')
      .text(d => d.label ?? '')
      .attr('font-size', 9).attr('fill', '#94a3b8').attr('text-anchor', 'middle');

    const node = g.append('g').selectAll<SVGCircleElement, GraphNode>('circle').data(nodes).join('circle')
      .attr('r', d => NODE_RADIUS[d.type] ?? 10)
      .attr('fill', d => NODE_COLORS[d.type] ?? '#64748b')
      .attr('stroke', 'white').attr('stroke-width', 2)
      .style('cursor', 'pointer')
      .on('click', (_, d) => setInfo(d))
      .call(d3.drag<SVGCircleElement, GraphNode>()
        .on('start', (e, d) => { if (!e.active) simulation.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y; })
        .on('drag', (e, d) => { d.fx = e.x; d.fy = e.y; })
        .on('end', (e, d) => { if (!e.active) simulation.alphaTarget(0); d.fx = null; d.fy = null; }));

    const nodeLabel = g.append('g').selectAll('text').data(nodes).join('text')
      .text(d => { const l = d.data?.['label'] as string | undefined ?? d.id; return l.length > 18 ? l.slice(0, 16) + '…' : l; })
      .attr('font-size', 11).attr('text-anchor', 'middle').attr('fill', '#1e293b')
      .attr('pointer-events', 'none')
      .attr('dy', d => (NODE_RADIUS[d.type] ?? 10) + 14);

    const nx = (n: GraphNode | string | number) => (n as GraphNode).x ?? 0;
    const ny = (n: GraphNode | string | number) => (n as GraphNode).y ?? 0;

    simulation.on('tick', () => {
      link.attr('x1', d => nx(d.source)).attr('y1', d => ny(d.source))
          .attr('x2', d => nx(d.target)).attr('y2', d => ny(d.target));
      linkLabel.attr('x', d => (nx(d.source) + nx(d.target)) / 2)
               .attr('y', d => (ny(d.source) + ny(d.target)) / 2 - 4);
      node.attr('cx', d => d.x ?? 0).attr('cy', d => d.y ?? 0);
      nodeLabel.attr('x', d => d.x ?? 0).attr('y', d => d.y ?? 0);
    });
  };

  const error = graphQuery.error
    ? ((graphQuery.error as { response?: { status?: number } }).response?.status === 404
        ? 'No data for this ticker. Extract metrics first.'
        : (graphQuery.error as Error).message ?? 'Failed to load graph.')
    : null;

  return (
    <div className="bg-white rounded-xl p-6 shadow-sm mb-6">
      <h2 className="text-lg font-semibold mb-2 text-slate-800">Knowledge Graph</h2>
      <p className="text-sm text-slate-500 mb-4">
        Force-directed graph of Company → Report → Metric relationships.
        Drag nodes to rearrange; scroll to zoom; click a node for details.
      </p>

      <div className="flex gap-3 flex-wrap items-end mb-4">
        <label className="text-xs font-medium text-slate-500 flex flex-col gap-1">
          Ticker
          <input type="text" value={ticker} className={INPUT} onChange={e => setTicker(e.target.value.toUpperCase())} />
        </label>
        <button
          onClick={() => graphQuery.refetch()}
          disabled={graphQuery.isFetching}
          className="px-5 py-1.5 bg-indigo-600 text-white rounded-md text-sm font-medium transition-colors hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {graphQuery.isFetching ? 'Loading…' : 'Load Graph'}
        </button>
      </div>

      {error && <p className="text-red-600 text-sm mb-3">{error}</p>}

      <div className="border border-slate-200 rounded-lg bg-slate-50 overflow-hidden">
        <svg ref={svgRef} className="w-full block" />
      </div>

      <div className="flex gap-5 mt-3 flex-wrap">
        {Object.entries(NODE_COLORS).map(([type, color]) => (
          <span key={type} className="flex items-center gap-1.5 text-xs text-slate-600">
            <span className="w-3 h-3 rounded-full inline-block" style={{ background: color }} />
            {type}
          </span>
        ))}
      </div>

      {info && (
        <div className="mt-4 bg-slate-100 p-3 rounded-md text-xs">
          <strong className="text-slate-800">{info.type}: {info.id}</strong>
          {info.data && (
            <pre className="mt-1 whitespace-pre-wrap text-slate-700">
              {JSON.stringify(info.data, null, 2)}
            </pre>
          )}
          <button
            onClick={() => setInfo(null)}
            className="mt-2 px-3 py-1 bg-slate-200 text-slate-700 rounded text-xs font-medium hover:bg-slate-300 transition-colors"
          >
            Close
          </button>
        </div>
      )}
    </div>
  );
}
