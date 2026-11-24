import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { genreStyle } from '../genres.js';
import BookCard from '../components/BookCard.jsx';

// Home page: shelves (genres) on the left, search + grid on the right.
export default function Catalog() {
  const { user } = useAuth();
  const [q, setQ] = useState('');
  const [smart, setSmart] = useState(false);
  const [genre, setGenre] = useState('');
  const [sort, setSort] = useState('title');
  const [books, setBooks] = useState([]);
  const [genres, setGenres] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.genres().then(setGenres).catch(() => {});
  }, []);

  // Re-fetch whenever a filter changes. The 250 ms delay waits for the user to stop typing.
  useEffect(() => {
    let stale = false;
    setLoading(true);
    const timer = setTimeout(async () => {
      try {
        const text = q.trim();
        let data;
        if (smart && text) {
          data = await api.smartSearch(text);                      // AI: ranked by meaning (TF-IDF)
          if (genre) data = data.filter((b) => b.genre === genre);
        } else {
          data = await api.books({ q: text, genre, sort });         // plain keyword search
        }
        if (!stale) { setBooks(data); setError(''); }
      } catch (err) {
        if (!stale) setError(err.message);
      } finally {
        if (!stale) setLoading(false);
      }
    }, 250);
    return () => { stale = true; clearTimeout(timer); };
  }, [q, smart, genre, sort, user?.id]);

  const replaceBook = (updated) => setBooks((list) => list.map((b) => (b.id === updated.id ? updated : b)));
  const smartActive = smart && q.trim();

  return (
    <div className="page catalog">
      <aside className="shelves" aria-label="Genres">
        <h2>Shelves</h2>
        <ul>
           <li>
             <button className={!genre ? 'active' : ''} onClick={() => setGenre('')}>
                <i className="dot dot-all" /> All books
             </button>
           </li>
           {genres.map((g) => (
             <li key={g}>
                <button className={genre === g ? 'active' : ''} onClick={() => setGenre(g)}>
                  <i className="dot" style={{ background: genreStyle(g).color }} /> {g}
                </button>
             </li>
           ))}
        </ul>
      </aside>

      <section>
        <h1 className="page-title">Find your next read</h1>

        <div className="search-row">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={smart ? 'Describe it: "a story about dragons and magic"' : 'Search title, author or topic'}
             aria-label="Search books"
          />
          <label className="switch" title="Ranks books by how well they match your words, using the TF-IDF engine on the server">
             <input type="checkbox" checked={smart} onChange={(e) => setSmart(e.target.checked)} />
             <span className="switch-track" aria-hidden="true" />
             Smart search
          </label>
        </div>

        <div className="toolbar">
          <span className="muted" aria-live="polite">
             {loading ? 'Loading…' : `${books.length} book${books.length === 1 ? '' : 's'}`}
             {smartActive && !loading ? ', best matches first' : ''}
          </span>
          {!smartActive && (
             <label className="sort">
               Sort by
               <select value={sort} onChange={(e) => setSort(e.target.value)}>
                 <option value="title">Title</option>
                 <option value="rating">Top rated</option>
                 <option value="newest">Newest</option>
               </select>
             </label>
          )}
        </div>

        {error && <p className="notice notice-error">{error}</p>}
        {!error && !loading && books.length === 0 && (
           <div className="empty">
             <h3>No books match that</h3>
             <p>Try fewer words, pick another shelf, or ask BookBot in the corner.</p>
           </div>
        )}

          <div className="grid">
             {books.map((b) => (
               <BookCard key={b.id} book={b} onUpdate={replaceBook} />
             ))}
          </div>
        </section>
      </div>
    );
}
