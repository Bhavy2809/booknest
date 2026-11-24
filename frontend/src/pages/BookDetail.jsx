import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { useToast } from '../Toast.jsx';
import { genreStyle } from '../genres.js';
import BookCard from '../components/BookCard.jsx';
import BookCover from '../components/BookCover.jsx';
import { RatingInput, Stars } from '../components/Stars.jsx';

export default function BookDetail() {
  const { id } = useParams();
  const { user, isAdmin } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [book, setBook] = useState(null);
  const [similar, setSimilar] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    setBook(null);
    setError('');
    api.book(id).then(setBook).catch((e) => setError(e.message));
    api.similar(id, 4).then(setSimilar).catch(() => setSimilar([]));
    window.scrollTo(0, 0);
  }, [id, user?.id]);

  if (error) {
    return (
       <div className="page narrow">
         <div className="empty"><h3>{error}</h3><p><Link to="/">Back to the catalog</Link></p></div>
       </div>
    );
  }
  if (!book) return <div className="page narrow"><p className="muted">Loading…</p></div>;

  async function rate(stars) {
    try {
      setBook(await api.rate(book.id, stars));
      toast(`Rated ${stars} star${stars > 1 ? 's' : ''}`);
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  async function toggleFavorite() {
    try {
      const updated = book.favorite ? await api.removeFavorite(book.id) : await api.addFavorite(book.id);
      setBook(updated);
      toast(updated.favorite ? 'Saved to your list' : 'Removed from your list');
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  async function remove() {
    if (!window.confirm(`Delete "${book.title}" for everyone? This cannot be undone.`)) return;
    try {
      await api.deleteBook(book.id);
      toast('Book deleted');
      navigate('/');
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  return (
    <div className="page narrow">
      <Link to="/" className="back">← All books</Link>

      <div className="detail">
         <div className="detail-cover"><BookCover book={book} size="large" /></div>

        <div className="detail-main">
          <p className="genre-tag">
            <i className="dot" style={{ background: genreStyle(book.genre).color }} /> {book.genre}
          </p>
          <h1>{book.title}</h1>
          <p className="byline">{book.author}{book.year ? `, ${book.year < 0 ? `${-book.year} BCE` : book.year}` : ''}</p>
          <Stars value={book.avgRating} count={book.ratingCount} size="lg" />

          <p className="description">{book.description || 'No description yet.'}</p>

          <div className="actions">
            {user ? (
               <button className={`btn ${book.favorite ? 'btn-outline' : 'btn-solid'}`} onClick={toggleFavorite}>
                  {book.favorite ? '♥ In your list' : '♡ Save to my list'}
               </button>
            ) : (
               <Link className="btn btn-solid" to="/login" state={{ from: `/books/${book.id}` }}>Log in to save and rate</Link>
            )}
            {isAdmin && (
               <>
                  <Link className="btn btn-outline" to={`/admin/books/${book.id}/edit`}>Edit</Link>
                  <button className="btn btn-danger" onClick={remove}>Delete</button>
               </>
            )}
          </div>

          {user && (
            <div className="your-rating">
              <span>Your rating</span>
                 <RatingInput value={book.myRating} onChange={rate} />
               </div>
           )}
         </div>
       </div>

       {similar.length > 0 && (
          <section className="related">
            <h2>Readers who like this also like</h2>
            <p className="muted">Picked by comparing the words used to describe each book. The line under each says why it fits (✦ means the AI model wrote it).</p>
            <div className="grid">
              {similar.map((r) => (
                <BookCard key={r.book.id} book={r.book} note={r.reason} noteSource={r.source} />
              ))}
            </div>
          </section>
       )}
     </div>
  );
}
