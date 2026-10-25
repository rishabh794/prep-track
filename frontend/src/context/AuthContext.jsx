import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(() => localStorage.getItem('preptrack_token'));
  const [loading, setLoading] = useState(true);

  const refreshUser = async () => {
    const savedToken = localStorage.getItem('preptrack_token');
    if (!savedToken) {
      setUser(null);
      setLoading(false);
      return;
    }

    try {
      const res = await api.get('/auth/me');
      setUser(res.data);
    } catch (err) {
      console.error('Failed to fetch user profile', err);
      localStorage.removeItem('preptrack_token');
      setToken(null);
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshUser();
  }, [token]);

  const login = async (email, password) => {
    const res = await api.post('/auth/login', { email, password });
    const { token: newToken } = res.data;
    localStorage.setItem('preptrack_token', newToken);
    setToken(newToken);
    await refreshUser();
    return res.data;
  };

  const register = async (email, password, displayName) => {
    const res = await api.post('/auth/register', { email, password, displayName });
    const { token: newToken } = res.data;
    localStorage.setItem('preptrack_token', newToken);
    setToken(newToken);
    await refreshUser();
    return res.data;
  };

  const logout = () => {
    localStorage.removeItem('preptrack_token');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
