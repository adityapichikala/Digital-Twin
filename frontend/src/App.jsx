import { Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import TopologyPage from './pages/TopologyPage';
import SimulationPage from './pages/SimulationPage';
import useAuthStore from './store/authStore';

function PrivateLayout({ children }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content">{children}</div>
    </div>
  );
}

function PlaceholderPage({ title }) {
  return (
    <div className="page-content">
      <h2 style={{ fontSize: '1.15rem', fontWeight: 600, marginBottom: '0.5rem' }}>{title}</h2>
      <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.85rem' }}>
        This page will be implemented in the next phase.
      </p>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={
        <PrivateLayout><DashboardPage /></PrivateLayout>
      } />
      <Route path="/topology" element={
        <PrivateLayout><TopologyPage /></PrivateLayout>
      } />
      <Route path="/simulate" element={
        <PrivateLayout><SimulationPage /></PrivateLayout>
      } />
      <Route path="/cost-risk" element={
        <PrivateLayout><PlaceholderPage title="Cost & Risk Assessment" /></PrivateLayout>
      } />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
