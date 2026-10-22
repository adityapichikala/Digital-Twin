import { Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import TopologyPage from './pages/TopologyPage';
import SimulationPage from './pages/SimulationPage';
import CostRiskPage from './pages/CostRiskPage';
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

function AdminOnlyRoute({ children }) {
  const user = useAuthStore((s) => s.user);
  const isViewer = user?.role?.toUpperCase() === 'VIEWER';
  if (isViewer) {
    return <Navigate to="/" replace />;
  }
  return children;
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
        <PrivateLayout><AdminOnlyRoute><SimulationPage /></AdminOnlyRoute></PrivateLayout>
      } />
      <Route path="/cost-risk" element={
        <PrivateLayout><CostRiskPage /></PrivateLayout>
      } />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
