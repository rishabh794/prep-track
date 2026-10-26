import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { Interview } from '../types';

export default function InterviewsPage() {
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [showAddModal, setShowAddModal] = useState<boolean>(false);

  // New Interview Form
  const [companyName, setCompanyName] = useState<string>('');
  const [role, setRole] = useState<string>('SDE-1');
  const [interviewDate, setInterviewDate] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);

  const fetchInterviews = () => {
    setLoading(true);
    api.get<Interview[]>('/interviews/upcoming')
      .then((res) => setInterviews(res.data))
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchInterviews();
  }, []);

  const handleAddInterview = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/onboarding/interviews', {
        companyName,
        role,
        interviewDate,
        notes
      });
      setShowAddModal(false);
      setCompanyName('');
      setInterviewDate('');
      setNotes('');
      fetchInterviews();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to schedule interview target');
    } finally {
      setSubmitting(false);
    }
  };

  const calculateDaysRemaining = (targetDate: string) => {
    const today = new Date();
    const target = new Date(targetDate);
    const diffTime = target.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays;
  };

  return (
    <div style={{ maxWidth: '960px', margin: '40px auto', padding: '0 24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h1 style={{ fontSize: '2rem', color: '#fff', marginBottom: '6px' }}>
            Upcoming Interviews
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
            Target dates calibrated into your knapsack planner and readiness forecast
          </p>
        </div>
        <button onClick={() => setShowAddModal(true)} className="btn btn-primary">
          + Add Interview Target
        </button>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
          Loading your target interviews...
        </div>
      ) : interviews.length === 0 ? (
        <div className="glass-panel" style={{ padding: '48px 32px', textAlign: 'center' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '16px' }}>🎯</div>
          <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '8px' }}>
            No Upcoming Interviews Scheduled
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '24px' }}>
            Add an upcoming interview date so the PrepTrack engine can forecast your pattern coverage and triage your study plan.
          </p>
          <button onClick={() => setShowAddModal(true)} className="btn btn-primary btn-sm">
            + Schedule Target Date
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
          {interviews.map((item) => {
            const daysLeft = calculateDaysRemaining(item.interviewDate);
            return (
              <div key={item.id} className="glass-panel" style={{ padding: '24px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
                  <div>
                    <h3 style={{ fontSize: '1.35rem', color: '#fff' }}>{item.companyName}</h3>
                    <div style={{ fontSize: '0.85rem', color: 'var(--accent-cyan)', fontWeight: 600 }}>
                      {item.role || 'Software Engineer'}
                    </div>
                  </div>
                  <span
                    className="badge"
                    style={{
                      background: daysLeft <= 14 ? 'rgba(244, 63, 94, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                      color: daysLeft <= 14 ? '#f43f5e' : '#a5b4fc',
                      borderColor: daysLeft <= 14 ? 'rgba(244, 63, 94, 0.3)' : 'rgba(99, 102, 241, 0.3)'
                    }}
                  >
                    {daysLeft > 0 ? `${daysLeft} Days Left` : daysLeft === 0 ? 'Today!' : 'Passed'}
                  </span>
                </div>

                <div style={{ fontSize: '0.825rem', color: 'var(--text-muted)', marginBottom: '14px' }}>
                  📅 Target Date: <strong>{item.interviewDate}</strong>
                </div>

                {item.notes && (
                  <div style={{
                    padding: '10px 14px',
                    borderRadius: 'var(--radius-sm)',
                    background: 'rgba(255, 255, 255, 0.03)',
                    border: '1px solid var(--border-subtle)',
                    fontSize: '0.825rem',
                    color: 'var(--text-main)',
                    lineHeight: 1.5
                  }}>
                    {item.notes}
                  </div>
                )}

                <div style={{ marginTop: '16px', display: 'flex', justifyContent: 'flex-end', borderTop: '1px solid var(--border-subtle)', paddingTop: '14px' }}>
                  <Link
                    to={`/forecast/${item.id}`}
                    className="btn btn-primary btn-sm"
                    style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                  >
                    <span>📊</span> View Readiness Forecast ➔
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal for adding interview */}
      {showAddModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(5, 8, 16, 0.75)',
          backdropFilter: 'blur(8px)',
          zIndex: 100,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '20px'
        }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '480px', padding: '30px', backgroundColor: '#0d1322' }}>
            <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '20px' }}>
              Add Upcoming Interview Target
            </h2>

            <form onSubmit={handleAddInterview}>
              <div className="form-group">
                <label className="form-label">Company Name</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Google, Microsoft, Meta"
                  value={companyName}
                  onChange={(e) => setCompanyName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Target Role</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. SDE-1"
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Interview Date</label>
                <input
                  type="date"
                  className="form-input"
                  value={interviewDate}
                  onChange={(e) => setInterviewDate(e.target.value)}
                  required
                />
              </div>

              <div className="form-group" style={{ marginBottom: '24px' }}>
                <label className="form-label">Preparation Notes</label>
                <textarea
                  className="form-input"
                  rows={3}
                  placeholder="e.g. Focus on Trees, Graphs, and DP"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="btn btn-secondary"
                  disabled={submitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={submitting}
                >
                  {submitting ? 'Saving...' : 'Save Target Interview'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
