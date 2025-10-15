/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  // Environment Variables
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_VERSION: string
  readonly VITE_APP_DESCRIPTION: string
  readonly VITE_APP_AUTHOR: string

  // Development Features
  readonly VITE_ENABLE_DEBUG_TOOLS: string
  readonly VITE_ENABLE_MOCK_API: string
  readonly VITE_LOG_LEVEL: string

  // API Configuration
  readonly VITE_API_TIMEOUT: string
  readonly VITE_API_RETRY_ATTEMPTS: string

  // Security
  readonly VITE_CSP_ENABLED: string

  // Performance
  readonly VITE_ENABLE_COMPRESSION: string
  readonly VITE_ENABLE_CACHING: string

  // Environment
  readonly NODE_ENV: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Global type definitions
declare global {
  // Utility types for better TypeScript experience
  type Nullable<T> = T | null
  type Optional<T> = T | undefined
  type Maybe<T> = T | null | undefined

  // API Response types
  interface ApiResponse<T = any> {
    data: T
    status: number
    message?: string
    timestamp: string
  }

  interface PaginatedResponse<T = any> extends ApiResponse<T[]> {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }

  // Error types
  interface ApiError {
    code: string
    message: string
    details?: any
    timestamp: string
  }

  // Member types
  interface Member {
    id: number
    firstName: string
    lastName: string
    email: string
    phone: string
    address: string
    joinDate: string
    status: 'ACTIVE' | 'INACTIVE'
    paymentStatus: 'CURRENT' | 'OVERDUE'
    lastPaymentDate?: string
  }

  // Payment types
  interface Payment {
    id: number
    memberId: number
    amount: number
    paymentDate: string
    paymentMethod: 'CASH' | 'CARD' | 'BANK_TRANSFER'
    notes?: string
  }

  // Communication types
  interface Communication {
    id: number
    subject: string
    message: string
    type: 'EMAIL' | 'SMS' | 'NOTIFICATION'
    status: 'DRAFT' | 'SENT' | 'FAILED'
    sentDate?: string
    recipientCount: number
  }
}

export {}
