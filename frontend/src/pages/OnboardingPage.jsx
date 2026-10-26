import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import api from '../api';
import { useAuth } from '../context/AuthContext';

export default function OnboardingPage() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const searchParams = new URLSearchParams(location.search);
  const force = searchParams.get('force') === 'true';

  useEffect(() => {
    if (user?.settings?.onboardingComplete && !force) {
      navigate('/settings', { replace: true });
    }
  }, [user, force, navigate]);

  const [step, setStep] = useState(1);
  const [availableSheets, setAvailableSheets] = useState([]);
  const [selectedSheetIds, setSelectedSheetIds] = useState([]);
  const [dedupSummary, setDedupSummary] = useState(null);

  // Settings
  const [dailyTimeBudgetMin, setDailyTimeBudgetMin] = useState(90);
  const [maxNewProblemsDay, setMaxNewProblemsDay] = useState(3);
  const [maxRevisionsDay, setMaxRevisionsDay] = useState(10);
  const [targetSkillLevel, setTargetSkillLevel] = useState(0.8);

  // Interview
  const [companyName, setCompanyName] = useState('Google');
  const [role, setRole] = useState('SDE-1');
  const [interviewDate, setInterviewDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    return d.toISOString().split('T')[0];
  });
  const [notes, setNotes] = useState('Focus on Dynamic Programming and Graphs');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get('/sheets/available')
      .then((res) => {
        setAvailableSheets(res.data);
        // Default select Blind 75 and NeetCode 150
        const defaultIds = res.data
          .filter((s) => s.name === 'Blind 75' || s.name === 'NeetCode 150')
          .map((s) => s.id);
        setSelectedSheetIds(defaultIds);
      })
      .catch((err) => console.error(err));
  }, []);

  const handleToggleSheet = (id) => {
    if (selectedSheetIds.includes(id)) {
      setSelectedSheetIds(selectedSheetIds.filter((item) => item !== id));
    } else {
      setSelectedSheetIds([...selectedSheetIds, id]);
    }
  };

  const handleSaveSheets = async () => {
    if (selectedSheetIds.length === 0) {
      setError('Please select at least one sheet');
      return;
    }
    setError(null);
    setLoading(true);

    try {
      const res = await api.post('/onboarding/sheets', { sheetIds: selectedSheetIds });
      setDedupSummary(res.data);
      setStep(2);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save sheet selection');
    } finally {
      setLoading(false);
    }
  };

  const handleSaveSettings = async () => {
    setError(null);
    setLoading(true);

    try {
      await api.put('/onboarding/settings', {
        dailyTimeBudgetMin,
        maxNewProblemsDay,
        maxRevisionsDay,
        targetSkillLevel
      });
      setStep(3);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update settings');
    } finally {
      setLoading(false);
    }
  };

  const handleSaveInterview = async (skip = false) => {
    setError(null);
    setLoading(true);

    try {
      if (!skip && companyName.trim()) {
        await api.post('/onboarding/interviews', {
          companyName,
          role,
          interviewDate,
          notes
        });
      }
      setStep(4);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save interview target');
    } finally {
      setLoading(false);
    }
  };

  const handleFinishOnboarding = async () => {
    setLoading(true);
    try {
      await api.post('/onboarding/complete');
      await refreshUser();
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to finalize onboarding');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '880px', margin: '40px auto', padding: '0 20px' }}>
      {/* Stepper Header */}
      <div style={{ marginBottom: '36px', textAlign: 'center' }}>
        <h1 style={{ fontSize: '2.2rem', color: '#fff', marginBottom: '8px' }}>
          Personalize Your Prep Engine
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
          Configure your multi-sheet catalog, time budget, and interview target
        </p>

        {/* Step Progress indicators */}
        <div style={{ display: 'flex', justifyContent: 'center', gap: '20px', marginTop: '24px' }}>
          {[
            { num: 1, title: 'Pick Sheets' },
            { num: 2, title: 'Daily Budget' },
            { num: 3, title: 'Upcoming Interview' },
            { num: 4, title: 'Review & Launch' }
          ].map((s) => (
            <div
              key={s.num}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                color: step === s.num ? 'var(--text-main)' : step > s.num ? 'var(--easy-color)' : 'var(--text-dim)',
                fontWeight: 600,
                fontSize: '0.85rem'
              }}
            >
              <div style={{
                width: '28px',
                height: '28px',
                borderRadius: '50%',
                background: step === s.num ? 'var(--accent-primary)' : step > s.num ? 'rgba(16, 185, 129, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                border: step > s.num ? '1px solid var(--easy-color)' : 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: step > s.num ? 'var(--easy-color)' : '#fff',
                fontSize: '0.8rem'
              }}>
                {step > s.num ? '✓' : s.num}
              </div>
              <span>{s.title}</span>
            </div>
          ))}
        </div>
      </div>

      {error && (
        <div style={{
          padding: '12px 16px',
          background: 'rgba(244, 63, 94, 0.1)',
          border: '1px solid rgba(244, 63, 94, 0.3)',
          borderRadius: 'var(--radius-md)',
          color: '#f43f5e',
          marginBottom: '24px'
        }}>
          {error}
        </div>
      )}

      {/* STEP 1: SHEET SELECTION & DEDUPLICATION */}
      {step === 1 && (
        <div className="glass-panel animate-fade-in" style={{ padding: '32px' }}>
          <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '8px' }}>
            1. Select Your Target Sheets
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '24px' }}>
            PrepTrack automatically resolves duplicates across sheets so you never solve the same problem twice.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px', marginBottom: '28px' }}>
            {availableSheets.map((sheet) => {
              const selected = selectedSheetIds.includes(sheet.id);
              return (
                <div
                  key={sheet.id}
                  onClick={() => handleToggleSheet(sheet.id)}
                  style={{
                    padding: '20px',
                    borderRadius: 'var(--radius-md)',
                    background: selected ? 'rgba(99, 102, 241, 0.15)' : 'rgba(255, 255, 255, 0.02)',
                    border: `1.5px solid ${selected ? 'var(--accent-primary)' : 'var(--border-subtle)'}`,
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    position: 'relative'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                    <h3 style={{ fontSize: '1.1rem', color: '#fff' }}>{sheet.name}</h3>
                    <div style={{
                      width: '20px',
                      height: '20px',
                      borderRadius: '4px',
                      border: `1.5px solid ${selected ? 'var(--accent-primary)' : 'var(--text-dim)'}`,
                      background: selected ? 'var(--accent-primary)' : 'transparent',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#fff',
                      fontSize: '0.75rem'
                    }}>
                      {selected && '✓'}
                    </div>
                  </div>
                  <p style={{ fontSize: '0.825rem', color: 'var(--text-muted)', marginBottom: '16px', minHeight: '36px' }}>
                    {sheet.description}
                  </p>
                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                    {sheet.totalProblems} Problems
                  </div>
                </div>
              );
            })}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button
              onClick={handleSaveSheets}
              className="btn btn-primary"
              disabled={loading || selectedSheetIds.length === 0}
            >
              {loading ? 'Resolving Duplicates...' : 'Continue to Daily Budget ➔'}
            </button>
          </div>
        </div>
      )}

      {/* STEP 2: DAILY TIME BUDGET & CAPS */}
      {step === 2 && (
        <div className="glass-panel animate-fade-in" style={{ padding: '32px' }}>
          {/* Dedup Callout */}
          {dedupSummary && (
            <div style={{
              padding: '16px 20px',
              background: 'rgba(6, 182, 212, 0.08)',
              border: '1px solid rgba(6, 182, 212, 0.25)',
              borderRadius: 'var(--radius-md)',
              marginBottom: '28px',
              display: 'flex',
              justifyContent: 'space-around',
              textAlign: 'center'
            }}>
              <div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', fontWeight: 600 }}>TOTAL ACROSS SHEETS</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 800, color: '#fff' }}>{dedupSummary.totalProblemsAcrossSheets}</div>
              </div>
              <div style={{ borderLeft: '1px solid var(--border-subtle)', paddingLeft: '20px' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', fontWeight: 600 }}>SHARED OVERLAP DEDUPLICATED</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--medium-color)' }}>{dedupSummary.overlapProblems}</div>
              </div>
              <div style={{ borderLeft: '1px solid var(--border-subtle)', paddingLeft: '20px' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', fontWeight: 600 }}>UNIQUE STUDY TARGET</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--easy-color)' }}>{dedupSummary.uniqueProblems}</div>
              </div>
            </div>
          )}

          <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '8px' }}>
            2. Daily Time Budget & Caps
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '24px' }}>
            The knapsack optimizer packs the highest-value items that strictly fit your daily budget.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px', marginBottom: '32px' }}>
            <div className="form-group">
              <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Daily Time Budget</span>
                <strong style={{ color: 'var(--accent-cyan)' }}>{dailyTimeBudgetMin} minutes</strong>
              </label>
              <input
                type="range"
                min="30"
                max="240"
                step="15"
                value={dailyTimeBudgetMin}
                onChange={(e) => setDailyTimeBudgetMin(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
              />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                Planner will never schedule more work than fits this window.
              </span>
            </div>

            <div className="form-group">
              <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Max New Problems / Day</span>
                <strong style={{ color: 'var(--accent-cyan)' }}>{maxNewProblemsDay} problems</strong>
              </label>
              <input
                type="range"
                min="1"
                max="10"
                value={maxNewProblemsDay}
                onChange={(e) => setMaxNewProblemsDay(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
              />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                Caps cognitive load on new unseen concepts.
              </span>
            </div>

            <div className="form-group">
              <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Max Revisions / Day</span>
                <strong style={{ color: 'var(--accent-cyan)' }}>{maxRevisionsDay} revisions</strong>
              </label>
              <input
                type="range"
                min="3"
                max="30"
                value={maxRevisionsDay}
                onChange={(e) => setMaxRevisionsDay(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
              />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                Recall checks take ~5 mins each.
              </span>
            </div>

            <div className="form-group">
              <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Target Skill Level</span>
                <strong style={{ color: 'var(--accent-cyan)' }}>{Math.round(targetSkillLevel * 100)}%</strong>
              </label>
              <input
                type="range"
                min="0.5"
                max="1.0"
                step="0.05"
                value={targetSkillLevel}
                onChange={(e) => setTargetSkillLevel(Number(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
              />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                Threshold where a pattern is considered interview-ready.
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <button onClick={() => setStep(1)} className="btn btn-secondary">
              Back
            </button>
            <button onClick={handleSaveSettings} className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving...' : 'Continue to Interview Target ➔'}
            </button>
          </div>
        </div>
      )}

      {/* STEP 3: UPCOMING INTERVIEW */}
      {step === 3 && (
        <div className="glass-panel animate-fade-in" style={{ padding: '32px' }}>
          <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '8px' }}>
            3. Add Your Target Interview (Optional)
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '24px' }}>
            PrepTrack forward-simulates your readiness and boosts priority for high-frequency topics tested by this company.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', marginBottom: '20px' }}>
            <div className="form-group">
              <label className="form-label">Company Name</label>
              <input
                type="text"
                className="form-input"
                placeholder="Google, Amazon, Meta..."
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Target Role</label>
              <input
                type="text"
                className="form-input"
                placeholder="SDE-1, Software Engineer..."
                value={role}
                onChange={(e) => setRole(e.target.value)}
              />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: '20px' }}>
            <label className="form-label">Target Interview Date</label>
            <input
              type="date"
              className="form-input"
              value={interviewDate}
              onChange={(e) => setInterviewDate(e.target.value)}
            />
          </div>

          <div className="form-group" style={{ marginBottom: '32px' }}>
            <label className="form-label">Focus Notes & Preparation Scope</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g. Focus on Dynamic Programming, Trees, and Graphs"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <button onClick={() => setStep(2)} className="btn btn-secondary">
              Back
            </button>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button onClick={() => handleSaveInterview(true)} className="btn btn-secondary">
                Skip for now
              </button>
              <button onClick={() => handleSaveInterview(false)} className="btn btn-primary" disabled={loading}>
                {loading ? 'Saving...' : 'Continue to Final Review ➔'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* STEP 4: REVIEW & COMPLETE */}
      {step === 4 && (
        <div className="glass-panel animate-fade-in" style={{ padding: '36px', textAlign: 'center' }}>
          <div style={{
            width: '60px',
            height: '60px',
            borderRadius: '50%',
            background: 'rgba(16, 185, 129, 0.15)',
            border: '2px solid var(--easy-color)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '1.8rem',
            color: 'var(--easy-color)',
            margin: '0 auto 20px'
          }}>
            ✓
          </div>
          <h2 style={{ fontSize: '1.8rem', color: '#fff', marginBottom: '8px' }}>
            Ready to Accelerate Your Prep
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', maxWidth: '500px', margin: '0 auto 28px' }}>
            Your sheets have been deduplicated, daily study budget configured to <strong>{dailyTimeBudgetMin} min/day</strong>, and your learning graph initialized.
          </p>

          <button
            onClick={handleFinishOnboarding}
            className="btn btn-primary"
            style={{ padding: '14px 36px', fontSize: '1.05rem' }}
            disabled={loading}
          >
            {loading ? 'Finalizing Setup...' : 'Launch PrepTrack Dashboard 🚀'}
          </button>
        </div>
      )}
    </div>
  );
}
