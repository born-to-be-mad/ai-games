import type { ToolProgress } from '../types/events'

interface Props {
  tools: ToolProgress[]
}

export function ToolProgressIndicator({ tools }: Props) {
  if (tools.length === 0) return null

  return (
    <div className="mt-2 space-y-1.5" role="status" aria-live="polite" aria-label="Tool execution progress">
      {tools.map((t, i) => (
        <div key={`${t.tool}-${i}`} className="flex items-start gap-2 text-xs animate-fade-in">
          <span className="mt-0.5 flex-shrink-0">
            {t.status === 'running' ? (
              <span
                className="inline-block w-3.5 h-3.5 border-2 border-terra-500 border-t-transparent rounded-full animate-spin"
                aria-label="Running"
              />
            ) : (
              <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-emerald-500/20" aria-label="Done">
                <svg className="w-2.5 h-2.5 text-emerald-400" fill="none" viewBox="0 0 12 12">
                  <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </span>
            )}
          </span>
          <div className="min-w-0">
            <span className="text-slate-400">
              <span className="text-slate-300 font-medium">{t.tool}</span>
              {' '}via{' '}
              <span className="text-slate-400">{t.agent}</span>
            </span>
            {t.resultPreview && (
              <p className="text-slate-500 truncate mt-0.5" title={t.resultPreview}>
                {t.resultPreview}
              </p>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
