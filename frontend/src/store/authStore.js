import { create } from 'zustand';
import api from '../api/client';

const useAuthStore = create((set) => ({
  token: localStorage.getItem('td_token') || null,
  user: null,

  login: async (username, password) => {
    const { data } = await api.post('/api/v1/auth/login', { username, password });
    localStorage.setItem('td_token', data.accessToken);
    set({ token: data.accessToken, user: { username: data.username, role: data.role } });
    return data;
  },

  logout: async () => {
    try {
      await api.post('/api/v1/auth/logout');
    } catch (_) { /* ignore network errors on logout */ }
    localStorage.removeItem('td_token');
    set({ token: null, user: null });
  },

  isAuthenticated: () => !!localStorage.getItem('td_token'),
}));

export default useAuthStore;
