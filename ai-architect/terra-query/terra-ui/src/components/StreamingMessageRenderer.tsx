import ReactMarkdown from 'react-markdown'
import type { Message } from '../types/events'
import { AgentThinkingPanel } from './AgentThinkingPanel'
import { SourceCitations } from './SourceCitations'
import { ToolProgressIndicator } from './ToolProgressIndicator'

interface Props {
  message: Message
}

export function StreamingMessageRenderer({ message }: Props) {
  const isAssistant = message.role === 'assistant'
  const isStreaming = message.isStreaming ?? false

  if (!isAssistant) {
    return (
      <div className="flex justify-end">
        <div className="max-w-[75%] bg-terra-600/20 border border-terra-600/30 rounded-2xl rounded-br-sm px-4 py-2.5 text-sm text-slate-100">
          {message.content}
        </div>
      </div>
    )
  }

  return (
    <div className="flex justify-start">
      <div className="max-w-[85%] w-full">
        {/* Tool progress — shown while streaming */}
        {(message.toolProgress?.length ?? 0) > 0 && (
          <div className="mb-2 bg-slate-800/40 border border-slate-700/40 rounded-xl px-3 py-2">
            <p className="text-xs font-medium text-slate-500 mb-1.5 uppercase tracking-wider">Processing</p>
            <ToolProgressIndicator tools={message.toolProgress ?? []} />
          </div>
        )}

        {/* Agent thinking steps */}
        {(message.thinkingSteps?.length ?? 0) > 0 && (
          <AgentThinkingPanel
            steps={message.thinkingSteps ?? []}
            isStreaming={isStreaming}
          />
        )}

        {/* Answer content */}
        {message.content && (
          <div
            className={`mt-2 bg-slate-800/30 border border-slate-700/40 rounded-2xl rounded-bl-sm px-4 py-3 ${
              isStreaming ? 'streaming-cursor' : ''
            }`}
          >
            <div className="prose-terra text-sm">
              <ReactMarkdown>{message.content}</ReactMarkdown>
            </div>
          </div>
        )}

        {/* Source citations — appear after ANSWER_COMPLETE */}
        {!isStreaming && message.sources !== undefined && (
          <SourceCitations
            sources={message.sources ?? []}
            toolsUsed={message.toolsUsed ?? []}
            agentChain={message.agentChain ?? []}
          />
        )}
      </div>
    </div>
  )
}
