import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import BookCard from '../components/BookCard.jsx';

// Personalised recommendations: GET /api/recommendations builds a taste profile from the
// logged-in user's favourites and ratings and returns the closest books with a reason for each.
export default function ForYou() {
  const { user } = useAuth();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    api.recommendations(8)
      .then((data) => { setItems(data); setError(''); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [user?.id]);

  const replace = (updated) => setItems((list) => list.map((r) => (r.book.id === updated.id ? { ...r, book: updated } : r)));

  return (
    <div className="page">
      <h1 className="page-title">Picked for you</h1>
      <p className="lede">
        BookNest compares the books you saved or rated highly with every other book and picks the closest matches.
        When the AI model is connected, it also writes the reason under each book (marked ✦).
        Rate a few more books and this list changes.
      </p>

      {error && <p className="notice notice-error">{error}</p>}
      {loading && <p className="muted">Working out your taste… (the first load can take a few seconds)</p>}
      {!loading && !error && items.length === 0 && (
         <div className="empty">
           <h3>Nothing to suggest yet</h3>
           <p>Save or rate a few books first. <Link to="/">Browse the catalog</Link></p>
         </div>
      )}

       <div className="grid">
         {items.map((r) => (
            <BookCard key={r.book.id} book={r.book} note={r.reason} noteSource={r.source} onUpdate={replace} />
         ))}
       </div>
     </div>
  );
}
