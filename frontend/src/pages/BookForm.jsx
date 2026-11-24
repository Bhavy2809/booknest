import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useToast } from '../Toast.jsx';

const EMPTY = { title: '', author: '', genre: '', year: '', description: '' };

// Admin only: the same form adds a new book (/admin/books/new) or edits one (/admin/books/:id/edit).
export default function BookForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState(EMPTY);
  const [genres, setGenres] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.genres().then(setGenres).catch(() => {});
    if (editing) {
      api.book(id)
        .then((b) => setForm({ title: b.title, author: b.author, genre: b.genre, year: b.year ?? '', description: b.description ?? '' }))
        .catch((e) => setError(e.message));
    } else {
      setForm(EMPTY);
    }
  }, [id, editing]);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    const payload = { ...form, year: form.year === '' ? null : Number(form.year) };
    try {
      const saved = editing ? await api.updateBook(id, payload) : await api.createBook(payload);
      toast(editing ? 'Changes saved' : 'Book added');
      navigate(`/books/${saved.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page form-page">
      <h1 className="page-title">{editing ? 'Edit book' : 'Add a book'}</h1>
      <form onSubmit={submit} className="form">
        <label>Title
           <input value={form.title} onChange={set('title')} required maxLength={200} />
        </label>
        <label>Author
           <input value={form.author} onChange={set('author')} required maxLength={200} />
        </label>
        <div className="row">
           <label>Genre
             <input value={form.genre} onChange={set('genre')} list="genre-list" required maxLength={60} />
             <datalist id="genre-list">{genres.map((g) => <option key={g} value={g} />)}</datalist>
           </label>
           <label>Year
             <input type="number" value={form.year} onChange={set('year')} />
           </label>
        </div>
        <label>Description
           <textarea rows={6} value={form.description} onChange={set('description')} maxLength={2000} />
           <small className="muted">
             Write what the book is about. Recommendations and smart search are built from these words.
            </small>
         </label>
         {error && <p className="notice notice-error">{error}</p>}
         <div className="actions">
            <button className="btn btn-solid" disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Add book'}</button>
            <Link className="btn btn-quiet" to={editing ? `/books/${id}` : '/'}>Cancel</Link>
         </div>
       </form>
     </div>
  );
}
