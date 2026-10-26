import React from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/auth');
  };

  const isActive = (path) => location.pathname === path;

  return (
    <header style={{
      borderBottom: '1px solid var(--border-subtle)',
      background: 'rgba(9, 13, 22, 0.85)',
      backdropFilter: 'blur(16px)',
      position: 'sticky',
      top: 0,
      zIndex: 50,
      padding: '0 24px'
    }}>
      <div style={{
        maxWidth: '1280px',
        margin: '0 auto',
        height: '70px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        {/* Brand Logo */}
        <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '38px',
            height: '38px',
            borderRadius: '10px',
            background: 'var(--gradient-brand)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 'var(--shadow-glow)',
            fontWeight: 800,
            fontSize: '1.2rem',
            color: '#fff'
          }}>
            P
          </div>
          <div>
            <div style={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.35rem',
              fontWeight: 800,
              letterSpacing: '-0.03em',
              background: 'linear-gradient(90deg, #ffffff 0%, #cbd5e1 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent'
            }}>
              PrepTrack
            </div>
            <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', marginTop: '-4px', fontWeight: 600, letterSpacing: '0.05em' }}>
              DSA PLANNER & ENGINE
            </div>
          </div>
        </Link>

        {/* Navigation Links */}
        <nav style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Link
            to="/"
            style={{
              padding: '8px 14px',
              borderRadius: 'var(--radius-sm)',
              fontSize: '0.9rem',
              fontWeight: 600,
              color: isActive('/') ? '#ffffff' : 'var(--text-muted)',
              background: isActive('/') ? 'rgba(255, 255, 255, 0.08)' : 'transparent',
              transition: 'all 0.2s ease'
            }}
          >
            My Sheets
          </Link>
          <Link
            to="/explore"
            style={{
              padding: '8px 14px',
              borderRadius: 'var(--radius-sm)',
              fontSize: '0.9rem',
              fontWeight: 600,
              color: isActive('/explore') ? '#ffffff' : 'var(--text-muted)',
              background: isActive('/explore') ? 'rgba(255, 255, 255, 0.08)' : 'transparent',
              transition: 'all 0.2s ease'
            }}
          >
            Explore Catalog
          </Link>
          <Link
            to="/interviews"
            style={{
              padding: '8px 14px',
              borderRadius: 'var(--radius-sm)',
              fontSize: '0.9rem',
              fontWeight: 600,
              color: isActive('/interviews') ? '#ffffff' : 'var(--text-muted)',
              background: isActive('/interviews') ? 'rgba(255, 255, 255, 0.08)' : 'transparent',
              transition: 'all 0.2s ease'
            }}
          >
            Interviews
          </Link>
          {user && (
            user.settings?.onboardingComplete ? (
              <Link
                to="/settings"
                style={{
                  padding: '8px 14px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  color: isActive('/settings') ? '#ffffff' : 'var(--text-muted)',
                  background: isActive('/settings') ? 'rgba(255, 255, 255, 0.08)' : 'transparent',
                  transition: 'all 0.2s ease'
                }}
              >
                Settings
              </Link>
            ) : (
              <Link
                to="/onboarding"
                style={{
                  padding: '8px 14px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  color: isActive('/onboarding') ? '#ffffff' : 'var(--text-muted)',
                  background: isActive('/onboarding') ? 'rgba(255, 255, 255, 0.08)' : 'transparent',
                  transition: 'all 0.2s ease'
                }}
              >
                Onboarding Setup
              </Link>
            )
          )}
        </nav>

        {/* User Account Section */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
          {user ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text-main)' }}>
                  {user.displayName}
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                  {user.settings?.dailyTimeBudgetMin || 90}m budget
                </div>
              </div>
              <button
                onClick={handleLogout}
                className="btn btn-secondary btn-sm"
              >
                Log Out
              </button>
            </div>
          ) : (
            <Link to="/auth" className="btn btn-primary btn-sm">
              Sign In / Register
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
