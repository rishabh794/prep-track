import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { Application, ApplicationStatus, Interview, InterviewTopic } from '../types';
import InterviewDebriefModal from '../components/InterviewDebriefModal';

const STATUS_COLUMNS: { key: ApplicationStatus; label: string; color: string }[] = [
  { key: 'WISHLIST', label: 'Wishlist', color: '#94a3b8' },
  { key: 'APPLIED', label: 'Applied', color: '#38bdf8' },
  { key: 'OA', label: 'OA / Screen', color: '#f59e0b' },
  { key: 'INTERVIEW', label: 'Interviewing', color: '#818cf8' },
  { key: 'OFFER', label: 'Offer', color: '#34d399' },
  { key: 'REJECTED', label: 'Archived', color: '#64748b' },
];

export default function InterviewsPage() {
  const [activeTab, setActiveTab] = useState<'applications' | 'interviews'>('applications');
  const [applications, setApplications] = useState<Application[]>([]);
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  // Modals
  const [showAddAppModal, setShowAddAppModal] = useState<boolean>(false);
  const [addInterviewAppId, setAddInterviewAppId] = useState<number | null>(null);
  const [debriefInterview, setDebriefInterview] = useState<Interview | null>(null);

  // Add Application Form
  const [appCompany, setAppCompany] = useState('');
  const [appRole, setAppRole] = useState('Software Engineer');
  const [appStatus, setAppStatus] = useState<ApplicationStatus>('APPLIED');
  const [appDate, setAppDate] = useState(new Date().toISOString().split('T')[0]);
  const [appNotes, setAppNotes] = useState('');
  const [submittingApp, setSubmittingApp] = useState(false);

  // Add Interview Form
  const [intDate, setIntDate] = useState('');
  const [intRound, setIntRound] = useState('Round 1: DSA / Coding');
  const [intNotes, setIntNotes] = useState('');
  const [submittingInt, setSubmittingInt] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [appsRes, intsRes] = await Promise.all([
        api.get<Application[]>('/applications'),
        api.get<Interview[]>('/interviews'),
      ]);
      setApplications(appsRes.data);
      setInterviews(intsRes.data);
    } catch (err) {
      console.error('Error fetching applications or interviews', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleCreateApplication = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!appCompany.trim()) return;
    setSubmittingApp(true);
    try {
      await api.post('/applications', {
        companyName: appCompany,
        role: appRole,
        status: appStatus,
        appliedDate: appDate,
        notes: appNotes,
      });
      setShowAddAppModal(false);
      setAppCompany('');
      setAppNotes('');
      fetchData();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to create application');
    } finally {
      setSubmittingApp(false);
    }
  };

  const handleUpdateAppStatus = async (appId: number, newStatus: ApplicationStatus) => {
    try {
      await api.put(`/applications/${appId}`, { status: newStatus });
      fetchData();
    } catch (err) {
      console.error('Failed to update status', err);
    }
  };

  const handleDeleteApplication = async (appId: number) => {
    if (!window.confirm('Delete this application and its interview rounds?')) return;
    try {
      await api.delete(`/applications/${appId}`);
      fetchData();
    } catch (err) {
      console.error('Failed to delete application', err);
    }
  };

  const handleAddInterview = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!addInterviewAppId || !intDate) return;
    setSubmittingInt(true);
    try {
      await api.post(`/applications/${addInterviewAppId}/interviews`, {
        interviewDate: intDate,
        roundName: intRound,
        notes: intNotes,
        outcome: 'UPCOMING',
      });
      setAddInterviewAppId(null);
      setIntDate('');
      setIntNotes('');
      fetchData();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to schedule interview round');
    } finally {
      setSubmittingInt(false);
    }
  };

  const calculateDaysRemaining = (targetDate: string) => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(targetDate);
    target.setHours(0, 0, 0, 0);
    const diffTime = target.getTime() - today.getTime();
    return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '40px auto', padding: '0 24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '28px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '2.1rem', color: '#fff', marginBottom: '6px', fontWeight: 800 }}>
            Applications & Interview Tracking
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
            Track application pipelines, schedule interview rounds, debrief AI feedback, and calibrate your knapsack engine.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '12px' }}>
          <button
            onClick={() => setShowAddAppModal(true)}
            className="btn btn-primary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
          >
            <span>+</span> Add Application
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid var(--border-subtle)', marginBottom: '32px' }}>
        <button
          onClick={() => setActiveTab('applications')}
          style={{
            padding: '12px 20px',
            background: 'transparent',
            border: 'none',
            borderBottom: activeTab === 'applications' ? '2px solid var(--accent-cyan)' : '2px solid transparent',
            color: activeTab === 'applications' ? '#fff' : 'var(--text-muted)',
            fontWeight: 700,
            fontSize: '0.95rem',
            cursor: 'pointer',
            transition: 'all 0.2s ease',
          }}
        >
          📂 Application Pipeline ({applications.length})
        </button>
        <button
          onClick={() => setActiveTab('interviews')}
          style={{
            padding: '12px 20px',
            background: 'transparent',
            border: 'none',
            borderBottom: activeTab === 'interviews' ? '2px solid var(--accent-cyan)' : '2px solid transparent',
            color: activeTab === 'interviews' ? '#fff' : 'var(--text-muted)',
            fontWeight: 700,
            fontSize: '0.95rem',
            cursor: 'pointer',
            transition: 'all 0.2s ease',
          }}
        >
          🎯 Target Interviews & Debriefs ({interviews.length})
        </button>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
          Loading your pipeline data...
        </div>
      ) : activeTab === 'applications' ? (
        /* Applications Pipeline View */
        <div>
          {applications.length === 0 ? (
            <div className="glass-panel" style={{ padding: '48px 32px', textAlign: 'center' }}>
              <div style={{ fontSize: '2.5rem', marginBottom: '16px' }}>💼</div>
              <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '8px' }}>
                No Applications Yet
              </h2>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '24px' }}>
                Add your target job applications to organize interviews, run readiness forecasts, and feed debrief insights into your daily planner.
              </p>
              <button onClick={() => setShowAddAppModal(true)} className="btn btn-primary btn-sm">
                + Create First Application
              </button>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '20px' }}>
              {applications.map((app) => (
                <div key={app.id} className="glass-panel" style={{ padding: '22px', display: 'flex', flexDirection: 'column' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                    <div>
                      <h3 style={{ fontSize: '1.3rem', color: '#fff', fontWeight: 700 }}>{app.companyName}</h3>
                      <div style={{ fontSize: '0.85rem', color: 'var(--accent-cyan)', fontWeight: 600 }}>
                        {app.role || 'Software Engineer'}
                      </div>
                    </div>
                    <select
                      value={app.status}
                      onChange={(e) => handleUpdateAppStatus(app.id, e.target.value as ApplicationStatus)}
                      style={{
                        background: 'rgba(255, 255, 255, 0.08)',
                        color: '#fff',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: '6px',
                        padding: '4px 8px',
                        fontSize: '0.78rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      {STATUS_COLUMNS.map((col) => (
                        <option key={col.key} value={col.key} style={{ background: '#0f172a', color: '#fff' }}>
                          {col.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  {app.appliedDate && (
                    <div style={{ fontSize: '0.78rem', color: 'var(--text-dim)', marginBottom: '8px' }}>
                      📅 Applied: {app.appliedDate}
                    </div>
                  )}

                  {app.notes && (
                    <p style={{
                      fontSize: '0.82rem',
                      color: 'var(--text-muted)',
                      background: 'rgba(255, 255, 255, 0.02)',
                      padding: '8px 12px',
                      borderRadius: '6px',
                      marginBottom: '14px',
                    }}>
                      {app.notes}
                    </p>
                  )}

                  {/* Attached Interviews */}
                  <div style={{ marginTop: 'auto', borderTop: '1px solid var(--border-subtle)', paddingTop: '14px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                      <span style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase' }}>
                        Interview Rounds ({app.interviews?.length || 0})
                      </span>
                      <button
                        onClick={() => setAddInterviewAppId(app.id)}
                        className="btn btn-secondary btn-sm"
                        style={{ padding: '3px 8px', fontSize: '0.72rem' }}
                      >
                        + Add Round
                      </button>
                    </div>

                    {app.interviews && app.interviews.length > 0 ? (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        {app.interviews.map((intRound) => (
                          <div
                            key={intRound.id}
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              padding: '8px 10px',
                              borderRadius: '6px',
                              background: 'rgba(255, 255, 255, 0.03)',
                              border: '1px solid var(--border-subtle)',
                              fontSize: '0.8rem',
                            }}
                          >
                            <div>
                              <div style={{ fontWeight: 600, color: '#fff' }}>{intRound.roundName || 'Interview'}</div>
                              <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>
                                {intRound.interviewDate} • {intRound.outcome || 'UPCOMING'}
                              </div>
                            </div>
                            <div style={{ display: 'flex', gap: '6px' }}>
                              <button
                                onClick={() => setDebriefInterview(intRound)}
                                className="btn btn-secondary btn-sm"
                                style={{ padding: '3px 8px', fontSize: '0.7rem' }}
                              >
                                Debrief
                              </button>
                              <Link
                                to={`/forecast/${intRound.id}`}
                                className="btn btn-primary btn-sm"
                                style={{ padding: '3px 8px', fontSize: '0.7rem', textDecoration: 'none' }}
                              >
                                Forecast
                              </Link>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-dim)', fontStyle: 'italic' }}>
                        No interview rounds scheduled yet.
                      </div>
                    )}

                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '12px' }}>
                      <button
                        onClick={() => handleDeleteApplication(app.id)}
                        style={{
                          background: 'transparent',
                          border: 'none',
                          color: '#f87171',
                          fontSize: '0.72rem',
                          cursor: 'pointer',
                        }}
                      >
                        Delete Application
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        /* Target Interviews View */
        <div>
          {interviews.length === 0 ? (
            <div className="glass-panel" style={{ padding: '48px 32px', textAlign: 'center' }}>
              <div style={{ fontSize: '2.5rem', marginBottom: '16px' }}>🎯</div>
              <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '8px' }}>
                No Target Interviews Scheduled
              </h2>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '24px' }}>
                Schedule upcoming interview target dates to allow the PrepTrack engine to calculate pattern coverage and readiness forecasts.
              </p>
              <button onClick={() => setShowAddAppModal(true)} className="btn btn-primary btn-sm">
                + Create Application & Target Date
              </button>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
              {interviews.map((item) => {
                const daysLeft = calculateDaysRemaining(item.interviewDate);
                return (
                  <div key={item.id} className="glass-panel" style={{ padding: '24px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
                      <div>
                        <h3 style={{ fontSize: '1.35rem', color: '#fff', fontWeight: 700 }}>{item.companyName}</h3>
                        <div style={{ fontSize: '0.85rem', color: 'var(--accent-cyan)', fontWeight: 600 }}>
                          {item.role || 'Software Engineer'}
                        </div>
                      </div>
                      <span
                        className="badge"
                        style={{
                          background: daysLeft <= 14 ? 'rgba(244, 63, 94, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                          color: daysLeft <= 14 ? '#f43f5e' : '#a5b4fc',
                          borderColor: daysLeft <= 14 ? 'rgba(244, 63, 94, 0.3)' : 'rgba(99, 102, 241, 0.3)',
                        }}
                      >
                        {daysLeft > 0 ? `${daysLeft} Days Left` : daysLeft === 0 ? 'Today!' : 'Completed'}
                      </span>
                    </div>

                    <div style={{ fontSize: '0.825rem', color: 'var(--text-muted)', marginBottom: '14px' }}>
                      📅 Target Date: <strong>{item.interviewDate}</strong> • Round: <strong>{item.roundName || 'Technical'}</strong>
                    </div>

                    {item.notes && (
                      <div style={{
                        padding: '10px 14px',
                        borderRadius: 'var(--radius-sm)',
                        background: 'rgba(255, 255, 255, 0.03)',
                        border: '1px solid var(--border-subtle)',
                        fontSize: '0.825rem',
                        color: 'var(--text-main)',
                        lineHeight: 1.5,
                        marginBottom: '14px',
                      }}>
                        {item.notes}
                      </div>
                    )}

                    {item.topics && item.topics.length > 0 && (
                      <div style={{ marginBottom: '14px', display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        {item.topics.map((t, idx) => (
                          <span
                            key={idx}
                            style={{
                              fontSize: '0.72rem',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              background: t.wasWeak ? 'rgba(239, 68, 68, 0.12)' : 'rgba(16, 185, 129, 0.12)',
                              color: t.wasWeak ? '#fca5a5' : '#86efac',
                              border: t.wasWeak ? '1px solid rgba(239, 68, 68, 0.3)' : '1px solid rgba(16, 185, 129, 0.3)',
                              fontWeight: 500,
                            }}
                          >
                            {t.wasWeak ? '⚠️ ' : '✅ '}{t.patternName}
                          </span>
                        ))}
                      </div>
                    )}

                    <div style={{ marginTop: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--border-subtle)', paddingTop: '14px', flexWrap: 'wrap', gap: '8px' }}>
                      <button
                        onClick={() => setDebriefInterview(item)}
                        className="btn btn-secondary btn-sm"
                        style={{
                          background: 'rgba(99, 102, 241, 0.12)',
                          color: '#a5b4fc',
                          borderColor: 'rgba(99, 102, 241, 0.3)',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                        }}
                      >
                        <span>🎙️</span> AI Debrief {item.aiExtracted ? '✓' : ''}
                      </button>

                      <Link
                        to={`/forecast/${item.id}`}
                        className="btn btn-primary btn-sm"
                        style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                      >
                        <span>📊</span> Forecast ➔
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Modal: Add Application */}
      {showAddAppModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(5, 8, 16, 0.75)',
          backdropFilter: 'blur(8px)',
          zIndex: 100,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '20px',
        }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '480px', padding: '30px', backgroundColor: '#0d1322' }}>
            <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '20px', fontWeight: 700 }}>
              Add Job Application
            </h2>

            <form onSubmit={handleCreateApplication}>
              <div className="form-group">
                <label className="form-label">Company Name *</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Google, Meta, Stripe"
                  value={appCompany}
                  onChange={(e) => setAppCompany(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Role</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Software Engineer"
                  value={appRole}
                  onChange={(e) => setAppRole(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Current Pipeline Stage</label>
                <select
                  className="form-input"
                  value={appStatus}
                  onChange={(e) => setAppStatus(e.target.value as ApplicationStatus)}
                >
                  {STATUS_COLUMNS.map((col) => (
                    <option key={col.key} value={col.key}>
                      {col.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Applied Date</label>
                <input
                  type="date"
                  className="form-input"
                  value={appDate}
                  onChange={(e) => setAppDate(e.target.value)}
                />
              </div>

              <div className="form-group" style={{ marginBottom: '24px' }}>
                <label className="form-label">Notes & Job Description</label>
                <textarea
                  className="form-input"
                  rows={3}
                  placeholder="e.g. Referral from college alumni, high emphasis on concurrency and system design"
                  value={appNotes}
                  onChange={(e) => setAppNotes(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  onClick={() => setShowAddAppModal(false)}
                  className="btn btn-secondary"
                  disabled={submittingApp}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={submittingApp}
                >
                  {submittingApp ? 'Saving...' : 'Create Application'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Interview Round to Application */}
      {addInterviewAppId && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(5, 8, 16, 0.75)',
          backdropFilter: 'blur(8px)',
          zIndex: 100,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '20px',
        }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '480px', padding: '30px', backgroundColor: '#0d1322' }}>
            <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '20px', fontWeight: 700 }}>
              Schedule Interview Round
            </h2>

            <form onSubmit={handleAddInterview}>
              <div className="form-group">
                <label className="form-label">Round Name</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Technical Round 1, Bar Raiser"
                  value={intRound}
                  onChange={(e) => setIntRound(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Interview Date *</label>
                <input
                  type="date"
                  className="form-input"
                  value={intDate}
                  onChange={(e) => setIntDate(e.target.value)}
                  required
                />
              </div>

              <div className="form-group" style={{ marginBottom: '24px' }}>
                <label className="form-label">Round Notes</label>
                <textarea
                  className="form-input"
                  rows={3}
                  placeholder="e.g. Focus on Graphs, DP, and Monotonic Stack"
                  value={intNotes}
                  onChange={(e) => setIntNotes(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  onClick={() => setAddInterviewAppId(null)}
                  className="btn btn-secondary"
                  disabled={submittingInt}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={submittingInt}
                >
                  {submittingInt ? 'Scheduling...' : 'Save Round'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Debrief Modal */}
      {debriefInterview && (
        <InterviewDebriefModal
          interview={debriefInterview}
          onClose={() => setDebriefInterview(null)}
          onSuccess={() => {
            setDebriefInterview(null);
            fetchData();
          }}
        />
      )}
    </div>
  );
}
