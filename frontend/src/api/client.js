import axios from 'axios';

const api = axios.create({
  baseURL: '', // relative — Vite proxy routes /api → gateway:3000
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('td_token');
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 globally, but do not redirect on auth endpoint failures (e.g. invalid credentials)
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isAuthCall = err.config?.url && err.config.url.includes('/auth');
    if (err.response?.status === 401 && !isAuthCall) {
      localStorage.removeItem('td_token');
      localStorage.removeItem('td_user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export default api;
