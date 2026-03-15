import { Outlet } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthContext'

// RootLayout wraps the entire app in AuthProvider, which needs to be inside
// RouterProvider so that useNavigate() is available.
export function RootLayout() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}
