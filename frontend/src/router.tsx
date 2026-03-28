import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RootLayout } from './RootLayout'
import { AppShell } from './shared/layout/AppShell'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { LoginPage } from './features/auth/LoginPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { AnalytesPage } from './features/catalog/analytes/AnalytesPage'
import { TechniquesPage } from './features/catalog/techniques/TechniquesPage'
import { SpecimenTypesPage } from './features/catalog/specimen-types/SpecimenTypesPage'
import { CollectionContainersPage } from './features/catalog/collection-containers/CollectionContainersPage'

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <Navigate to="/dashboard" replace /> },
              { path: '/dashboard', element: <DashboardPage /> },
              { path: '/catalog/analytes', element: <AnalytesPage /> },
              { path: '/catalog/techniques', element: <TechniquesPage /> },
              { path: '/catalog/specimen-types', element: <SpecimenTypesPage /> },
              { path: '/catalog/collection-containers', element: <CollectionContainersPage /> },
            ],
          },
        ],
      },
      {
        path: '*',
        element: <Navigate to="/dashboard" replace />,
      },
    ],
  },
])
