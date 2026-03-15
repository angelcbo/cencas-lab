import { NavLink } from 'react-router-dom'
import { LayoutDashboard, FlaskConical } from 'lucide-react'
import { cn } from '@/lib/utils'

const navItems = [
  {
    label: 'Dashboard',
    to: '/dashboard',
    icon: LayoutDashboard,
  },
  {
    group: 'Catálogo',
    items: [
      { label: 'Analitos', to: '/catalog/analytes', icon: FlaskConical },
    ],
  },
]

export function Sidebar() {
  return (
    <aside className="w-56 border-r border-border bg-card flex flex-col shrink-0">
      <div className="h-14 flex items-center px-4 border-b border-border">
        <span className="font-semibold text-foreground tracking-tight">Cenicast LIS</span>
      </div>
      <nav className="flex-1 overflow-y-auto px-2 py-3 space-y-1">
        {navItems.map((item) => {
          if ('group' in item) {
            return (
              <div key={item.group} className="pt-3">
                <p className="px-2 mb-1 text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  {item.group}
                </p>
                {item.items.map((sub) => (
                  <SidebarLink key={sub.to} to={sub.to} icon={sub.icon} label={sub.label} />
                ))}
              </div>
            )
          }
          return (
            <SidebarLink key={item.to} to={item.to} icon={item.icon} label={item.label} />
          )
        })}
      </nav>
    </aside>
  )
}

function SidebarLink({
  to,
  icon: Icon,
  label,
}: {
  to: string
  icon: React.ComponentType<{ className?: string }>
  label: string
}) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-2.5 px-2 py-1.5 rounded-md text-sm transition-colors',
          isActive
            ? 'bg-primary text-primary-foreground'
            : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
        )
      }
    >
      <Icon className="h-4 w-4 shrink-0" />
      {label}
    </NavLink>
  )
}
