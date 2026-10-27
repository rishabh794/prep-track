import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../api';
import { ReadinessForecast, PatternProjection, TriageStatus } from '../types';

export const ForecastPage: React.FC = () => {
  const { interviewId } = useParams<{ interviewId: string }>();
  const [forecast, setForecast] = useState<ReadinessForecast | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<'ALL' | TriageStatus>('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const fetchForecast = async () => {
    if (!interviewId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<ReadinessForecast>(`/forecast/interviews/${interviewId}`);
      setForecast(res.data);
    } catch (err: any) {
      console.error('Failed to load forecast', err);
      setError(err.response?.data?.message || 'Failed to load readiness forecast.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchForecast();
  }, [interviewId]);

  if (loading) {
    return (
      <div style={{ minHeight: '80vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '16px' }}>
        <div style={{
          width: '42px',
          height: '42px',
          borderRadius: '50%',
          border: '4px solid rgba(99, 102, 241, 0.2)',
          borderTopColor: 'var(--accent-primary)',
          animation: 'spin 0.8s linear infinite'
        }} />
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
          Estimating preparation from your current study settings...
        </p>
        <style>{`
          @keyframes spin {
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    );
  }

  if (error || !forecast) {
    return (
      <div style={{ maxWidth: '500px', margin: '80px auto', padding: '0 20px', textAlign: 'center' }}>
        <div className="glass-panel" style={{ padding: '40px 30px' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '16px' }}>⚠️</div>
          <h2 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '8px' }}>Unable to Load Forecast</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '24px' }}>
            {error || 'Interview forecast could not be found.'}
          </p>
          <div style={{ display: 'flex', justifyContent: 'center', gap: '12px' }}>
            <Link to="/interviews" className="btn btn-secondary btn-sm">
              Back to Interviews
            </Link>
            <button onClick={fetchForecast} className="btn btn-primary btn-sm">
              Retry Simulation
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (forecast.status !== 'AVAILABLE') {
    const needsSheets = forecast.status === 'ONBOARDING_REQUIRED' || forecast.status === 'INSUFFICIENT_DATA';
    return (
      <div style={{ maxWidth: '620px', margin: '64px auto', padding: '0 24px' }}>
        <div className="glass-panel" style={{ padding: '32px' }} role="status">
          <h1 style={{ fontSize: '1.5rem', marginBottom: '12px' }}>
            {forecast.status === 'INTERVIEW_PASSED' ? 'Interview date has passed' : 'Forecast needs preparation data'}
          </h1>
          <p style={{ color: 'var(--text-muted)', marginBottom: '24px' }}>{forecast.statusMessage}</p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px' }}>
            {needsSheets && <Link className="btn btn-primary" to="/settings">Choose problem sheets</Link>}
            <Link className="btn btn-secondary" to="/interviews">Back to interviews</Link>
          </div>
        </div>
      </div>
    );
  }

  const overallPercent = Math.round((forecast.overallReadiness || 0) * 100);
  const strokeDashoffset = 283 - (283 * overallPercent) / 100;

  const getStatusBadge = (status: TriageStatus) => {
    switch (status) {
      case 'ON_TRACK':
        return <span className="badge badge-on-track">✅ ON TRACK</span>;
      case 'IMPROVING':
        return <span className="badge badge-improving">📈 IMPROVING</span>;
      case 'AT_RISK':
        return <span className="badge badge-at-risk">⚠️ AT RISK</span>;
      case 'CONSIDER_DROPPING':
        return <span className="badge badge-consider-dropping">💤 CONSIDER DROPPING</span>;
    }
  };

  const filteredProjections = forecast.patternProjections.filter((p) => {
    const matchesFilter = statusFilter === 'ALL' || p.status === statusFilter;
    const matchesSearch = p.pattern.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  const statusCounts = {
    ALL: forecast.patternProjections.length,
    AT_RISK: forecast.patternProjections.filter((p) => p.status === 'AT_RISK').length,
    IMPROVING: forecast.patternProjections.filter((p) => p.status === 'IMPROVING').length,
    ON_TRACK: forecast.patternProjections.filter((p) => p.status === 'ON_TRACK').length,
    CONSIDER_DROPPING: forecast.patternProjections.filter((p) => p.status === 'CONSIDER_DROPPING').length,
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px 24px' }}>
      {/* Top Breadcrumb & Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px', marginBottom: '32px' }}>
        <div>
          <Link
            to="/interviews"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '0.85rem',
              color: 'var(--text-muted)',
              marginBottom: '10px',
              fontWeight: 500
            }}
          >
            ← Back to Target Interviews
          </Link>
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
            <h1 style={{ fontSize: '2.1rem', fontWeight: 800, color: '#fff', letterSpacing: '-0.02em' }}>
              Readiness Forecast — {forecast.companyName}
            </h1>
            <span
              className="badge"
              style={{
                background: 'rgba(99, 102, 241, 0.18)',
                color: '#a5b4fc',
                borderColor: 'rgba(99, 102, 241, 0.4)',
                fontSize: '0.825rem',
                padding: '4px 12px'
              }}
            >
              ⏳ {forecast.daysRemaining} Days Left
            </span>
          </div>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '6px' }}>
            Interview: <strong style={{ color: '#fff' }}>{forecast.interviewDate}</strong> · {forecast.dailyBudgetMinutes} minutes/day · {forecast.simulationDays} simulated days
          </p>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.825rem', marginTop: '8px' }}>
            Approximate projection through {forecast.projectedThroughDate}, using settings and progress as of {forecast.asOfDate}.
            {forecast.horizonCapped && ' Your interview is beyond the supported forecast horizon.'}
          </p>
        </div>

        <button
          onClick={fetchForecast}
          className="btn btn-secondary btn-sm"
          style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
        >
          <span>⟳</span> Re-simulate Timeline
        </button>
      </div>

      <details className="glass-panel" style={{ padding: '18px 24px', marginBottom: '24px' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 600 }}>Forecast assumptions and limits</summary>
        <ul style={{ paddingLeft: '20px', marginTop: '12px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          {forecast.limitations.map(limitation => <li key={limitation} style={{ margin: '8px 0' }}>{limitation}</li>)}
        </ul>
      </details>

      {/* Top Grid: Overall Readiness Gauge + Triage Recommendations */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px', marginBottom: '32px' }}>
        {/* Readiness Gauge Card */}
        <div className="glass-panel" style={{ padding: '28px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ fontSize: '0.78rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--text-dim)', marginBottom: '16px' }}>
              Overall Readiness Metric
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '10px 0' }}>
              <div style={{ position: 'relative', width: '160px', height: '160px' }}>
                <svg width="160" height="160" viewBox="0 0 100 100" style={{ transform: 'rotate(-90deg)' }}>
                  <circle
                    cx="50"
                    cy="50"
                    r="45"
                    fill="transparent"
                    stroke="rgba(255, 255, 255, 0.08)"
                    strokeWidth="8"
                  />
                  <circle
                    cx="50"
                    cy="50"
                    r="45"
                    fill="transparent"
                    stroke={overallPercent >= 70 ? '#10b981' : overallPercent >= 45 ? '#f59e0b' : '#f43f5e'}
                    strokeWidth="8"
                    strokeDasharray="283"
                    strokeDashoffset={strokeDashoffset}
                    strokeLinecap="round"
                    style={{ transition: 'stroke-dashoffset 1s ease' }}
                  />
                </svg>
                <div style={{
                  position: 'absolute',
                  inset: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <span style={{ fontSize: '2.4rem', fontWeight: 800, color: '#fff', lineHeight: 1 }}>
                    {overallPercent}%
                  </span>
                  <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', marginTop: '4px' }}>
                    Prepared
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px', marginTop: '16px' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#fff', marginBottom: '4px' }}>
              {overallPercent >= 75 ? '🚀 High Interview Readiness' : overallPercent >= 50 ? '⚖️ Moderate Preparedness' : '⚠️ Strategic Focus Required'}
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', lineHeight: 1.4 }}>
              Weighted coverage of company patterns considering simulated problem practice and retention decay.
            </p>
          </div>
        </div>

        {/* Strategic Triage Recommendations */}
        <div className="glass-panel" style={{ padding: '28px', gridColumn: 'span 2' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
            <span style={{ fontSize: '1.25rem' }}>🎯</span>
            <h2 style={{ fontSize: '1.15rem', color: '#fff', fontWeight: 700 }}>
              Strategic Triage & Action Items
            </h2>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {forecast.recommendations.map((rec, idx) => (
              <div key={idx} className="triage-card">
                <div style={{
                  width: '26px',
                  height: '26px',
                  borderRadius: '50%',
                  background: 'var(--gradient-brand)',
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.75rem',
                  fontWeight: 800,
                  flexShrink: 0
                }}>
                  {idx + 1}
                </div>
                <div style={{ fontSize: '0.9rem', color: '#e2e8f0', lineHeight: 1.5, fontWeight: 500 }}>
                  {rec}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Pattern Projections Matrix */}
      <div className="glass-panel" style={{ padding: '28px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
          <div>
            <h2 style={{ fontSize: '1.25rem', color: '#fff', fontWeight: 700, marginBottom: '4px' }}>
              Pattern Mastery Projections
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.825rem' }}>
              Estimated skill and retention through {forecast.projectedThroughDate}
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            {/* Search Input */}
            <input
              type="text"
              placeholder="Search patterns..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="form-input"
              style={{ width: '200px', padding: '6px 12px', fontSize: '0.825rem' }}
            />

            {/* Filter Pills */}
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
              {(['ALL', 'AT_RISK', 'IMPROVING', 'ON_TRACK', 'CONSIDER_DROPPING'] as const).map((status) => (
                <button
                  key={status}
                  onClick={() => setStatusFilter(status)}
                  className={`filter-pill ${statusFilter === status ? 'active' : ''}`}
                >
                  {status === 'ALL' ? 'All' : status.replace('_', ' ')} ({statusCounts[status]})
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Table */}
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.875rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)', fontSize: '0.775rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                <th style={{ padding: '12px 14px' }}>DSA Pattern</th>
                <th style={{ padding: '12px 14px' }}>Current Skill</th>
                <th style={{ padding: '12px 14px' }}>Projected Skill</th>
                <th style={{ padding: '12px 14px' }}>Current Retention</th>
                <th style={{ padding: '12px 14px' }}>Projected Retention</th>
                <th style={{ padding: '12px 14px', textAlign: 'right' }}>Status</th>
              </tr>
            </thead>
            <tbody>
              {filteredProjections.map((p, idx) => {
                const curSkillPct = Math.round(p.currentSkill * 100);
                const projSkillPct = Math.round(p.projectedSkill * 100);
                const curRetPct = Math.round(p.currentRetention * 100);
                const projRetPct = Math.round(p.projectedRetention * 100);
                const delta = projSkillPct - curSkillPct;

                return (
                  <tr
                    key={idx}
                    style={{
                      borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                      transition: 'background 0.2s ease'
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                  >
                    <td style={{ padding: '14px', fontWeight: 600, color: '#fff' }}>
                      {p.pattern}
                    </td>

                    {/* Current Skill */}
                    <td style={{ padding: '14px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', width: '36px', color: 'var(--text-muted)' }}>
                          {curSkillPct}%
                        </span>
                        <div className="progress-bar-bg" style={{ width: '70px' }}>
                          <div
                            className="progress-bar-fill"
                            style={{
                              width: `${curSkillPct}%`,
                              background: 'rgba(255, 255, 255, 0.4)'
                            }}
                          />
                        </div>
                      </div>
                    </td>

                    {/* Projected Skill */}
                    <td style={{ padding: '14px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem', fontWeight: 700, width: '36px', color: '#fff' }}>
                          {projSkillPct}%
                        </span>
                        <div className="progress-bar-bg" style={{ width: '70px' }}>
                          <div
                            className="progress-bar-fill"
                            style={{
                              width: `${projSkillPct}%`,
                              background: projSkillPct >= 75 ? 'var(--easy-color)' : projSkillPct >= 50 ? 'var(--accent-cyan)' : 'var(--medium-color)'
                            }}
                          />
                        </div>
                        {delta > 0 && (
                          <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#34d399', fontFamily: 'var(--font-mono)' }}>
                            +{delta}%
                          </span>
                        )}
                      </div>
                    </td>

                    {/* Current Retention */}
                    <td style={{ padding: '14px', fontFamily: 'var(--font-mono)', fontSize: '0.825rem', color: 'var(--text-dim)' }}>
                      {curRetPct}%
                    </td>

                    {/* Projected Retention */}
                    <td style={{ padding: '14px' }}>
                      <span style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '0.825rem',
                        fontWeight: projRetPct >= 65 ? 700 : 500,
                        color: projRetPct >= 65 ? '#34d399' : 'var(--text-muted)'
                      }}>
                        {projRetPct}%
                      </span>
                    </td>

                    {/* Status Badge */}
                    <td style={{ padding: '14px', textAlign: 'right' }}>
                      {getStatusBadge(p.status)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          {filteredProjections.length === 0 && (
            <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              No patterns matching the selected filter.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
export default ForecastPage;
