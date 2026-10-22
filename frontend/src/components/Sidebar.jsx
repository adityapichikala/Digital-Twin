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
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  const isViewer = user?.role?.toUpperCase() === 'VIEWER';
  const visibleNav = NAV.filter((item) => !(isViewer && item.to === '/simulate'));

  return (
    <nav className="sidebar">
      <div className="sidebar-logo">
        <h1>TwinDigital</h1>
        <p>Kubernetes Digital Twin</p>
      </div>

      {visibleNav.map(({ to, label, icon: Icon }) => (
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

      <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {user && (
          <div style={{ padding: '0.4rem 0.75rem', fontSize: '0.75rem', color: 'var(--color-text-secondary)', borderTop: '1px solid var(--color-border)', paddingTop: '0.75rem' }}>
            <span style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>{user.username}</span>
            <span className={`badge ${isViewer ? 'badge-blue' : 'badge-green'}`} style={{ marginLeft: '0.5rem', fontSize: '0.65rem' }}>
              {user.role}
            </span>
          </div>
        )}
        <button id="logout-btn" className="nav-item" onClick={handleLogout} style={{ color: 'var(--color-danger)' }}>
          <LogOut size={16} /> Sign out
        </button>
      </div>
    </nav>
  );
}

