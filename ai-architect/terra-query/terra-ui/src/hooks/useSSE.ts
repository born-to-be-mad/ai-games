import { useCallback, useRef, useState } from 'react'
import type {
  AnswerChunkEvent,
  AnswerCompleteEvent,
  AgentThinkingEvent,
  ToolCallEndEvent,
  ToolCallStartEvent,
  ChatRequest,
} from '../types/events'

export type SSEStatus = 'idle' | 'streaming' | 'complete' | 'error'

export interface SSEHandlers {
  onToolCallStart?: (e: ToolCallStartEvent) => void
  onToolCallEnd?: (e: ToolCallEndEvent) => void
  onAgentThinking?: (e: AgentThinkingEvent) => void
  onAnswerChunk?: (e: AnswerChunkEvent) => void
  onAnswerComplete?: (e: AnswerCompleteEvent) => void
  onError?: (msg: string) => void
  onDone?: () => void
}

const MAX_RETRIES = 5
const BASE_DELAY_MS = 1000
const MAX_DELAY_MS = 30_000

/**
 * POST-based SSE hook.
 * Standard EventSource only supports GET; this uses fetch + ReadableStream
 * to consume a text/event-stream response from a POST endpoint.
 * Includes exponential backoff reconnection (max 5 retries, 30s cap).
 */
export function useSSE(url: string) {
  const [status, setStatus] = useState<SSEStatus>('idle')
  const abortRef = useRef<AbortController | null>(null)
  const retriesRef = useRef(0)

  const abort = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setStatus('idle')
  }, [])

  const send = useCallback(
    async (body: ChatRequest, handlers: SSEHandlers) => {
      // Cancel any in-flight request
      abortRef.current?.abort()
      retriesRef.current = 0

      const attempt = async (): Promise<void> => {
        const controller = new AbortController()
        abortRef.current = controller
        setStatus('streaming')

        try {
          const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
            body: JSON.stringify(body),
            signal: controller.signal,
          })

          if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`)
          }
          if (!response.body) {
            throw new Error('Response body is null')
          }

          const reader = response.body.getReader()
          const decoder = new TextDecoder()
          let buffer = ''

          while (true) {
            const { value, done } = await reader.read()
            if (done) break

            buffer += decoder.decode(value, { stream: true })

            // SSE messages are separated by double newline
            const messages = buffer.split('\n\n')
            // Last element may be incomplete — keep in buffer
            buffer = messages.pop() ?? ''

            for (const message of messages) {
              if (!message.trim()) continue

              let eventType = ''
              let dataLine = ''

              for (const line of message.split('\n')) {
                if (line.startsWith('event:')) {
                  eventType = line.slice(6).trim()
                } else if (line.startsWith('data:')) {
                  dataLine = line.slice(5).trim()
                }
              }

              if (!dataLine) continue

              try {
                const parsed = JSON.parse(dataLine)
                dispatch(eventType, parsed, handlers)
              } catch {
                // Malformed JSON — skip
              }
            }
          }

          retriesRef.current = 0
          setStatus('complete')
          handlers.onDone?.()
        } catch (err) {
          if ((err as Error).name === 'AbortError') return

          const retries = retriesRef.current
          if (retries < MAX_RETRIES) {
            const delay = Math.min(BASE_DELAY_MS * 2 ** retries, MAX_DELAY_MS)
            retriesRef.current = retries + 1
            setTimeout(attempt, delay)
          } else {
            const msg = err instanceof Error ? err.message : 'Stream failed'
            setStatus('error')
            handlers.onError?.(msg)
          }
        }
      }

      await attempt()
    },
    [url]
  )

  return { status, send, abort }
}

function dispatch(eventType: string, data: unknown, handlers: SSEHandlers) {
  switch (eventType) {
    case 'TOOL_CALL_START':
      handlers.onToolCallStart?.(data as ToolCallStartEvent)
      break
    case 'TOOL_CALL_END':
      handlers.onToolCallEnd?.(data as ToolCallEndEvent)
      break
    case 'AGENT_THINKING':
      handlers.onAgentThinking?.(data as AgentThinkingEvent)
      break
    case 'ANSWER_CHUNK':
      handlers.onAnswerChunk?.(data as AnswerChunkEvent)
      break
    case 'ANSWER_COMPLETE':
      handlers.onAnswerComplete?.(data as AnswerCompleteEvent)
      break
  }
}
