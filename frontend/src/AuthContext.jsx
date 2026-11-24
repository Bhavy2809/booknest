import { createContext, useContext, useEffect, useState } from 'react';
import { api, getToken, setToken } from './api.js';

// React Context makes "who is logged in?" available to every component without passing props down.
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);   // false until we have checked any saved token

  // On first load: if a token is saved from last time, ask the server whether it is still valid.
  useEffect(() => {
    if (!getToken()) {
      setReady(true);
      return;
    }
    api.me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setReady(true));
  }, []);

  const accept = (response) => {
     setToken(response.token);
     setUser(response.user);
     return response.user;
  };

  const value = {
    user,
    ready,
    isAdmin: user?.role === 'ADMIN',
    login: async (email, password) => accept(await api.login(email, password)),
     register: async (name, email, password) => accept(await api.register(name, email, password)),
     logout: () => {
        setToken(null);
        setUser(null);
     },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
