import { useEffect, useRef, useState } from 'react'
import type { AgentThinkingEvent } from '../types/events'

interface Props {
  steps: AgentThinkingEvent[]
  isStreaming: boolean
}

export function AgentThinkingPanel({ steps, isStreaming }: Props) {
  const [collapsed, setCollapsed] = useState(false)
  const collapseTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Auto-collapse 2s after streaming ends
  useEffect(() => {
    if (!isStreaming && steps.length > 0) {
      collapseTimerRef.current = setTimeout(() => setCollapsed(true), 2000)
    }
    return () => {
      if (collapseTimerRef.current) clearTimeout(collapseTimerRef.current)
    }
  }, [isStreaming, steps.length])

  if (steps.length === 0) return null

  return (
    <div className="mt-2 rounded-lg border border-slate-700/50 bg-slate-800/30 overflow-hidden animate-fade-in">
      <button
        className="w-full flex items-center justify-between px-3 py-2 text-xs text-slate-400 hover:text-slate-300 hover:bg-slate-800/50 transition-colors"
        onClick={() => setCollapsed(c => !c)}
        aria-expanded={!collapsed}
        aria-controls="thinking-steps"
      >
        <span className="flex items-center gap-1.5">
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 16 16">
            <path
              d="M8 1a7 7 0 100 14A7 7 0 008 1zm0 3a1 1 0 110 2 1 1 0 010-2zm1 8H7V7h2v5z"
              fill="currentColor"
            />
          </svg>
          Agent reasoning ({steps.length} step{steps.length !== 1 ? 's' : ''})
        </span>
        <svg
          className={`w-3.5 h-3.5 transition-transform ${collapsed ? '' : 'rotate-180'}`}
          fill="none"
          viewBox="0 0 16 16"
        >
          <path d="M4 6l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </button>

      {!collapsed && (
        <div id="thinking-steps" className="px-3 pb-3 space-y-1.5 animate-slide-down">
          {steps.map((s, i) => (
            <div key={i} className="flex items-start gap-2 text-xs">
              <span className="mt-0.5 w-4 h-4 rounded-full bg-slate-700 flex items-center justify-center text-slate-400 flex-shrink-0 text-[10px]">
                {i + 1}
              </span>
              <div>
                <span className="text-terra-500 font-medium">{s.agent}</span>
                <span className="text-slate-500 mx-1">—</span>
                <span className="text-slate-400">{s.status}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
