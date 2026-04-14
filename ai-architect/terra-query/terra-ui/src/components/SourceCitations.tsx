interface Props {
  sources: string[]
  toolsUsed: string[]
  agentChain: string[]
}

export function SourceCitations({ sources, toolsUsed, agentChain }: Props) {
  if (sources.length === 0 && toolsUsed.length === 0) return null

  return (
    <div className="mt-3 pt-3 border-t border-slate-700/50 space-y-2 animate-fade-in">
      {sources.length > 0 && (
        <div>
          <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Sources</span>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {sources.map(src => (
              <span
                key={src}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-500/10 border border-blue-500/20 text-xs text-blue-300"
              >
                <svg className="w-3 h-3" fill="none" viewBox="0 0 12 12">
                  <path
                    d="M6 1a5 5 0 100 10A5 5 0 006 1zM4 6h4M6 4v4"
                    stroke="currentColor"
                    strokeWidth="1.2"
                    strokeLinecap="round"
                  />
                </svg>
                {src}
              </span>
            ))}
          </div>
        </div>
      )}

      {toolsUsed.length > 0 && (
        <div>
          <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Tools used</span>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {toolsUsed.map(tool => (
              <span
                key={tool}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-700/50 border border-slate-600/50 text-xs text-slate-300"
              >
                <svg className="w-3 h-3 text-terra-500" fill="none" viewBox="0 0 12 12">
                  <path
                    d="M9.5 1.5L10.5 2.5 8 5l1 1-3.5 5.5L4 10 9.5 6.5l1 1 2.5-2.5-1-1z"
                    stroke="currentColor"
                    strokeWidth="1"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    fill="none"
                  />
                </svg>
                {tool}
              </span>
            ))}
          </div>
        </div>
      )}

      {agentChain.length > 0 && (
        <div>
          <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Agent chain</span>
          <div className="mt-1 flex items-center gap-1.5 flex-wrap">
            {agentChain.map((agent, i) => (
              <span key={agent} className="flex items-center gap-1.5">
                <span className="text-xs text-slate-300 bg-slate-700/50 border border-slate-600/30 px-2 py-0.5 rounded">
                  {agent}
                </span>
                {i < agentChain.length - 1 && (
                  <svg className="w-3 h-3 text-slate-600 flex-shrink-0" fill="none" viewBox="0 0 12 12">
                    <path d="M4 6h4M6 4l2 2-2 2" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
