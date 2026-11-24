// Every genre has one fixed colour and a three-letter shelf code, like the labels on library spines.
// The colour is how you recognise a genre anywhere in the app: sidebar, covers, chat results.

const GENRES = {
   'Fantasy': { color: '#5b3f8c', code: 'FAN' },
   'Science Fiction': { color: '#14597a', code: 'SCI' },
   'Mystery & Thriller': { color: '#2f3640', code: 'MYS' },
   'Romance': { color: '#a8324a', code: 'ROM' },
   'Classics': { color: '#7a4a21', code: 'CLA' },
   'Self-Help': { color: '#b8700f', code: 'SLF' },
   'Technology': { color: '#0f6b5c', code: 'TEC' },
   'Biography': { color: '#3d6b2e', code: 'BIO' },
   'History': { color: '#6b5a2b', code: 'HIS' },
   'Philosophy': { color: '#4a5a78', code: 'PHI' },
};

// Genres an admin invents later still get a stable colour (derived from the name).
export function genreStyle(name = '') {
  if (GENRES[name]) return GENRES[name];
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  return {
     color: `hsl(${Math.abs(hash) % 360} 42% 30%)`,
     code: name.replace(/[^a-z]/gi, '').slice(0, 3).toUpperCase() || 'GEN',
  };
}

// Call number printed on the cover label, e.g. "FAN 001 TOL" (shelf code, book number, author's first letters)
export function callNumber(book) {
  const { code } = genreStyle(book.genre);
  const lastName = (book.author || '').split(/\s+/).pop() || '';
  return { shelf: code, line: `${String(book.id).padStart(3, '0')} ${lastName.slice(0, 3).toUpperCase()}` };
}
