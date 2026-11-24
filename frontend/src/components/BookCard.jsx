import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { useToast } from '../Toast.jsx';
import BookCover from './BookCover.jsx';
import { Stars } from './Stars.jsx';

// One book in a grid. `note` is the optional explanation ("Because you liked ...").
// `noteSource` is "ai" when the sentence was written by the LLM, otherwise it was built from shared words.
export default function BookCard({ book, onUpdate, note, noteSource }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  async function toggleFavorite() {
    if (!user) {
      toast('Log in to save books to your list');
      navigate('/login', { state: { from: location.pathname } });
      return;
    }
    try {
      const updated = book.favorite ? await api.removeFavorite(book.id) : await api.addFavorite(book.id);
      onUpdate?.(updated);
      toast(updated.favorite ? 'Saved to your list' : 'Removed from your list');
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  return (
     <article className="card">
       <div className="card-cover">
          <Link to={`/books/${book.id}`} tabIndex={-1}>
            <BookCover book={book} />
          </Link>
       </div>
       <h3 className="card-title">
          <Link to={`/books/${book.id}`}>{book.title}</Link>
       </h3>
       <p className="card-author">{book.author}</p>
       <div className="card-meta">
          <Stars value={book.avgRating} count={book.ratingCount} />
          <button
            type="button"
            className={`heart ${book.favorite ? 'on' : ''}`}
            onClick={toggleFavorite}
            aria-pressed={book.favorite}
            aria-label={book.favorite ? `Remove ${book.title} from your list` : `Save ${book.title} to your list`}
          >
            {book.favorite ? '♥' : '♡'}
          </button>
       </div>
       {note && (
          <p className="card-note">
            {noteSource === 'ai' && <span className="ai-mark" title="Written by the AI model">✦ </span>}
            {note}
          </p>
       )}
     </article>
  );
}
