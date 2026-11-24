// All communication with the Spring Boot REST API lives in this one file.
// Every component calls these functions instead of using fetch() directly.

const BASE = '/api';
const TOKEN_KEY = 'booknest_token';

export const getToken = () => {
   try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
};
export const setToken = (token) => {
   try { token ? localStorage.setItem(TOKEN_KEY, token) : localStorage.removeItem(TOKEN_KEY); } catch { /* storage blocked */ }
};

async function request(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;   // proves who we are to the server

  let response;
  try {
    response = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  } catch {
    throw new Error('Cannot reach the server. Is the Spring Boot backend running on port 8080?');
  }
  if (response.status === 204) return null;

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(data?.error || `Something went wrong (HTTP ${response.status})`);
    error.status = response.status;
    throw error;
  }
  return data;
}

const query = (params) => {
   const qs = new URLSearchParams();
   Object.entries(params).forEach(([key, value]) => value && qs.set(key, value));
   const text = qs.toString();
   return text ? `?${text}` : '';
};

export const api = {
  // auth
  register: (name, email, password) => request('/auth/register', { method: 'POST', body: { name, email, password } }),
  login: (email, password) => request('/auth/login', { method: 'POST', body: { email, password } }),
  me: () => request('/auth/me'),

  // catalog
  books: (params = {}) => request('/books' + query(params)),
  book: (id) => request(`/books/${id}`),
  genres: () => request('/genres'),
  createBook: (book) => request('/books', { method: 'POST', body: book }),
  updateBook: (id, book) => request(`/books/${id}`, { method: 'PUT', body: book }),
  deleteBook: (id) => request(`/books/${id}`, { method: 'DELETE' }),
  // reader actions
  rate: (id, stars) => request(`/books/${id}/rating`, { method: 'PUT', body: { stars } }),
  favorites: () => request('/favorites'),
  addFavorite: (id) => request(`/favorites/${id}`, { method: 'PUT' }),
  removeFavorite: (id) => request(`/favorites/${id}`, { method: 'DELETE' }),

   // AI features
   similar: (id, limit = 4) => request(`/books/${id}/similar?limit=${limit}`),
   recommendations: (limit = 8) => request(`/recommendations?limit=${limit}`),
   smartSearch: (q) => request(`/ai/search?q=${encodeURIComponent(q)}`),
   chat: (message, history) => request('/chat', { method: 'POST', body: { message, history } }),
   chatStatus: () => request('/chat/status'),
};
