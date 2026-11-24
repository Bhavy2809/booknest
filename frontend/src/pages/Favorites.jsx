import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import BookCard from '../components/BookCard.jsx';

export default function Favorites() {
  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.favorites()
      .then(setBooks)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  // Un-hearting a book removes it from this page straight away.
  const update = (updated) =>
    setBooks((list) => (updated.favorite ? list.map((b) => (b.id === updated.id ? updated : b)) : list.filter((b) => b.id !== updated.id)));

  return (
    <div className="page">
      <h1 className="page-title">My list</h1>
       {error && <p className="notice notice-error">{error}</p>}
       {loading && <p className="muted">Loading…</p>}
       {!loading && !error && books.length === 0 && (
          <div className="empty">
            <h3>Your list is empty</h3>
            <p>Tap the heart on any book to keep it here. <Link to="/">Browse books</Link></p>
          </div>
       )}
       <div className="grid">
          {books.map((b) => <BookCard key={b.id} book={b} onUpdate={update} />)}
       </div>
     </div>
  );
}
