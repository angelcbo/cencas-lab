import { createContext, useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi, type LoginCredentials } from './authApi'
import { clearAccessToken, registerUnauthorizedHandler, setAccessToken } from '@/api/client'
import type { UserInfo } from '@/api/types'

interface AuthContextValue {
  user: UserInfo | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (credentials: LoginCredentials) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const navigate = useNavigate()
  const initializedRef = useRef(false)

  const handleUnauthorized = useCallback(() => {
    clearAccessToken()
    setUser(null)
    navigate('/login', { replace: true })
  }, [navigate])

  // Register the 401 handler once so the axios interceptor can call it
  useEffect(() => {
    registerUnauthorizedHandler(handleUnauthorized)
  }, [handleUnauthorized])

  // Silent refresh on mount — restores session if cookie is still valid
  useEffect(() => {
    if (initializedRef.current) return
    initializedRef.current = true

    authApi
      .refresh()
      .then((data) => {
        setAccessToken(data.accessToken)
        // Fetch user info from the token by calling login? No — refresh doesn't return user.
        // We store user from the last login. On refresh we only get a new access token.
        // Since JWT carries the claims, decode the payload to rebuild UserInfo.
        const payload = parseJwtPayload(data.accessToken)
        if (payload) {
          setUser({
            id: payload.sub,
            email: payload.email,
            firstName: payload.firstName ?? '',
            lastName: payload.lastName ?? '',
            role: payload.role,
            tenantId: payload.tenantId ?? null,
          })
        }
      })
      .catch(() => {
        // No valid session — show login
      })
      .finally(() => {
        setIsLoading(false)
      })
  }, [])

  const login = useCallback(async (credentials: LoginCredentials) => {
    const data = await authApi.login(credentials)
    setAccessToken(data.accessToken)
    setUser(data.user)
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      clearAccessToken()
      setUser(null)
      navigate('/login', { replace: true })
    }
  }, [navigate])

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: user !== null,
        isLoading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

// Decode JWT payload (base64url) — no verification, just to extract claims after refresh
function parseJwtPayload(token: string): Record<string, string> | null {
  try {
    const base64 = token.split('.')[1]
    const json = atob(base64.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(json) as Record<string, string>
  } catch {
    return null
  }
}
