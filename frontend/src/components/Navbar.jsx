import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext.jsx';

export default function Navbar() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <header className="topbar">
      <div className="topbar-inner">
        <Link to="/" className="brand" aria-label="BookNest home">
           <svg viewBox="0 0 32 32" width="26" height="26" aria-hidden="true">
             <rect x="3" y="6" width="7" height="22" rx="1.5" fill="#5b3f8c" />
             <rect x="12.5" y="3" width="7" height="25" rx="1.5" fill="#14597a" />
             <rect x="22" y="8" width="7" height="20" rx="1.5" fill="#a8324a" />
           </svg>
           BookNest
        </Link>

         <nav className="nav" aria-label="Main">
           <NavLink to="/" end>Browse</NavLink>
           <NavLink to="/for-you">For you</NavLink>
           <NavLink to="/favorites">My list</NavLink>
           {isAdmin && <NavLink to="/admin/books/new">Add book</NavLink>}
         </nav>

         <div className="account">
           {user ? (
              <>
                 <span className="who">{user.name}{isAdmin && <em className="badge">admin</em>}</span>
                 <button className="btn btn-quiet" onClick={() => { logout(); navigate('/'); }}>Log out</button>
              </>
           ) : (
              <>
                 <Link className="btn btn-quiet" to="/login">Log in</Link>
                 <Link className="btn btn-solid" to="/register">Sign up</Link>
              </>
           )}
         </div>
       </div>
     </header>
  );
}
