/**
 * Security Utilities for Member Tracker Application
 * Provides security-related functions, CSP configuration, and security best practices
 * Note: CSP should be configured server-side or in index.html meta tags for production
 */

/**
 * Security configuration
 */
const securityConfig = {
  // Content Security Policy (CSP) configuration - configure in server or index.html
  csp: {
    enabled: import.meta.env.VITE_CSP_ENABLED === 'true',
    directives: {
      'default-src': ["'self'"],
      'script-src': ["'self'", "'unsafe-inline'", "'unsafe-eval'"], // Remove unsafe-inline/eval in production
      'style-src': ["'self'", "'unsafe-inline'", "https://fonts.googleapis.com"],
      'font-src': ["'self'", "https://fonts.gstatic.com", "data:"],
      'img-src': ["'self'", "data:", "https:"],
      'connect-src': ["'self'", "ws:", "wss:", "http://localhost:8080", "https://localhost:8080"],
      'frame-src': ["'none'"],
      'object-src': ["'none'"],
      'base-uri': ["'self'"],
      'form-action': ["'self'"],
      'frame-ancestors': ["'none'"],
      'block-all-mixed-content': [],
      'upgrade-insecure-requests': []
    }
  },

  // Security headers configuration - set server-side
  headers: {
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'X-XSS-Protection': '1; mode=block',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()'
  },

  // Input validation configuration
  validation: {
    maxInputLength: 1000,
    allowedHtmlTags: [], // No HTML tags allowed by default
    allowedAttributes: [],
    maxFileSize: 10 * 1024 * 1024, // 10MB
    allowedFileTypes: ['image/jpeg', 'image/png', 'image/gif', 'application/pdf']
  }
}

/**
 * Security Manager Class
 */
class SecurityManager {
  constructor() {
    this.isCSPEnabled = securityConfig.csp.enabled
    this.securityEvents = []
  }

  /**
   * Generate CSP header string (use in server response or index.html)
   */
  generateCSPHeader() {
    const directives = securityConfig.csp.directives
    const cspParts = []

    for (const [directive, sources] of Object.entries(directives)) {
      if (sources.length > 0) {
        cspParts.push(`${directive} ${sources.join(' ')}`)
      } else {
        cspParts.push(directive)
      }
    }

    return cspParts.join('; ')
  }

  /**
   * Log security events
   */
  logSecurityEvent(type, data, severity = 'medium') {
    const event = {
      type,
      data,
      severity,
      timestamp: new Date().toISOString(),
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'unknown',
      url: typeof window !== 'undefined' ? window.location?.href : 'unknown'
    }

    this.securityEvents.push(event)

    // Keep only last 100 events
    if (this.securityEvents.length > 100) {
      this.securityEvents.shift()
    }

    // Log based on severity
    const logMethod = {
      low: console.debug,
      medium: console.warn,
      high: console.error
    }[severity] || console.warn

    logMethod(`Security event: ${type}`, event)

    // Emit security event if window is available
    if (typeof window !== 'undefined' && window.dispatchEvent) {
      window.dispatchEvent(new CustomEvent('security-event', {
        detail: event
      }))
    }

    return event
  }

  /**
   * Input validation and sanitization
   */
  validateInput(input, options = {}) {
    const {
      type = 'text',
      maxLength = securityConfig.validation.maxInputLength,
      required = false,
      pattern = null
    } = options

    // Check required field
    if (required && (!input || input.trim() === '')) {
      throw new SecurityError('INPUT_REQUIRED', 'This field is required')
    }

    // Check max length
    if (input && input.length > maxLength) {
      throw new SecurityError('INPUT_TOO_LONG', `Input exceeds maximum length of ${maxLength} characters`)
    }

    // Check pattern
    if (pattern && input && !pattern.test(input)) {
      throw new SecurityError('INPUT_PATTERN_MISMATCH', 'Input does not match required pattern')
    }

    // Type-specific validation
    switch (type) {
      case 'email':
        if (input && !this.isValidEmail(input)) {
          throw new SecurityError('INVALID_EMAIL', 'Invalid email format')
        }
        break

      case 'phone':
        if (input && !this.isValidPhone(input)) {
          throw new SecurityError('INVALID_PHONE', 'Invalid phone number format')
        }
        break

      case 'number':
        if (input && isNaN(Number(input))) {
          throw new SecurityError('INVALID_NUMBER', 'Invalid number format')
        }
        break

      case 'url':
        if (input && !this.isValidUrl(input)) {
          throw new SecurityError('INVALID_URL', 'Invalid URL format')
        }
        break
    }

    return this.sanitizeInput(input, type)
  }

  /**
   * Sanitize input based on type (regex-based, no DOM)
   */
  sanitizeInput(input, type = 'text') {
    if (!input) return input

    let sanitized = input.toString().trim()

    switch (type) {
      case 'html':
        sanitized = this.stripHtmlTags(sanitized)
        break

      case 'text':
        sanitized = this.stripHtmlTags(sanitized)
        break

      case 'email':
        sanitized = sanitized.toLowerCase()
        break

      case 'number':
        sanitized = sanitized.replace(/[^\d.-]/g, '')
        break
    }

    return sanitized
  }

  /**
   * Strip HTML tags using regex (simple, for basic sanitization)
   */
  stripHtmlTags(html) {
    return html.replace(/<[^>]*>/g, '').replace(/&[a-zA-Z0-9#]+;/g, '')
  }

  /**
   * Validate file upload
   */
  validateFile(file, options = {}) {
    const {
      maxSize = securityConfig.validation.maxFileSize,
      allowedTypes = securityConfig.validation.allowedFileTypes,
      required = false
    } = options

    // Check if file is provided
    if (required && !file) {
      throw new SecurityError('FILE_REQUIRED', 'File is required')
    }

    if (!file) return true

    // Check file size
    if (file.size > maxSize) {
      throw new SecurityError('FILE_TOO_LARGE', `File size exceeds ${maxSize / 1024 / 1024}MB limit`)
    }

    // Check file type
    if (allowedTypes.length > 0 && !allowedTypes.includes(file.type)) {
      throw new SecurityError('INVALID_FILE_TYPE', `File type ${file.type} is not allowed`)
    }

    // Check file name for path traversal
    if (file.name.includes('..') || file.name.includes('/') || file.name.includes('\\')) {
      throw new SecurityError('INVALID_FILE_NAME', 'Invalid file name')
    }

    return true
  }

  /**
   * Email validation
   */
  isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(email)
  }

  /**
   * Phone number validation (basic)
   */
  isValidPhone(phone) {
    const phoneRegex = /^[\+]?[1-9][\d]{0,15}$/
    return phoneRegex.test(phone.replace(/[\s\-\(\)]/g, ''))
  }

  /**
   * URL validation
   */
  isValidUrl(url) {
    try {
      new URL(url)
      return true
    } catch {
      return false
    }
  }

  /**
   * Generate secure random token
   */
  generateSecureToken(length = 32) {
    const array = new Uint8Array(length)
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      crypto.getRandomValues(array)
    } else {
      // Fallback for environments without crypto
      for (let i = 0; i < length; i++) {
        array[i] = Math.floor(Math.random() * 256)
      }
    }
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('')
  }

  /**
   * Hash data (simple implementation - for non-critical use)
   */
  async hashData(data, algorithm = 'SHA-256') {
    if (typeof crypto === 'undefined' || !crypto.subtle) {
      console.warn('Web Crypto API not available for hashing')
      return this.generateSecureToken(32) // Fallback
    }

    const encoder = new TextEncoder()
    const dataBuffer = encoder.encode(data)
    const hashBuffer = await crypto.subtle.digest(algorithm, dataBuffer)
    const hashArray = Array.from(new Uint8Array(hashBuffer))
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
  }

  /**
   * Get security report
   */
  getSecurityReport() {
    return {
      csp: {
        enabled: this.isCSPEnabled,
        header: this.isCSPEnabled ? this.generateCSPHeader() : null
      },
      headers: securityConfig.headers,
      events: {
        total: this.securityEvents.length,
        bySeverity: this.securityEvents.reduce((acc, event) => {
          acc[event.severity] = (acc[event.severity] || 0) + 1
          return acc
        }, {}),
        recent: this.securityEvents.slice(-10)
      },
      environment: import.meta.env.NODE_ENV,
      timestamp: new Date().toISOString()
    }
  }

  /**
   * Clear security events
   */
  clearSecurityEvents() {
    this.securityEvents = []
  }
}

/**
 * Custom Security Error Class
 */
class SecurityError extends Error {
  constructor(code, message, details = null) {
    super(message)
    this.name = 'SecurityError'
    this.code = code
    this.details = details
    this.timestamp = new Date().toISOString()
  }
}

// Create global security manager instance
const securityManager = new SecurityManager()

/**
 * Security utility functions
 */
export const security = {
  /**
   * Input validation
   */
  validate: (input, options) => securityManager.validateInput(input, options),

  /**
   * File validation
   */
  validateFile: (file, options) => securityManager.validateFile(file, options),

  /**
   * Sanitization
   */
  sanitize: (input, type) => securityManager.sanitizeInput(input, type),
  sanitizeHTML: (html) => securityManager.sanitizeInput(html, 'html'),

  /**
   * Validation helpers
   */
  isValidEmail: (email) => securityManager.isValidEmail(email),
  isValidPhone: (phone) => securityManager.isValidPhone(phone),
  isValidUrl: (url) => securityManager.isValidUrl(url),

  /**
   * Cryptography
   */
  generateToken: (length) => securityManager.generateSecureToken(length),
  hashData: (data, algorithm) => securityManager.hashData(data, algorithm),

  /**
   * Security monitoring
   */
  logEvent: (type, data, severity) => securityManager.logSecurityEvent(type, data, severity),
  getReport: () => securityManager.getSecurityReport(),
  clearEvents: () => securityManager.clearSecurityEvents(),

  /**
   * Configuration
   */
  getConfig: () => securityConfig,
  isCSPEnabled: securityManager.isCSPEnabled
}

// Export security manager for advanced usage
export { securityManager, SecurityError }

export default security
