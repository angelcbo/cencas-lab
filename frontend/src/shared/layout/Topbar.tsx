import { LogOut, User } from 'lucide-react'
import { useAuth } from '@/features/auth/useAuth'

export function Topbar() {
  const { user, logout } = useAuth()

  return (
    <header className="h-14 border-b border-border bg-card flex items-center justify-end px-4 gap-3 shrink-0">
      {user && (
        <div className="flex items-center gap-2 text-sm">
          <User className="h-4 w-4 text-muted-foreground" />
          <span className="text-foreground font-medium">
            {user.firstName} {user.lastName}
          </span>
          <span className="text-xs text-muted-foreground bg-muted px-1.5 py-0.5 rounded">
            {user.role}
          </span>
        </div>
      )}
      <button
        onClick={() => void logout()}
        className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
        type="button"
      >
        <LogOut className="h-4 w-4" />
        Salir
      </button>
    </header>
  )
}
