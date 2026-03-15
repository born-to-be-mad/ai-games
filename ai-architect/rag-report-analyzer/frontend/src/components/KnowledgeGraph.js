import React, { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import { getMetricsGraph } from '../services/ApiService';

const NODE_COLORS = {
  Company: '#4f46e5',
  Report:  '#059669',
  Metric:  '#d97706',
};

const NODE_RADIUS = { Company: 22, Report: 16, Metric: 10 };

function KnowledgeGraph() {
  const [ticker, setTicker] = useState('NVDA');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [info, setInfo] = useState(null);
  const svgRef = useRef(null);
  const simRef = useRef(null);

  const loadGraph = async () => {
    setLoading(true);
    setError(null);
    setInfo(null);
    try {
      const { data } = await getMetricsGraph(ticker);
      drawGraph(data);
    } catch (err) {
      setError(err.response?.status === 404
        ? 'No data for this ticker. Extract metrics first.'
        : (err.message || 'Failed to load graph.'));
    } finally {
      setLoading(false);
    }
  };

  const drawGraph = ({ nodes, edges }) => {
    if (!svgRef.current) return;

    // Stop previous simulation
    if (simRef.current) simRef.current.stop();

    const width = svgRef.current.clientWidth || 800;
    const height = 500;

    const svg = d3.select(svgRef.current);
    svg.selectAll('*').remove();
    svg.attr('height', height);

    // Arrow marker
    svg.append('defs').append('marker')
      .attr('id', 'arrow')
      .attr('viewBox', '0 -5 10 10')
      .attr('refX', 18).attr('refY', 0)
      .attr('markerWidth', 6).attr('markerHeight', 6)
      .attr('orient', 'auto')
      .append('path')
      .attr('d', 'M0,-5L10,0L0,5')
      .attr('fill', '#94a3b8');

    const g = svg.append('g');

    // Zoom + pan
    const zoom = d3.zoom()
      .scaleExtent([0.3, 3])
      .on('zoom', e => g.attr('transform', e.transform));
    svg.call(zoom);

    const simulation = d3.forceSimulation(nodes)
      .force('link', d3.forceLink(edges).id(d => d.id).distance(110))
      .force('charge', d3.forceManyBody().strength(-280))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide().radius(d => (NODE_RADIUS[d.type] || 10) + 10));
    simRef.current = simulation;

    // Links
    const link = g.append('g')
      .selectAll('line')
      .data(edges)
      .join('line')
      .attr('stroke', '#94a3b8')
      .attr('stroke-width', 1.5)
      .attr('marker-end', 'url(#arrow)');

    // Link labels
    const linkLabel = g.append('g')
      .selectAll('text')
      .data(edges)
      .join('text')
      .text(d => d.label || '')
      .attr('font-size', 9)
      .attr('fill', '#94a3b8')
      .attr('text-anchor', 'middle');

    // Nodes
    const node = g.append('g')
      .selectAll('circle')
      .data(nodes)
      .join('circle')
      .attr('r', d => NODE_RADIUS[d.type] || 10)
      .attr('fill', d => NODE_COLORS[d.type] || '#64748b')
      .attr('stroke', 'white')
      .attr('stroke-width', 2)
      .style('cursor', 'pointer')
      .on('click', (_, d) => setInfo(d))
      .call(
        d3.drag()
          .on('start', (e, d) => { if (!e.active) simulation.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y; })
          .on('drag', (e, d) => { d.fx = e.x; d.fy = e.y; })
          .on('end', (e, d) => { if (!e.active) simulation.alphaTarget(0); d.fx = null; d.fy = null; })
      );

    // Node labels
    const nodeLabel = g.append('g')
      .selectAll('text')
      .data(nodes)
      .join('text')
      .text(d => {
        const label = d.data?.label || d.data?.name || d.id;
        return label.length > 18 ? label.slice(0, 16) + '…' : label;
      })
      .attr('font-size', 11)
      .attr('text-anchor', 'middle')
      .attr('fill', '#1e293b')
      .attr('pointer-events', 'none')
      .attr('dy', d => (NODE_RADIUS[d.type] || 10) + 14);

    simulation.on('tick', () => {
      link
        .attr('x1', d => d.source.x).attr('y1', d => d.source.y)
        .attr('x2', d => d.target.x).attr('y2', d => d.target.y);
      linkLabel
        .attr('x', d => (d.source.x + d.target.x) / 2)
        .attr('y', d => (d.source.y + d.target.y) / 2 - 4);
      node.attr('cx', d => d.x).attr('cy', d => d.y);
      nodeLabel.attr('x', d => d.x).attr('y', d => d.y);
    });
  };

  // Cleanup on unmount
  useEffect(() => () => { if (simRef.current) simRef.current.stop(); }, []);

  return (
    <div className="panel">
      <h2>Knowledge Graph</h2>
      <p className="panel-description">
        Force-directed graph of Company → Report → Metric relationships.
        Drag nodes to rearrange; scroll to zoom; click a node for details.
      </p>

      <div className="form-row">
        <label>
          Ticker
          <input
            type="text"
            value={ticker}
            onChange={e => setTicker(e.target.value.toUpperCase())}
          />
        </label>
        <button className="btn" onClick={loadGraph} disabled={loading}>
          {loading ? 'Loading…' : 'Load Graph'}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      <div className="graph-canvas-wrapper">
        <svg ref={svgRef} className="graph-canvas" />
      </div>

      <div className="graph-legend">
        {Object.entries(NODE_COLORS).map(([type, color]) => (
          <span key={type} className="graph-legend-item">
            <span className="legend-dot" style={{ background: color }} />
            {type}
          </span>
        ))}
      </div>

      {info && (
        <div className="graph-node-info">
          <strong>{info.type}: {info.id}</strong>
          {info.data && (
            <pre>{JSON.stringify(info.data, null, 2)}</pre>
          )}
          <button className="btn secondary btn-sm" onClick={() => setInfo(null)}>
            Close
          </button>
        </div>
      )}
    </div>
  );
}

export default KnowledgeGraph;
