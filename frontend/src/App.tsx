import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { RequireAdmin } from './auth/RequireAdmin'
import { RequireAuth } from './auth/RequireAuth'
import { Shell } from './layout/Shell'
import { AdminCreateEventPage } from './pages/AdminCreateEventPage'
import { AdminDashboardPage } from './pages/AdminDashboardPage'
import { AdminInsightsPage } from './pages/AdminInsightsPage'
import { EventDetailPage } from './pages/EventDetailPage'
import { EventsPage } from './pages/EventsPage'
import { LoginPage } from './pages/LoginPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { OrderPage } from './pages/OrderPage'
import { OrdersPage } from './pages/OrdersPage'
import { RegisterPage } from './pages/RegisterPage'
import { WaitingRoomPage } from './pages/WaitingRoomPage'
import { WaitlistPage } from './pages/WaitlistPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Shell />}>
              <Route path="/" element={<EventsPage />} />
              <Route path="/events/:eventId" element={<EventDetailPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route
                path="/events/:eventId/queue"
                element={
                  <RequireAuth>
                    <WaitingRoomPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/orders"
                element={
                  <RequireAuth>
                    <OrdersPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/orders/:orderNo"
                element={
                  <RequireAuth>
                    <OrderPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/waitlist"
                element={
                  <RequireAuth>
                    <WaitlistPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/notifications"
                element={
                  <RequireAuth>
                    <NotificationsPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/admin"
                element={
                  <RequireAdmin>
                    <AdminDashboardPage />
                  </RequireAdmin>
                }
              />
              <Route
                path="/admin/events/new"
                element={
                  <RequireAdmin>
                    <AdminCreateEventPage />
                  </RequireAdmin>
                }
              />
              <Route
                path="/admin/insights"
                element={
                  <RequireAdmin>
                    <AdminInsightsPage />
                  </RequireAdmin>
                }
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}
