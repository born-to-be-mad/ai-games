import { useEffect, useRef } from 'react'
import type { Message } from '../types/events'
import { StreamingMessageRenderer } from './StreamingMessageRenderer'

interface Props {
  messages: Message[]
}

const EXAMPLE_QUERIES = [
  'What were the deadliest earthquakes in the 21st century?',
  'Show me hurricane trends over the last 20 years',
  'Compare flood frequency across continents',
  'Which regions have the highest volcanic activity?',
]

export function ChatWindow({ messages }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center px-4 py-12 text-center">
        <div className="w-14 h-14 rounded-2xl bg-terra-600/20 border border-terra-600/30 flex items-center justify-center mb-5">
          <svg className="w-7 h-7 text-terra-500" fill="none" viewBox="0 0 24 24">
            <path
              d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15v-4H7l5-8v4h4l-5 8z"
              fill="currentColor"
            />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-slate-100 mb-2">TerraQuery</h1>
        <p className="text-slate-400 text-sm max-w-md mb-8">
          AI-powered natural disaster research. Ask about earthquakes, floods, hurricanes, and more.
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-w-xl w-full">
          {EXAMPLE_QUERIES.map(q => (
            <button
              key={q}
              className="text-left px-4 py-3 bg-slate-800/50 border border-slate-700/50 rounded-xl text-sm text-slate-300 hover:bg-slate-700/50 hover:border-slate-600 hover:text-slate-100 transition-colors"
              onClick={() => {
                const input = document.querySelector<HTMLTextAreaElement>('textarea')
                if (input) {
                  input.value = q
                  input.focus()
                  input.dispatchEvent(new Event('input', { bubbles: true }))
                }
              }}
            >
              {q}
            </button>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div
      className="flex-1 overflow-y-auto scrollbar-thin px-4 py-6"
      role="log"
      aria-live="polite"
      aria-label="Conversation"
    >
      <div className="max-w-4xl mx-auto space-y-6">
        {messages.map(msg => (
          <StreamingMessageRenderer key={msg.id} message={msg} />
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
