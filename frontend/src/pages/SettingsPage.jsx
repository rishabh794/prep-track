import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api';
import { useAuth } from '../context/AuthContext';

export default function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState('sheets'); // 'sheets' | 'budget' | 'interview'

  // Sheets state
  const [availableSheets, setAvailableSheets] = useState([]);
  const [selectedSheetIds, setSelectedSheetIds] = useState([]);
  const [dedupSummary, setDedupSummary] = useState(null);
  const [savingSheets, setSavingSheets] = useState(false);

  // Budget settings state
  const [dailyTimeBudgetMin, setDailyTimeBudgetMin] = useState(90);
  const [maxNewProblemsDay, setMaxNewProblemsDay] = useState(3);
  const [maxRevisionsDay, setMaxRevisionsDay] = useState(10);
  const [targetSkillLevel, setTargetSkillLevel] = useState(0.8);
  const [savingBudget, setSavingBudget] = useState(false);

  // Interview state
  const [upcomingInterviews, setUpcomingInterviews] = useState([]);
  const [companyName, setCompanyName] = useState('Google');
  const [role, setRole] = useState('SDE-1');
  const [interviewDate, setInterviewDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    return d.toISOString().split('T')[0];
  });
  const [notes, setNotes] = useState('Focus on Dynamic Programming, Trees, and Graphs');
  const [savingInterview, setSavingInterview] = useState(false);

  // Status & Feedback
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (user?.settings) {
      setDailyTimeBudgetMin(user.settings.dailyTimeBudgetMin || 90);
      setMaxNewProblemsDay(user.settings.maxNewProblemsDay || 3);
      setMaxRevisionsDay(user.settings.maxRevisionsDay || 10);
      setTargetSkillLevel(user.settings.targetSkillLevel || 0.8);
    }
  }, [user]);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get('/sheets/available'),
      api.get('/user/sheets'),
      api.get('/interviews/upcoming')
    ])
      .then(([availRes, userSheetsRes, interviewsRes]) => {
        setAvailableSheets(availRes.data);
        const userSheetIds = userSheetsRes.data.map((s) => s.sheetId);
        setSelectedSheetIds(userSheetIds.length > 0 ? userSheetIds : availRes.data.map(s => s.id));
        setUpcomingInterviews(interviewsRes.data || []);
      })
      .catch((err) => {
        console.error('Failed to load settings data', err);
        setError('Failed to load settings data. Please refresh.');
      })
      .finally(() => setLoading(false));
  }, []);

  const showToast = (text) => {
    setMessage(text);
    setTimeout(() => setMessage(null), 4000);
  };

  const handleToggleSheet = (id) => {
    if (selectedSheetIds.includes(id)) {
      if (selectedSheetIds.length === 1) {
        setError('You must keep at least one active target sheet.');
        setTimeout(() => setError(null), 3000);
        return;
      }
      setSelectedSheetIds(selectedSheetIds.filter((item) => item !== id));
    } else {
      setSelectedSheetIds([...selectedSheetIds, id]);
    }
    setDedupSummary(null);
  };

  const handleSaveSheets = async () => {
    if (selectedSheetIds.length === 0) {
      setError('Please select at least one sheet.');
      return;
    }
    setError(null);
    setSavingSheets(true);

    try {
      const res = await api.post('/onboarding/sheets', { sheetIds: selectedSheetIds });
      setDedupSummary(res.data);
      showToast('Target sheets and deduplication updated successfully!');
      await refreshUser();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update sheets');
    } finally {
      setSavingSheets(false);
    }
  };

  const handleSaveBudget = async () => {
    setError(null);
    setSavingBudget(true);

    try {
      await api.put('/onboarding/settings', {
        dailyTimeBudgetMin,
        maxNewProblemsDay,
        maxRevisionsDay,
        targetSkillLevel
      });
      await refreshUser();
      showToast('Daily study budget and velocity saved!');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update study budget');
    } finally {
      setSavingBudget(false);
    }
  };

  const handleSaveInterview = async (e) => {
    e.preventDefault();
    setError(null);
    setSavingInterview(true);

    try {
      await api.post('/onboarding/interviews', {
        companyName,
        role,
        interviewDate,
        notes
      });
      const res = await api.get('/interviews/upcoming');
      setUpcomingInterviews(res.data || []);
      showToast(`Interview target for ${companyName} saved!`);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save interview target');
    } finally {
      setSavingInterview(false);
    }
  };

  // Quick stats calculation
  const totalSelectedProblems = availableSheets
    .filter((s) => selectedSheetIds.includes(s.id))
    .reduce((acc, curr) => acc + (curr.totalProblems || 0), 0);

  if (loading) {
    return (
      <div style={{ maxWidth: '1000px', margin: '60px auto', textAlign: 'center', color: 'var(--text-muted)' }}>
        Loading your settings and study profile...
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '1080px', margin: '36px auto', padding: '0 24px' }}>
      {/* Header */}
      <div style={{ marginBottom: '28px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '14px' }}>
          <div>
            <h1 style={{ fontSize: '2rem', color: '#fff', marginBottom: '6px' }}>
              Study Engine Settings
            </h1>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
              Adjust your target sheets, daily learning budget, and interview timeline directly without re-running onboarding.
            </p>
          </div>
          <Link to="/" className="btn btn-secondary btn-sm">
            ← Back to My Sheets
          </Link>
        </div>
      </div>

      {/* Success / Error Alerts */}
      {message && (
        <div style={{
          padding: '12px 18px',
          background: 'rgba(16, 185, 129, 0.15)',
          border: '1px solid var(--easy-color)',
          borderRadius: 'var(--radius-md)',
          color: 'var(--easy-color)',
          fontWeight: 600,
          marginBottom: '24px',
          display: 'flex',
          alignItems: 'center',
          gap: '10px'
        }}>
          <span>✓</span>
          <span>{message}</span>
        </div>
      )}

      {error && (
        <div style={{
          padding: '12px 18px',
          background: 'rgba(244, 63, 94, 0.12)',
          border: '1px solid rgba(244, 63, 94, 0.3)',
          borderRadius: 'var(--radius-md)',
          color: '#f43f5e',
          fontSize: '0.9rem',
          marginBottom: '24px'
        }}>
          {error}
        </div>
      )}

      {/* Settings Navigation Tabs */}
      <div style={{
        display: 'flex',
        gap: '8px',
        borderBottom: '1px solid var(--border-subtle)',
        marginBottom: '28px',
        overflowX: 'auto'
      }}>
        <button
          onClick={() => setActiveTab('sheets')}
          style={{
            padding: '12px 20px',
            fontSize: '0.95rem',
            fontWeight: 600,
            background: 'none',
            border: 'none',
            borderBottom: activeTab === 'sheets' ? '2.5px solid var(--accent-primary)' : '2.5px solid transparent',
            color: activeTab === 'sheets' ? '#fff' : 'var(--text-muted)',
            cursor: 'pointer',
            transition: 'all 0.2s ease',
            whiteSpace: 'nowrap'
          }}
        >
          📚 Target Sheets ({selectedSheetIds.length} Selected)
        </button>

        <button
          onClick={() => setActiveTab('budget')}
          style={{
            padding: '12px 20px',
            fontSize: '0.95rem',
            fontWeight: 600,
            background: 'none',
            border: 'none',
            borderBottom: activeTab === 'budget' ? '2.5px solid var(--accent-primary)' : '2.5px solid transparent',
            color: activeTab === 'budget' ? '#fff' : 'var(--text-muted)',
            cursor: 'pointer',
            transition: 'all 0.2s ease',
            whiteSpace: 'nowrap'
          }}
        >
          ⏱️ Daily Study Budget ({dailyTimeBudgetMin}m / day)
        </button>

        <button
          onClick={() => setActiveTab('interview')}
          style={{
            padding: '12px 20px',
            fontSize: '0.95rem',
            fontWeight: 600,
            background: 'none',
            border: 'none',
            borderBottom: activeTab === 'interview' ? '2.5px solid var(--accent-primary)' : '2.5px solid transparent',
            color: activeTab === 'interview' ? '#fff' : 'var(--text-muted)',
            cursor: 'pointer',
            transition: 'all 0.2s ease',
            whiteSpace: 'nowrap'
          }}
        >
          🎯 Interview Targets ({upcomingInterviews.length})
        </button>
      </div>

      {/* ============================================================== */}
      {/* TAB 1: TARGET SHEETS & DEDUPLICATION */}
      {/* ============================================================== */}
      {activeTab === 'sheets' && (
        <div className="glass-panel animate-fade-in" style={{ padding: '32px' }}>
          <div style={{ marginBottom: '24px' }}>
            <h2 style={{ fontSize: '1.35rem', color: '#fff', marginBottom: '6px' }}>
              Select Active Preparation Sheets
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              PrepTrack cross-references all selected sheets against our canonical problem registry, automatically deduplicating identical problems.
            </p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', marginBottom: '28px' }}>
            {availableSheets.map((sheet) => {
              const selected = selectedSheetIds.includes(sheet.id);
              return (
                <div
                  key={sheet.id}
                  onClick={() => handleToggleSheet(sheet.id)}
                  style={{
                    padding: '22px',
                    borderRadius: 'var(--radius-md)',
                    background: selected ? 'rgba(99, 102, 241, 0.12)' : 'rgba(255, 255, 255, 0.02)',
                    border: `1.5px solid ${selected ? 'var(--accent-primary)' : 'var(--border-subtle)'}`,
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    boxShadow: selected ? '0 0 20px rgba(99, 102, 241, 0.15)' : 'none'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
                    <h3 style={{ fontSize: '1.15rem', color: '#fff' }}>{sheet.name}</h3>
                    <div style={{
                      width: '22px',
                      height: '22px',
                      borderRadius: '5px',
                      border: `1.5px solid ${selected ? 'var(--accent-primary)' : 'var(--text-dim)'}`,
                      background: selected ? 'var(--accent-primary)' : 'transparent',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#fff',
                      fontSize: '0.8rem',
                      fontWeight: 700
                    }}>
                      {selected && '✓'}
                    </div>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '16px', minHeight: '38px', lineHeight: 1.45 }}>
                    {sheet.description}
                  </p>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                      {sheet.totalProblems} Problems
                    </span>
                    {sheet.sourceUrl && (
                      <a
                        href={sheet.sourceUrl}
                        target="_blank"
                        rel="noreferrer"
                        onClick={(e) => e.stopPropagation()}
                        style={{ fontSize: '0.75rem', color: 'var(--text-dim)', textDecoration: 'underline' }}
                      >
                        Source Link ↗
                      </a>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Deduplication Summary Banner */}
          {dedupSummary ? (
            <div style={{
              background: 'rgba(6, 182, 212, 0.08)',
              border: '1px solid rgba(6, 182, 212, 0.3)',
              borderRadius: 'var(--radius-md)',
              padding: '20px',
              marginBottom: '28px'
            }}>
              <h4 style={{ color: 'var(--accent-cyan)', fontSize: '0.95rem', marginBottom: '12px', fontWeight: 700 }}>
                ⚡ Deduplication Summary for Active Selection
              </h4>
              <div style={{ display: 'flex', gap: '30px', flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Raw Total Across Sheets</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>{dedupSummary.totalProblemsAcrossSheets}</div>
                </div>
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Unique Problems in Catalog</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--easy-color)' }}>{dedupSummary.uniqueProblems}</div>
                </div>
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Duplicate Overlaps Saved</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-cyan)' }}>{dedupSummary.overlapProblems}</div>
                </div>
              </div>
            </div>
          ) : (
            <div style={{
              background: 'rgba(255, 255, 255, 0.03)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)',
              padding: '16px 20px',
              marginBottom: '28px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <span style={{ fontSize: '0.9rem', color: 'var(--text-muted)' }}>
                {selectedSheetIds.length} sheet{selectedSheetIds.length > 1 ? 's' : ''} selected ({totalSelectedProblems} total problem references).
              </span>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                Click Save below to recompute deduplication.
              </span>
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
            <button
              onClick={handleSaveSheets}
              className="btn btn-primary"
              disabled={savingSheets || selectedSheetIds.length === 0}
            >
              {savingSheets ? 'Saving Changes...' : 'Save Target Sheets 💾'}
            </button>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* TAB 2: DAILY STUDY BUDGET & VELOCITY */}
      {/* ============================================================== */}
      {activeTab === 'budget' && (
        <div className="glass-panel animate-fade-in" style={{ padding: '32px' }}>
          <div style={{ marginBottom: '28px' }}>
            <h2 style={{ fontSize: '1.35rem', color: '#fff', marginBottom: '6px' }}>
              Daily Study Budget & Practice Velocity
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              Set your target daily workload. PrepTrack uses these constraints to generate balanced daily schedules of new problems and active recall revisions.
            </p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '28px', marginBottom: '32px' }}>
            {/* Daily Minutes Budget */}
            <div style={{
              padding: '24px',
              background: 'rgba(255, 255, 255, 0.02)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <label className="form-label" style={{ fontSize: '1rem', color: '#fff', fontWeight: 600 }}>
                  Daily Time Budget
                </label>
                <span style={{
                  fontSize: '1.15rem',
                  fontWeight: 800,
                  color: 'var(--accent-cyan)',
                  background: 'rgba(6, 182, 212, 0.1)',
                  padding: '4px 12px',
                  borderRadius: 'var(--radius-sm)'
                }}>
                  {dailyTimeBudgetMin} min/day
                </span>
              </div>
              <p style={{ fontSize: '0.825rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
                How much focused time you can commit every single day.
              </p>
              <input
                type="range"
                min="30"
                max="240"
                step="15"
                value={dailyTimeBudgetMin}
                onChange={(e) => setDailyTimeBudgetMin(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--accent-primary)', marginBottom: '14px', cursor: 'pointer' }}
              />
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {[45, 60, 90, 120, 150].map((mins) => (
                  <button
                    key={mins}
                    type="button"
                    onClick={() => setDailyTimeBudgetMin(mins)}
                    style={{
                      padding: '4px 10px',
                      borderRadius: 'var(--radius-sm)',
                      background: dailyTimeBudgetMin === mins ? 'var(--accent-primary)' : 'rgba(255,255,255,0.05)',
                      color: '#fff',
                      fontSize: '0.75rem',
                      border: 'none',
                      cursor: 'pointer'
                    }}
                  >
                    {mins}m
                  </button>
                ))}
              </div>
            </div>

            {/* Target Skill Mastery */}
            <div style={{
              padding: '24px',
              background: 'rgba(255, 255, 255, 0.02)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <label className="form-label" style={{ fontSize: '1rem', color: '#fff', fontWeight: 600 }}>
                  Mastery Target
                </label>
                <span style={{
                  fontSize: '1.15rem',
                  fontWeight: 800,
                  color: 'var(--easy-color)',
                  background: 'rgba(16, 185, 129, 0.1)',
                  padding: '4px 12px',
                  borderRadius: 'var(--radius-sm)'
                }}>
                  {Math.round(targetSkillLevel * 100)}% Proficiency
                </span>
              </div>
              <p style={{ fontSize: '0.825rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
                Target retention score required before a pattern is considered interview-ready.
              </p>
              <input
                type="range"
                min="0.5"
                max="0.95"
                step="0.05"
                value={targetSkillLevel}
                onChange={(e) => setTargetSkillLevel(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--easy-color)', marginBottom: '14px', cursor: 'pointer' }}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                <span>50% (Fast Pace)</span>
                <span>80% (Recommended)</span>
                <span>95% (Thorough Mastery)</span>
              </div>
            </div>

            {/* Max New Problems */}
            <div style={{
              padding: '24px',
              background: 'rgba(255, 255, 255, 0.02)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <label className="form-label" style={{ fontSize: '1rem', color: '#fff', fontWeight: 600 }}>
                  Max New Problems / Day
                </label>
                <span style={{ fontSize: '1.15rem', fontWeight: 800, color: 'var(--medium-color)' }}>
                  {maxNewProblemsDay} new
                </span>
              </div>
              <p style={{ fontSize: '0.825rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
                Limits cognitive fatigue by capping unfamiliar concepts assigned per day.
              </p>
              <input
                type="range"
                min="1"
                max="8"
                step="1"
                value={maxNewProblemsDay}
                onChange={(e) => setMaxNewProblemsDay(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--medium-color)', cursor: 'pointer' }}
              />
            </div>

            {/* Max Revisions */}
            <div style={{
              padding: '24px',
              background: 'rgba(255, 255, 255, 0.02)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <label className="form-label" style={{ fontSize: '1rem', color: '#fff', fontWeight: 600 }}>
                  Max Revisions / Day
                </label>
                <span style={{ fontSize: '1.15rem', fontWeight: 800, color: 'var(--accent-secondary)' }}>
                  {maxRevisionsDay} reviews
                </span>
              </div>
              <p style={{ fontSize: '0.825rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
                Cap on spaced repetition cards and recall prompts per morning session.
              </p>
              <input
                type="range"
                min="3"
                max="25"
                step="1"
                value={maxRevisionsDay}
                onChange={(e) => setMaxRevisionsDay(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--accent-secondary)', cursor: 'pointer' }}
              />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button
              onClick={handleSaveBudget}
              className="btn btn-primary"
              disabled={savingBudget}
            >
              {savingBudget ? 'Saving Settings...' : 'Save Study Budget 💾'}
            </button>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* TAB 3: TARGET INTERVIEW & TIMELINE */}
      {/* ============================================================== */}
      {activeTab === 'interview' && (
        <div className="glass-panel animate-fade-in" style={{ padding: '32px' }}>
          <div style={{ marginBottom: '28px' }}>
            <h2 style={{ fontSize: '1.35rem', color: '#fff', marginBottom: '6px' }}>
              Interview Target & Priority Tuning
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              Target interviews allow PrepTrack to prioritize company-specific high-frequency patterns in your daily revision plan.
            </p>
          </div>

          {/* Existing Upcoming Interviews */}
          {upcomingInterviews.length > 0 && (
            <div style={{ marginBottom: '32px' }}>
              <h3 style={{ fontSize: '1.05rem', color: '#fff', marginBottom: '14px' }}>
                Scheduled Target Interviews ({upcomingInterviews.length})
              </h3>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '14px' }}>
                {upcomingInterviews.map((iv) => {
                  const targetD = new Date(iv.interviewDate);
                  const now = new Date();
                  const diffDays = Math.ceil((targetD - now) / (1000 * 60 * 60 * 24));

                  return (
                    <div
                      key={iv.id}
                      style={{
                        padding: '18px',
                        borderRadius: 'var(--radius-md)',
                        background: 'rgba(255, 255, 255, 0.03)',
                        border: '1px solid var(--border-subtle)'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                        <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>
                          {iv.companyName}
                        </span>
                        <span style={{
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          padding: '3px 8px',
                          borderRadius: '4px',
                          background: diffDays <= 7 ? 'rgba(244, 63, 94, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                          color: diffDays <= 7 ? 'var(--hard-color)' : 'var(--accent-primary)'
                        }}>
                          {diffDays > 0 ? `${diffDays}d remaining` : 'Today'}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                        {iv.role || 'Software Engineer'} • {iv.roundName || 'Technical Round'}
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                        Date: {iv.interviewDate}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Add / Update Interview Form */}
          <form onSubmit={handleSaveInterview} style={{
            padding: '24px',
            background: 'rgba(255, 255, 255, 0.02)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)'
          }}>
            <h3 style={{ fontSize: '1.05rem', color: '#fff', marginBottom: '18px' }}>
              Add / Update Target Interview
            </h3>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px', marginBottom: '18px' }}>
              <div className="form-group">
                <label className="form-label">Company Name</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Google, Amazon, Uber"
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
                  placeholder="e.g. SDE-1, Full Stack"
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  required
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px', marginBottom: '18px' }}>
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

              <div className="form-group">
                <label className="form-label">Focus Notes & Scope</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Heavy emphasis on Graphs and Dynamic Programming"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="submit"
                className="btn btn-primary"
                disabled={savingInterview}
              >
                {savingInterview ? 'Saving Target...' : 'Save Interview Target 🎯'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Footer Option to Re-run Wizard if desired */}
      <div style={{
        marginTop: '32px',
        textAlign: 'center',
        paddingTop: '20px',
        borderTop: '1px solid var(--border-subtle)',
        fontSize: '0.85rem',
        color: 'var(--text-dim)'
      }}>
        Prefer walking through the 4-step onboarding wizard again?{' '}
        <Link
          to="/onboarding?force=true"
          style={{ color: 'var(--accent-cyan)', textDecoration: 'underline' }}
        >
          Launch Full Onboarding Wizard ➔
        </Link>
      </div>
    </div>
  );
}
