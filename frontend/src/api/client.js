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

// Handle 401 globally
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('td_token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export default api;
