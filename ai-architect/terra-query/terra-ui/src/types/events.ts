export type EventType =
  | 'TOOL_CALL_START'
  | 'TOOL_CALL_END'
  | 'AGENT_THINKING'
  | 'ANSWER_CHUNK'
  | 'ANSWER_COMPLETE'

export interface ToolCallStartEvent {
  type: 'TOOL_CALL_START'
  tool: string
  agent: string
}

export interface ToolCallEndEvent {
  type: 'TOOL_CALL_END'
  tool: string
  agent: string
  resultPreview: string
}

export interface AgentThinkingEvent {
  type: 'AGENT_THINKING'
  agent: string
  status: string
}

export interface AnswerChunkEvent {
  type: 'ANSWER_CHUNK'
  text: string
}

export interface AnswerCompleteEvent {
  type: 'ANSWER_COMPLETE'
  toolsUsed: string[]
  sources: string[]
  agentChain: string[]
}

export type ChatEvent =
  | ToolCallStartEvent
  | ToolCallEndEvent
  | AgentThinkingEvent
  | AnswerChunkEvent
  | AnswerCompleteEvent

export interface ChatRequest {
  message: string
  conversationId?: string
}

export interface ToolProgress {
  tool: string
  agent: string
  status: 'running' | 'done'
  resultPreview?: string
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  isStreaming?: boolean
  toolProgress?: ToolProgress[]
  thinkingSteps?: AgentThinkingEvent[]
  sources?: string[]
  toolsUsed?: string[]
  agentChain?: string[]
}
