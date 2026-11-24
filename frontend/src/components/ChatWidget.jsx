import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { genreStyle } from '../genres.js';

const GREETING = {
  role: 'assistant',
  content: 'Hi, I\'m BookBot. Tell me what you feel like reading, or name a book you loved.',
   books: [],
};
const SUGGESTIONS = ['Something like Dune', 'A fantasy with dragons', 'Best history books', 'Books to build better habits'];

// The AI chatbot. It sends the typed message (plus recent history) to POST /api/chat
// and shows the text reply together with the matching books as little cards.
export default function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([GREETING]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [engine, setEngine] = useState(null);    // { mode: 'llm' | 'local', model: 'Gemini (...)' }
  const [lastMode, setLastMode] = useState(null);    // what actually answered the last message
  const endRef = useRef(null);

  useEffect(() => {
    api.chatStatus().then(setEngine).catch(() => {});
  }, []);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [messages, open, sending]);

  async function send(text) {
    const message = text.trim();
    if (!message || sending) return;
    const history = messages.slice(1).map(({ role, content }) => ({ role, content }));    // skip the greeting
    setMessages((m) => [...m, { role: 'user', content: message }]);
    setInput('');
    setSending(true);
    try {
      const reply = await api.chat(message, history);
      setLastMode(reply.mode);
      if (reply.mode === 'local') api.chatStatus().then(setEngine).catch(() => {});   // fetch the reason it failed
      setMessages((m) => [...m, { role: 'assistant', content: reply.reply, books: reply.books }]);
    } catch (err) {
      setMessages((m) => [...m, { role: 'assistant', content: err.message, books: [], error: true }]);
    } finally {
      setSending(false);
    }
  }

  if (!open) {
    return (
      <button className="chat-fab" onClick={() => setOpen(true)}>
        <span aria-hidden="true">✦</span> Ask BookBot
      </button>
      );
  }

  return (
    <section className="chat" aria-label="BookBot chat">
      <header className="chat-head">
        <div>
           <strong>BookBot</strong>
           <span className="chat-mode">
              {engine?.mode !== 'llm'
                ? 'Offline engine (no AI model connected)'
                : lastMode === 'local'
                  ? `${engine.model} is not answering, using the offline engine`
                  : `Powered by ${engine.model}`}
           </span>
           {engine?.mode === 'llm' && lastMode === 'local' && engine.error && (
              <span className="chat-error" title={engine.error}>Reason: {engine.error.slice(0, 160)}</span>
           )}
        </div>
        <button className="icon-btn" onClick={() => setOpen(false)} aria-label="Close chat">✕</button>
      </header>

      <div className="chat-log" aria-live="polite">
        {messages.map((m, i) => (
          <div key={i} className={`msg msg-${m.role} ${m.error ? 'msg- error' : ''}`}>
             <p>{m.content}</p>
             {m.books?.length > 0 && (
                <ul className="chat-books">
                  {m.books.map((b) => (
                    <li key={b.id}>
                      <Link to={`/books/${b.id}`} onClick={() => setOpen(false)}>
                        <span className="chip-cover" style={{ background: genreStyle(b.genre).color }} />
                        <span>
                          <strong>{b.title}</strong>
                          <small>{b.author}</small>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
             )}
          </div>
        ))}
        {sending && <div className="msg msg-assistant"><p className="typing">Looking through the shelves…</p></div>}
        <div ref={endRef} />
      </div>
       {messages.length === 1 && (
          <div className="chat-suggest">
            {SUGGESTIONS.map((s) => (
              <button key={s} onClick={() => send(s)}>{s}</button>
            ))}
          </div>
       )}

       <form className="chat-form" onSubmit={(e) => { e.preventDefault(); send(input); }}>
         <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask for a book…"
            maxLength={500}
            aria-label="Message to BookBot"
         />
         <button className="btn btn-solid" disabled={sending || ! input.trim()}>Send</button>
       </form>
     </section>
  );
}
