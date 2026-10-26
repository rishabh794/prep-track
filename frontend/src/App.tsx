import React, { ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Navbar from './components/Navbar';
import AuthPage from './pages/AuthPage';
import OnboardingPage from './pages/OnboardingPage';
import SheetsPage from './pages/SheetsPage';
import ExploreSheetsPage from './pages/ExploreSheetsPage';
import InterviewsPage from './pages/InterviewsPage';
import SettingsPage from './pages/SettingsPage';
import TodayPage from './pages/TodayPage';
import { ForecastPage } from './pages/ForecastPage';
import SimulatorPage from './pages/SimulatorPage';

interface ProtectedRouteProps {
  children: ReactNode;
}

function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px 0', color: 'var(--text-muted)' }}>
        Authenticating session...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/auth" replace />;
  }

  return <>{children}</>;
}

function DashboardIndexRoute() {
  const { user } = useAuth();
  if (user && user.settings && !user.settings.onboardingComplete) {
    return <Navigate to="/onboarding" replace />;
  }
  return <TodayPage />;
}

function AppRoutes() {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Navbar />
      <main style={{ flex: 1, paddingBottom: '60px' }}>
        <Routes>
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <DashboardIndexRoute />
              </ProtectedRoute>
            }
          />
          <Route
            path="/today"
            element={
              <ProtectedRoute>
                <TodayPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/sheets"
            element={
              <ProtectedRoute>
                <SheetsPage />
              </ProtectedRoute>
            }
          />
          <Route path="/explore" element={<ExploreSheetsPage />} />
          <Route path="/auth" element={<AuthPage />} />
          <Route
            path="/onboarding"
            element={
              <ProtectedRoute>
                <OnboardingPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <SettingsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/interviews"
            element={
              <ProtectedRoute>
                <InterviewsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/forecast/:interviewId"
            element={
              <ProtectedRoute>
                <ForecastPage />
              </ProtectedRoute>
            }
          />
          <Route path="/simulator" element={<ProtectedRoute><SimulatorPage /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
