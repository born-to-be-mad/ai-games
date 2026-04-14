import { useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ComposableMap, Geographies, Geography, ZoomableGroup } from 'react-simple-maps'

const GEO_URL = 'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json'

interface Props {
  toolsUsed: string[]
  agentChain: string[]
  visible: boolean
}

type Tab = 'map' | 'tools' | 'agents'

const COLORS = ['#e05c2d', '#c44a1c', '#f97316', '#fb923c', '#fed7aa']

export function VisualizationPanel({ toolsUsed, agentChain, visible }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>('map')

  if (!visible) return null

  const toolData = toolsUsed.map((tool, i) => ({
    name: tool.replace(/_/g, ' '),
    calls: i + 1, // ordinal position as proxy for usage
  }))

  const agentData = agentChain.map((agent, i) => ({
    name: agent.replace(/Agent$/, ''),
    step: i + 1,
  }))

  return (
    <aside
      className="w-80 xl:w-96 flex-shrink-0 border-l border-slate-700/50 flex flex-col bg-slate-900/50"
      aria-label="Visualization panel"
    >
      <div className="px-4 py-3 border-b border-slate-700/50">
        <h2 className="text-sm font-semibold text-slate-200">Insights</h2>
        <div className="mt-2 flex gap-1">
          {(['map', 'tools', 'agents'] as Tab[]).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-3 py-1 rounded text-xs font-medium transition-colors ${
                activeTab === tab
                  ? 'bg-terra-600/30 text-terra-300 border border-terra-600/40'
                  : 'text-slate-400 hover:text-slate-300 hover:bg-slate-800'
              }`}
            >
              {tab.charAt(0).toUpperCase() + tab.slice(1)}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-thin p-4">
        {activeTab === 'map' && (
          <div>
            <p className="text-xs text-slate-500 mb-3">Global disaster event coverage</p>
            <div className="rounded-lg overflow-hidden border border-slate-700/40 bg-slate-800/30">
              <ComposableMap
                projection="geoNaturalEarth1"
                projectionConfig={{ scale: 120 }}
                width={340}
                height={200}
                aria-label="World map"
              >
                <ZoomableGroup>
                  <Geographies geography={GEO_URL}>
                    {({ geographies }) =>
                      geographies.map(geo => (
                        <Geography
                          key={geo.rsmKey}
                          geography={geo}
                          fill="#1e293b"
                          stroke="#334155"
                          strokeWidth={0.4}
                          style={{
                            default: { outline: 'none' },
                            hover: { fill: '#334155', outline: 'none' },
                            pressed: { outline: 'none' },
                          }}
                        />
                      ))
                    }
                  </Geographies>
                </ZoomableGroup>
              </ComposableMap>
            </div>
            <p className="text-xs text-slate-600 mt-2 text-center">
              Zoom/pan to explore. Highlighted regions after query.
            </p>
          </div>
        )}

        {activeTab === 'tools' && (
          <div>
            <p className="text-xs text-slate-500 mb-3">
              {toolData.length === 0 ? 'Tools appear after a query completes.' : 'Tools invoked this query'}
            </p>
            {toolData.length > 0 ? (
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={toolData} layout="vertical" margin={{ left: 8, right: 16 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" horizontal={false} />
                  <XAxis type="number" tick={{ fill: '#64748b', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <YAxis
                    type="category"
                    dataKey="name"
                    tick={{ fill: '#94a3b8', fontSize: 10 }}
                    axisLine={false}
                    tickLine={false}
                    width={90}
                  />
                  <Tooltip
                    contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8 }}
                    labelStyle={{ color: '#e2e8f0', fontSize: 11 }}
                    itemStyle={{ color: '#e05c2d', fontSize: 11 }}
                  />
                  <Bar dataKey="calls" radius={[0, 4, 4, 0]}>
                    {toolData.map((_, i) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-32 flex items-center justify-center text-slate-600 text-xs border border-dashed border-slate-700 rounded-lg">
                No data yet
              </div>
            )}
          </div>
        )}

        {activeTab === 'agents' && (
          <div>
            <p className="text-xs text-slate-500 mb-3">
              {agentData.length === 0 ? 'Agent chain appears after a query completes.' : 'Agent execution chain'}
            </p>
            {agentData.length > 0 ? (
              <div className="space-y-2">
                {agentData.map((a, i) => (
                  <div key={a.name} className="flex items-center gap-3">
                    <div className="w-6 h-6 rounded-full bg-terra-600/20 border border-terra-600/40 flex items-center justify-center text-xs text-terra-400 font-bold flex-shrink-0">
                      {a.step}
                    </div>
                    <div className="flex-1 bg-slate-800/50 border border-slate-700/40 rounded-lg px-3 py-2">
                      <p className="text-sm text-slate-200">{a.name}</p>
                    </div>
                    {i < agentData.length - 1 && (
                      <div className="absolute left-3 w-6 flex justify-center">
                        <div className="w-px h-2 bg-slate-700" />
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="h-32 flex items-center justify-center text-slate-600 text-xs border border-dashed border-slate-700 rounded-lg">
                No data yet
              </div>
            )}
          </div>
        )}
      </div>
    </aside>
  )
}
