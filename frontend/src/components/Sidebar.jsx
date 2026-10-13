import { NavLink, useNavigate } from 'react-router-dom';
import { LayoutDashboard, Network, FlaskConical, DollarSign, LogOut } from 'lucide-react';
import useAuthStore from '../store/authStore';

const NAV = [
  { to: '/',           label: 'Dashboard',  icon: LayoutDashboard },
  { to: '/topology',   label: 'Topology',   icon: Network },
  { to: '/simulate',   label: 'Simulate',   icon: FlaskConical },
  { to: '/cost-risk',  label: 'Cost & Risk',icon: DollarSign },
];

export default function Sidebar() {
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <nav className="sidebar">
      <div className="sidebar-logo">
        <h1>TwinDigital</h1>
        <p>Kubernetes Digital Twin</p>
      </div>

      {NAV.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
        >
          <Icon size={16} />
          {label}
        </NavLink>
      ))}

      <div style={{ marginTop: 'auto' }}>
        <button id="logout-btn" className="nav-item" onClick={handleLogout} style={{ color: 'var(--color-danger)' }}>
          <LogOut size={16} /> Sign out
        </button>
      </div>
    </nav>
  );
}
