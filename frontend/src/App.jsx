import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';
import Navbar from './components/Navbar.jsx';
import ChatWidget from './components/ChatWidget.jsx';
import Catalog from './pages/Catalog.jsx';
import BookDetail from './pages/BookDetail.jsx';
import ForYou from './pages/ForYou.jsx';
import Favorites from './pages/Favorites.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import BookForm from './pages/BookForm.jsx';

// Wrapper for pages that need a logged-in user (and optionally an admin).
function Protected({ admin = false, children }) {
  const { user, ready, isAdmin } = useAuth();
  const location = useLocation();
  if (!ready) return null;
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  if (admin && !isAdmin) {
    return (
       <div className="page narrow">
         <div className="empty"><h3>Admins only</h3><p>Your account cannot manage books. <Link to="/">Back to the catalog</Link></p></div>
       </div>
    );
  }
  return children;
}

export default function App() {
  return (
    <>
       <Navbar />
       <main>
         <Routes>
           <Route path="/" element={<Catalog />} />
           <Route path="/books/:id" element={<BookDetail />} />
           <Route path="/for-you" element={<Protected><ForYou /></Protected>} />
           <Route path="/favorites" element={<Protected><Favorites /></Protected>} />
           <Route path="/login" element={<Login />} />
           <Route path="/register" element={<Register />} />
           <Route path="/admin/books/new" element={<Protected admin><BookForm /></Protected>} />
           <Route path="/admin/books/:id/edit" element={<Protected admin><BookForm /></Protected>} />
           <Route path="*" element={<div className="page narrow"><div className="empty"><h3>Page not found</h3><p><Link to="/">Go to the catalog</Link></p></div></div>} />
         </Routes>
       </main>
       <ChatWidget />
     </>
  );
}
