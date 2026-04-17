import { useCallback, useEffect, useRef, useState } from 'react'
import { ChatWindow } from './components/ChatWindow'
import { MessageInput } from './components/MessageInput'
import { VisualizationPanel } from './components/VisualizationPanel'
import { useSSE } from './hooks/useSSE'
import type { Message } from './types/events'

const STREAM_URL = '/api/v1/chat/stream'
const INSIGHTS_MIN_WIDTH = 420
const INSIGHTS_MAX_WIDTH = 900
const INSIGHTS_DEFAULT_WIDTH = 560

function generateId() {
  return crypto.randomUUID()
}

export default function App() {
  const [messages, setMessages] = useState<Message[]>([])
  const [conversationId, setConversationId] = useState<string | undefined>()
  const [vizData, setVizData] = useState<{ toolsUsed: string[]; agentChain: string[] }>({
    toolsUsed: [],
    agentChain: [],
  })
  const [mapSignalText, setMapSignalText] = useState('')
  const [showViz, setShowViz] = useState(false)
  const [insightsWidth, setInsightsWidth] = useState(INSIGHTS_DEFAULT_WIDTH)
  const [runtimeConfig, setRuntimeConfig] = useState<{
    provider: string
    model: string
    embeddingModel: string
    contextWindowStrategy: string
    maxQueriesPerMinute: number
    dailyCostCapUsd: number
    dailyCostSpentUsd: number
    dailyCostRemainingUsd: number
  } | null>(null)
  const streamingIdRef = useRef<string | null>(null)
  const mapSignalRef = useRef('')
  const isResizingRef = useRef(false)

  const { status, send, abort } = useSSE(STREAM_URL)
  const isStreaming = status === 'streaming'

  const updateStreamingMessage = useCallback((updater: (msg: Message) => Message) => {
    setMessages(prev =>
      prev.map(m => (m.id === streamingIdRef.current ? updater(m) : m))
    )
  }, [])

  const handleSend = useCallback(
    (text: string) => {
      const userMsgId = generateId()
      const assistantMsgId = generateId()
      streamingIdRef.current = assistantMsgId

      const userMsg: Message = { id: userMsgId, role: 'user', content: text }
      const assistantMsg: Message = {
        id: assistantMsgId,
        role: 'assistant',
        content: '',
        isStreaming: true,
        toolProgress: [],
        thinkingSteps: [],
      }

      setMessages(prev => [...prev, userMsg, assistantMsg])
      mapSignalRef.current = text

      send(
        { message: text, conversationId },
        {
          onToolCallStart(e) {
            updateStreamingMessage(msg => ({
              ...msg,
              toolProgress: [
                ...(msg.toolProgress ?? []),
                { tool: e.tool, agent: e.agent, status: 'running' },
              ],
            }))
          },

          onToolCallEnd(e) {
            mapSignalRef.current = `${mapSignalRef.current} ${e.resultPreview ?? ''}`.trim()
            updateStreamingMessage(msg => ({
              ...msg,
              toolProgress: (msg.toolProgress ?? []).map(t =>
                t.tool === e.tool && t.agent === e.agent && t.status === 'running'
                  ? { ...t, status: 'done', resultPreview: e.resultPreview }
                  : t
              ),
            }))
          },

          onAgentThinking(e) {
            updateStreamingMessage(msg => ({
              ...msg,
              thinkingSteps: [...(msg.thinkingSteps ?? []), e],
            }))
          },

          onAnswerChunk(e) {
            mapSignalRef.current = `${mapSignalRef.current} ${e.text}`.trim()
            updateStreamingMessage(msg => ({
              ...msg,
              content: msg.content + e.text,
            }))
          },

          onAnswerComplete(e) {
            if (!conversationId) {
              setConversationId(generateId())
            }
            setVizData({ toolsUsed: e.toolsUsed, agentChain: e.agentChain })
            setMapSignalText(`${mapSignalRef.current} ${e.sources.join(' ')}`.trim())
            setShowViz(true)
            updateStreamingMessage(msg => ({
              ...msg,
              isStreaming: false,
              sources: e.sources,
              toolsUsed: e.toolsUsed,
              agentChain: e.agentChain,
            }))
            streamingIdRef.current = null
          },

          onError(errMsg) {
            updateStreamingMessage(msg => ({
              ...msg,
              isStreaming: false,
              content: msg.content || `Error: ${errMsg}`,
            }))
            streamingIdRef.current = null
          },

          onDone() {
            // Ensure streaming flag cleared even if ANSWER_COMPLETE was missed
            updateStreamingMessage(msg =>
              msg.isStreaming ? { ...msg, isStreaming: false } : msg
            )
            streamingIdRef.current = null
          },
        }
      )
    },
    [conversationId, send, updateStreamingMessage]
  )

  const handleNewConversation = () => {
    if (isStreaming) abort()
    setMessages([])
    setConversationId(undefined)
    setVizData({ toolsUsed: [], agentChain: [] })
    setMapSignalText('')
    setShowViz(false)
    streamingIdRef.current = null
    mapSignalRef.current = ''
  }

  useEffect(() => {
    let cancelled = false

    const loadRuntimeConfig = async () => {
      try {
        const response = await fetch('/api/v1/config/runtime')
        if (!response.ok) {
          return
        }
        const config = await response.json()
        if (!cancelled) {
          setRuntimeConfig(config)
        }
      } catch {
        // Non-blocking: chat UI should still work if config endpoint is unavailable.
      }
    }

    loadRuntimeConfig()
    const intervalId = window.setInterval(loadRuntimeConfig, 30000)
    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [])

  useEffect(() => {
    const onMouseMove = (event: MouseEvent) => {
      if (!isResizingRef.current || !showViz) {
        return
      }
      const desired = window.innerWidth - event.clientX
      const clamped = Math.max(INSIGHTS_MIN_WIDTH, Math.min(INSIGHTS_MAX_WIDTH, desired))
      setInsightsWidth(clamped)
    }

    const onMouseUp = () => {
      isResizingRef.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
    return () => {
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }
  }, [showViz])

  const startResizing = () => {
    if (!showViz) {
      return
    }
    isResizingRef.current = true
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      {/* Header */}
      <header className="flex-shrink-0 flex items-center justify-between px-4 py-3 border-b border-slate-700/50 bg-slate-900/80 backdrop-blur-sm">
        <div className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-lg bg-terra-600/30 border border-terra-600/40 flex items-center justify-center">
            <svg className="w-4 h-4 text-terra-400" fill="currentColor" viewBox="0 0 16 16">
              <path d="M8 1a7 7 0 100 14A7 7 0 008 1zm-.5 9.5v-3l-2 2-1-1 3-3 3 3-1 1-2-2v3h-1z" />
            </svg>
          </div>
          <span className="font-semibold text-slate-100 text-sm">TerraQuery</span>
          {conversationId && (
            <span className="text-xs text-slate-600 font-mono hidden sm:inline">
              {conversationId.slice(0, 8)}
            </span>
          )}
          {runtimeConfig && (
            <>
              <span className="hidden lg:inline text-[11px] px-2 py-0.5 rounded-full border border-slate-700 bg-slate-800 text-slate-300">
                {runtimeConfig.provider}:{runtimeConfig.model}
              </span>
              <span className="hidden xl:inline text-[11px] px-2 py-0.5 rounded-full border border-slate-700 bg-slate-800 text-slate-300">
                RAG:{runtimeConfig.embeddingModel}
              </span>
              <span className="hidden xl:inline text-[11px] px-2 py-0.5 rounded-full border border-slate-700 bg-slate-800 text-slate-300">
                Cost ${runtimeConfig.dailyCostSpentUsd.toFixed(3)} / ${runtimeConfig.dailyCostCapUsd.toFixed(2)}
              </span>
            </>
          )}
        </div>

        <div className="flex items-center gap-2">
          {isStreaming && (
            <button
              onClick={abort}
              className="text-xs px-2.5 py-1 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 hover:bg-red-500/20 transition-colors"
            >
              Stop
            </button>
          )}
          <button
            onClick={() => setShowViz(v => !v)}
            className={`text-xs px-2.5 py-1 rounded-lg border transition-colors ${
              showViz
                ? 'bg-terra-600/20 border-terra-600/40 text-terra-300'
                : 'bg-slate-800 border-slate-700 text-slate-400 hover:text-slate-300'
            }`}
          >
            Insights
          </button>
          <button
            onClick={handleNewConversation}
            className="text-xs px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700 text-slate-400 hover:text-slate-300 hover:bg-slate-700 transition-colors"
          >
            New chat
          </button>
        </div>
      </header>

      {/* Main area */}
      <div className="flex-1 flex overflow-hidden">
        <div className="flex-1 flex flex-col overflow-hidden">
          <ChatWindow messages={messages} />
          <MessageInput onSend={handleSend} disabled={isStreaming} />
        </div>
        {showViz && (
          <button
            type="button"
            onMouseDown={startResizing}
            aria-label="Resize insights panel"
            className="w-2 cursor-col-resize bg-slate-900/70 border-l border-slate-700/50 hover:bg-terra-500/30 transition-colors"
          />
        )}
        <VisualizationPanel
          toolsUsed={vizData.toolsUsed}
          agentChain={vizData.agentChain}
          signalText={mapSignalText}
          widthPx={insightsWidth}
          visible={showViz}
        />
      </div>
    </div>
  )
}
