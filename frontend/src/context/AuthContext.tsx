import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import api from '../api';
import { User } from '../types';

interface AuthContextType {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<any>;
  register: (email: string, password: string, displayName: string) => Promise<any>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  token: null,
  loading: true,
  login: async () => {},
  register: async () => {},
  logout: () => {},
  refreshUser: async () => {},
});

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('preptrack_token'));
  const [loading, setLoading] = useState<boolean>(true);

  const refreshUser = async () => {
    const savedToken = localStorage.getItem('preptrack_token');
    if (!savedToken) {
      setUser(null);
      setLoading(false);
      return;
    }

    try {
      const res = await api.get<User>('/auth/me');
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

  const login = async (email: string, password: string) => {
    const res = await api.post('/auth/login', { email, password });
    const { token: newToken } = res.data;
    localStorage.setItem('preptrack_token', newToken);
    setToken(newToken);
    return res.data;
  };

  const register = async (email: string, password: string, displayName: string) => {
    const res = await api.post('/auth/register', { email, password, displayName });
    const { token: newToken } = res.data;
    localStorage.setItem('preptrack_token', newToken);
    setToken(newToken);
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
