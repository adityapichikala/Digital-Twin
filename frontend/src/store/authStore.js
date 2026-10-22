import { create } from 'zustand';
import api from '../api/client';

function parseToken(token) {
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length === 3) {
      const payload = JSON.parse(atob(parts[1]));
      return {
        username: payload.sub || payload.username || '',
        role: payload.role || (Array.isArray(payload.roles) ? payload.roles[0] : 'VIEWER'),
      };
    }
  } catch {
    // ignore malformed token
  }
  return null;
}

const initialToken = localStorage.getItem('td_token') || null;
const initialUser = (() => {
  try {
    const saved = localStorage.getItem('td_user');
    if (saved) return JSON.parse(saved);
  } catch {
    // ignore parse error
  }
  return parseToken(initialToken);
})();

const useAuthStore = create((set) => ({
  token: initialToken,
  user: initialUser,

  login: async (username, password) => {
    const { data } = await api.post('/api/v1/auth/login', { username, password });
    localStorage.setItem('td_token', data.accessToken);
    const userData = { username: data.username, role: data.role };
    localStorage.setItem('td_user', JSON.stringify(userData));
    set({ token: data.accessToken, user: userData });
    return data;
  },

  logout: async () => {
    try {
      await api.post('/api/v1/auth/logout');
    } catch {
      /* ignore network errors on logout */
    }
    localStorage.removeItem('td_token');
    localStorage.removeItem('td_user');
    set({ token: null, user: null });
  },

  isAuthenticated: () => !!localStorage.getItem('td_token'),
}));

export default useAuthStore;

