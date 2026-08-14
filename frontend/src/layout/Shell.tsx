import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Shell() {
  const { signedIn, logout } = useAuth()

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
