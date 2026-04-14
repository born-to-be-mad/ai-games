import { useCallback, useRef, useState } from 'react'
import { ChatWindow } from './components/ChatWindow'
import { MessageInput } from './components/MessageInput'
import { VisualizationPanel } from './components/VisualizationPanel'
import { useSSE } from './hooks/useSSE'
import type { Message } from './types/events'

const STREAM_URL = '/api/v1/chat/stream'

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
  const [showViz, setShowViz] = useState(false)
  const streamingIdRef = useRef<string | null>(null)

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
    setShowViz(false)
    streamingIdRef.current = null
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
        <VisualizationPanel
          toolsUsed={vizData.toolsUsed}
          agentChain={vizData.agentChain}
          visible={showViz}
        />
      </div>
    </div>
  )
}
