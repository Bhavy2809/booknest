import { createContext, useCallback, useContext, useRef, useState } from 'react';

// A tiny notification system: any component can call toast('Saved') or toast('Failed', 'error').
const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [message, setMessage] = useState(null);
  const timer = useRef(null);

  const toast = useCallback((text, kind = 'info') => {
    setMessage({ text, kind });
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setMessage(null), 3200);
  }, []);

  return (
     <ToastContext.Provider value={toast}>
       {children}
       <div className="toast-region" role="status" aria-live="polite">
         {message && <div className={`toast toast-${message.kind}`}>{message.text}</div>}
       </div>
     </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
