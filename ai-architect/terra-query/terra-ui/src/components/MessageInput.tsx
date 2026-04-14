import { useRef, type KeyboardEvent } from 'react'

interface Props {
  onSend: (message: string) => void
  disabled: boolean
}

export function MessageInput({ onSend, disabled }: Props) {
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const handleSend = () => {
    const value = textareaRef.current?.value.trim()
    if (!value || disabled) return
    onSend(value)
    if (textareaRef.current) {
      textareaRef.current.value = ''
      textareaRef.current.style.height = 'auto'
    }
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleInput = () => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`
  }

  return (
    <div className="border-t border-slate-700/50 bg-slate-900/80 backdrop-blur-sm px-4 py-3">
      <div className="flex items-end gap-2 max-w-4xl mx-auto">
        <div className="flex-1 relative">
          <textarea
            ref={textareaRef}
            rows={1}
            disabled={disabled}
            onKeyDown={handleKeyDown}
            onInput={handleInput}
            placeholder="Ask about natural disasters, trends, statistics…"
            aria-label="Chat message input"
            className="w-full resize-none bg-slate-800 border border-slate-600/50 rounded-xl px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-terra-500/60 focus:ring-1 focus:ring-terra-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors scrollbar-thin"
            style={{ minHeight: 42 }}
          />
        </div>
        <button
          onClick={handleSend}
          disabled={disabled}
          aria-label="Send message"
          className="flex-shrink-0 w-10 h-10 rounded-xl bg-terra-600 hover:bg-terra-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center justify-center focus-visible:ring-2 focus-visible:ring-terra-400"
        >
          {disabled ? (
            <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
          ) : (
            <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 16 16">
              <path
                d="M14 8L2 2l2.5 6L2 14l12-6z"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinejoin="round"
                fill="none"
              />
            </svg>
          )}
        </button>
      </div>
      <p className="text-center text-xs text-slate-600 mt-2">
        Enter to send · Shift+Enter for new line
      </p>
    </div>
  )
}
