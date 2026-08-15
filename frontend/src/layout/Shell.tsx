import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Shell() {
  const { signedIn, isAdmin, logout } = useAuth()

  return (
    <div className="app">
      <header className="masthead">
        <Link to="/" className="wordmark">
          Fair Ticketing
        </Link>
        <nav>
          <NavLink to="/">Shows</NavLink>
          {signedIn ? (
            <>
              <NavLink to="/orders">Orders</NavLink>
              <NavLink to="/waitlist">Waitlist</NavLink>
              <NavLink to="/notifications">Inbox</NavLink>
              {isAdmin ? <NavLink to="/admin">Dashboard</NavLink> : null}
              {isAdmin ? <NavLink to="/admin/events/new">List a show</NavLink> : null}
              {isAdmin ? <NavLink to="/admin/insights">Insights</NavLink> : null}
              <button type="button" className="linkish" onClick={logout}>
                Sign out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Sign in</NavLink>
              <NavLink to="/register" className="cta">
                Create account
              </NavLink>
            </>
          )}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
