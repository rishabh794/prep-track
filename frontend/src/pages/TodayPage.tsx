import React, { useState, useEffect } from 'react';
import api from '../api';
import {
  DailyPlan,
  PlanItem,
  AttemptOutcome,
  FailureReason,
  CreateAttemptRequest,
  AttemptResponse,
} from '../types';

export default function TodayPage() {
  const [plan, setPlan] = useState<DailyPlan | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Active problem modal & attempt submission state
  const [activeItem, setActiveItem] = useState<PlanItem | null>(null);
  const [activeOutcome, setActiveOutcome] = useState<AttemptOutcome | null>(null);
  const [selectedFailureReason, setSelectedFailureReason] = useState<FailureReason>('PATTERN_GAP');
  const [hintsUsed, setHintsUsed] = useState<number>(0);
  const [elapsedSec, setElapsedSec] = useState<number>(0);
  const [timerRunning, setTimerRunning] = useState<boolean>(false);
  const [notes, setNotes] = useState<string>('');
  const [insightNote, setInsightNote] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);

  // Attempt outcome feedback modal
  const [attemptFeedback, setAttemptFeedback] = useState<AttemptResponse | null>(null);

  // Skip modal state
  const [skipItem, setSkipItem] = useState<PlanItem | null>(null);
  const [skipReason, setSkipReason] = useState<string>('Short on time today');
  const [skipping, setSkipping] = useState<boolean>(false);

  // Expanded breakdown IDs
  const [expandedBreakdown, setExpandedBreakdown] = useState<Record<number, boolean>>({});

  // Regenerate state
  const [regenerating, setRegenerating] = useState<boolean>(false);

  const fetchPlan = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get<DailyPlan>('/plan/today');
      setPlan(res.data);
    } catch (err: any) {
      console.error('Failed to fetch plan', err);
      setError(err.response?.data?.message || 'Failed to load today’s plan');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPlan();
  }, []);

  // Timer effect
  useEffect(() => {
    let interval: any = null;
    if (timerRunning) {
      interval = setInterval(() => {
        setElapsedSec((prev) => prev + 1);
      }, 1000);
    } else if (!timerRunning && elapsedSec !== 0) {
      clearInterval(interval);
    }
    return () => clearInterval(interval);
  }, [timerRunning, elapsedSec]);

  const handleStartProblem = (item: PlanItem) => {
    setActiveItem(item);
    setActiveOutcome(null);
    setElapsedSec(0);
    setHintsUsed(0);
    setNotes('');
    setInsightNote('');
    setTimerRunning(true);
  };

  const handleSelectOutcome = (item: PlanItem, outcome: AttemptOutcome) => {
    setActiveItem(item);
    setActiveOutcome(outcome);
    setTimerRunning(false);

    // If solved independently, submit immediately
    if (outcome === 'SOLVED_INDEPENDENTLY') {
      submitAttempt(item, outcome, null);
    }
  };

  const submitAttempt = async (
    item: PlanItem,
    outcome: AttemptOutcome,
    failureReason: FailureReason | null
  ) => {
    try {
      setSubmitting(true);
      const req: CreateAttemptRequest = {
        problemId: item.problemId,
        outcome,
        failureReason,
        timeTakenSec: elapsedSec > 0 ? elapsedSec : (item.estimatedMinutes * 60),
        hintsUsed,
        isRevision: item.followUpType !== undefined && item.followUpType !== null,
        notes: notes.trim() || undefined,
        insightNote: insightNote.trim() || undefined,
      };

      const res = await api.post<AttemptResponse>('/attempts', req);
      setAttemptFeedback(res.data);

      // Close solving modal
      setActiveItem(null);
      setActiveOutcome(null);

      // Refresh today's plan to show item completed without refilling budget
      await fetchPlan();
    } catch (err: any) {
      console.error('Failed to submit attempt', err);
      alert(err.response?.data?.message || 'Failed to submit attempt');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSkipSubmit = async () => {
    if (!skipItem) return;
    try {
      setSkipping(true);
      await api.patch(`/plan/items/${skipItem.planItemId}/skip`, { reason: skipReason });
      setSkipItem(null);
      await fetchPlan();
    } catch (err: any) {
      console.error('Failed to skip item', err);
      alert(err.response?.data?.message || 'Failed to skip item');
    } finally {
      setSkipping(false);
    }
  };

  const handleRegenerate = async () => {
    if (!window.confirm('Regenerating today’s plan will discard uncompleted items and re-optimize based on current state. Continue?')) {
      return;
    }
    try {
      setRegenerating(true);
      const res = await api.post<DailyPlan>('/plan/today/regenerate');
      setPlan(res.data);
    } catch (err: any) {
      console.error('Failed to regenerate plan', err);
      alert(err.response?.data?.message || 'Failed to regenerate plan');
    } finally {
      setRegenerating(false);
    }
  };

  const toggleBreakdown = (itemId: number) => {
    setExpandedBreakdown((prev) => ({ ...prev, [itemId]: !prev[itemId] }));
  };

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div style={{ maxWidth: '1000px', margin: '40px auto', padding: '0 20px', textAlign: 'center' }}>
        <div style={{ color: 'var(--brand-primary)', fontSize: '2rem', marginBottom: '16px' }}>⏳</div>
        <div style={{ fontSize: '1.2rem', fontWeight: 600 }}>Optimizing today’s knapsack plan...</div>
        <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '6px' }}>
          Querying pattern retention curves, checking upcoming interview topics, and balancing time budget.
        </div>
      </div>
    );
  }

  if (error || !plan) {
    return (
      <div style={{ maxWidth: '800px', margin: '40px auto', padding: '24px', background: 'rgba(239, 68, 68, 0.08)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(239, 68, 68, 0.2)' }}>
        <h3 style={{ color: '#ef4444', marginBottom: '12px' }}>Could not load today’s plan</h3>
        <p style={{ color: 'var(--text-main)', marginBottom: '16px' }}>{error || 'No plan data found.'}</p>
        <button className="btn btn-primary btn-sm" onClick={fetchPlan}>Try Again</button>
      </div>
    );
  }

  const budgetPercent = Math.min(100, Math.round((plan.completedMinutes / Math.max(1, plan.dailyBudgetMinutes)) * 100));
  const totalItemsCount = plan.totalCount || 0;
  const completedItemsCount = plan.completedCount || 0;

  return (
    <div style={{ maxWidth: '1080px', margin: '30px auto', padding: '0 24px' }}>
      {/* Top Header Card */}
      <div style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--border-subtle)',
        borderRadius: 'var(--radius-lg)',
        padding: '24px 30px',
        marginBottom: '28px',
        boxShadow: 'var(--shadow-card)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span style={{ fontSize: '1.6rem' }}>🎯</span>
              <h1 style={{ fontSize: '1.8rem', fontWeight: 800, margin: 0 }}>Today's Study Plan</h1>
              <span className="badge badge-primary" style={{ fontSize: '0.8rem', padding: '3px 10px' }}>
                {plan.date}
              </span>
            </div>
            <p style={{ color: 'var(--text-muted)', margin: '6px 0 0', fontSize: '0.95rem' }}>
              Bounded knapsack schedule tailored to your time budget, pattern retention, and upcoming interviews.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
            <button
              onClick={handleRegenerate}
              disabled={regenerating}
              className="btn btn-secondary btn-sm"
              title="Force re-optimization from fresh state"
              style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
            >
              <span>{regenerating ? '⏳' : '🔄'}</span>
              <span>{regenerating ? 'Re-optimizing...' : 'Regenerate'}</span>
            </button>
          </div>
        </div>

        {/* Overdue alert if applicable */}
        {plan.overdueSummary && plan.overdueSummary.totalOverdue > 0 && (
          <div style={{
            marginTop: '18px',
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm)',
            background: 'rgba(245, 158, 11, 0.1)',
            border: '1px solid rgba(245, 158, 11, 0.25)',
            color: '#fbbf24',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '0.9rem'
          }}>
            <span style={{ fontSize: '1.2rem' }}>⚠️</span>
            <div>
              <strong>Backlog Managed:</strong> {plan.overdueSummary.message}
            </div>
          </div>
        )}

        {/* Progress & Budget Summary */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '20px',
          marginTop: '24px',
          paddingTop: '20px',
          borderTop: '1px solid var(--border-subtle)'
        }}>
          <div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Time Budget Progress
            </div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: '4px', color: 'var(--text-main)' }}>
              {plan.completedMinutes} <span style={{ fontSize: '0.9rem', color: 'var(--text-muted)', fontWeight: 500 }}>/ {plan.dailyBudgetMinutes} min</span>
            </div>
            <div style={{ height: '6px', width: '100%', background: 'rgba(255,255,255,0.08)', borderRadius: '3px', marginTop: '8px', overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${budgetPercent}%`, background: 'var(--gradient-brand)', borderRadius: '3px', transition: 'width 0.4s ease' }} />
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Checklist Status
            </div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: '4px', color: completedItemsCount === totalItemsCount && totalItemsCount > 0 ? '#10b981' : 'var(--text-main)' }}>
              {completedItemsCount} of {totalItemsCount} Done
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              {plan.totalEstimatedMinutes} min planned total
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              No-Treadmill Guarantee
            </div>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '6px', lineHeight: 1.4 }}>
              Completing items marks them done. Budget is finite and does <strong>not</strong> continuously refill.
            </div>
          </div>
        </div>
      </div>

      {/* Sections: RETRY, REVISION, NEW_PROBLEM */}
      {plan.sections.length === 0 ? (
        <div style={{
          textAlign: 'center',
          padding: '50px 20px',
          background: 'var(--bg-card)',
          borderRadius: 'var(--radius-lg)',
          border: '1px solid var(--border-subtle)'
        }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '10px' }}>🎉</div>
          <h3>All Caught Up!</h3>
          <p style={{ color: 'var(--text-muted)', maxWidth: '460px', margin: '0 auto 16px' }}>
            No problems scheduled for today. You can select more sheets in Settings or browse the Explore catalog.
          </p>
        </div>
      ) : (
        plan.sections.map((section) => (
          <div key={section.type} style={{ marginBottom: '32px' }}>
            {/* Section Title */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
              <span style={{
                fontSize: '0.8rem',
                fontWeight: 700,
                letterSpacing: '0.08em',
                textTransform: 'uppercase',
                padding: '4px 10px',
                borderRadius: 'var(--radius-sm)',
                background: section.type === 'RETRY'
                  ? 'rgba(245, 158, 11, 0.15)'
                  : section.type === 'REVISION'
                  ? 'rgba(99, 102, 241, 0.15)'
                  : 'rgba(16, 185, 129, 0.15)',
                color: section.type === 'RETRY'
                  ? '#fbbf24'
                  : section.type === 'REVISION'
                  ? '#818cf8'
                  : '#34d399',
                border: section.type === 'RETRY'
                  ? '1px solid rgba(245, 158, 11, 0.3)'
                  : section.type === 'REVISION'
                  ? '1px solid rgba(99, 102, 241, 0.3)'
                  : '1px solid rgba(16, 185, 129, 0.3)'
              }}>
                {section.type === 'RETRY' && '🔁 Priority Retries'}
                {section.type === 'REVISION' && '🧠 Spaced Revisions'}
                {section.type === 'NEW_PROBLEM' && '✨ Skill Acquisition'}
              </span>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
                ({section.items.length} {section.items.length === 1 ? 'item' : 'items'})
              </span>
            </div>

            {/* Item Cards */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {section.items.map((item) => (
                <div
                  key={item.planItemId}
                  style={{
                    background: item.completed
                      ? 'rgba(16, 185, 129, 0.03)'
                      : item.skipped
                      ? 'rgba(255, 255, 255, 0.02)'
                      : 'var(--bg-card)',
                    border: item.completed
                      ? '1px solid rgba(16, 185, 129, 0.25)'
                      : item.skipped
                      ? '1px solid rgba(255, 255, 255, 0.05)'
                      : '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '20px 24px',
                    boxShadow: 'var(--shadow-card)',
                    opacity: item.skipped ? 0.6 : 1,
                    transition: 'all 0.2s ease'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '14px' }}>
                    <div style={{ flex: 1, minWidth: '280px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                        <span style={{
                          fontSize: '1.15rem',
                          fontWeight: 700,
                          color: item.completed ? '#10b981' : 'var(--text-main)',
                          textDecoration: item.completed ? 'line-through' : 'none'
                        }}>
                          {item.title}
                        </span>

                        <span className={`badge ${
                          item.difficulty === 'EASY' ? 'badge-easy' : item.difficulty === 'MEDIUM' ? 'badge-medium' : 'badge-hard'
                        }`}>
                          {item.difficulty}
                        </span>

                        <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)', fontWeight: 500 }}>
                          ⏱️ ~{item.estimatedMinutes} min
                        </span>

                        {item.completed && (
                          <span className="badge badge-easy" style={{ fontSize: '0.75rem' }}>
                            ✓ Done
                          </span>
                        )}

                        {item.skipped && (
                          <span className="badge" style={{ background: 'rgba(255,255,255,0.1)', color: 'var(--text-dim)' }}>
                            Skipped: {item.skippedReason || 'Skipped'}
                          </span>
                        )}
                      </div>

                      {/* Pattern Tags */}
                      {item.patterns && item.patterns.length > 0 && (
                        <div style={{ display: 'flex', gap: '6px', marginTop: '8px', flexWrap: 'wrap' }}>
                          {item.patterns.map((p, idx) => (
                            <span key={idx} style={{
                              fontSize: '0.75rem',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              background: 'rgba(99, 102, 241, 0.08)',
                              color: '#a5b4fc',
                              border: '1px solid rgba(99, 102, 241, 0.2)'
                            }}>
                              {p}
                            </span>
                          ))}
                        </div>
                      )}

                      {/* Follow-up directive if present */}
                      {item.followUpType && (
                        <div style={{
                          marginTop: '10px',
                          padding: '8px 12px',
                          borderRadius: 'var(--radius-sm)',
                          background: 'rgba(238, 242, 255, 0.05)',
                          borderLeft: '3px solid #6366f1',
                          fontSize: '0.85rem',
                          color: '#c7d2fe'
                        }}>
                          <strong>Target Mode:</strong> {item.followUpDetail || item.followUpType}
                        </div>
                      )}

                      {/* Mathematical Reason string */}
                      <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '10px', lineHeight: 1.4 }}>
                        <span style={{ color: 'var(--text-dim)', fontWeight: 600 }}>Algorithm rationale: </span>
                        {item.reason}
                      </div>
                    </div>

                    {/* Actions on Card */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', alignItems: 'flex-end' }}>
                      {!item.completed && !item.skipped ? (
                        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                          <button
                            onClick={() => handleSelectOutcome(item, 'SOLVED_INDEPENDENTLY')}
                            className="btn btn-sm"
                            style={{ background: 'rgba(16, 185, 129, 0.2)', color: '#34d399', border: '1px solid rgba(16, 185, 129, 0.4)' }}
                            title="Solved without hints or looking at solution"
                          >
                            ✅ Solved
                          </button>

                          <button
                            onClick={() => handleSelectOutcome(item, 'SOLVED_WITH_HELP')}
                            className="btn btn-sm"
                            style={{ background: 'rgba(245, 158, 11, 0.2)', color: '#fbbf24', border: '1px solid rgba(245, 158, 11, 0.4)' }}
                            title="Solved with hints, video, or editorial help"
                          >
                            🟡 With Help
                          </button>

                          <button
                            onClick={() => handleSelectOutcome(item, 'COULD_NOT_SOLVE')}
                            className="btn btn-sm"
                            style={{ background: 'rgba(239, 68, 68, 0.2)', color: '#f87171', border: '1px solid rgba(239, 68, 68, 0.4)' }}
                            title="Could not solve; will resurface as retry tomorrow"
                          >
                            ❌ Couldn't Solve
                          </button>

                          <button
                            onClick={() => setSkipItem(item)}
                            className="btn btn-secondary btn-sm"
                            title="Skip this item today"
                          >
                            Skip
                          </button>
                        </div>
                      ) : (
                        <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                          {item.completed ? 'Completed today' : 'Skipped'}
                        </div>
                      )}

                      {/* Expand math button */}
                      <button
                        onClick={() => toggleBreakdown(item.planItemId)}
                        style={{
                          background: 'none',
                          border: 'none',
                          color: '#818cf8',
                          fontSize: '0.78rem',
                          cursor: 'pointer',
                          padding: '4px',
                          textDecoration: 'underline'
                        }}
                      >
                        {expandedBreakdown[item.planItemId] ? 'Hide math breakdown ▲' : 'View math breakdown ▼'}
                      </button>
                    </div>
                  </div>

                  {/* Expandable Value Breakdown */}
                  {expandedBreakdown[item.planItemId] && item.valueBreakdown && (
                    <div style={{
                      marginTop: '16px',
                      padding: '14px 18px',
                      background: 'rgba(0, 0, 0, 0.2)',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--border-subtle)',
                      fontSize: '0.8rem'
                    }}>
                      <div style={{ fontWeight: 700, marginBottom: '8px', color: '#c7d2fe' }}>
                        Value Function Decomposition (Total Value: {item.valueBreakdown.totalValue})
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px' }}>
                        <div>
                          <strong>Interview Proximity Multiplier:</strong> {item.valueBreakdown.interviewRelevance}x
                        </div>
                        <div>
                          <strong>Retry Boost:</strong> {item.valueBreakdown.retryBoost}x
                        </div>
                        <div>
                          <strong>Follow-Up Multiplier:</strong> {item.valueBreakdown.followUpMultiplier}x
                        </div>
                        <div>
                          <strong>Diversity Multiplier:</strong> {item.valueBreakdown.diversityPenalty}x
                        </div>
                      </div>

                      {item.valueBreakdown.patterns && item.valueBreakdown.patterns.length > 0 && (
                        <div style={{ marginTop: '10px' }}>
                          <strong>Pattern Breakdown:</strong>
                          <table style={{ width: '100%', marginTop: '6px', borderCollapse: 'collapse', fontSize: '0.78rem' }}>
                            <thead>
                              <tr style={{ color: 'var(--text-dim)', textAlign: 'left', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                                <th style={{ padding: '4px 6px' }}>Pattern</th>
                                <th style={{ padding: '4px 6px' }}>Skill</th>
                                <th style={{ padding: '4px 6px' }}>Need</th>
                                <th style={{ padding: '4px 6px' }}>Retention</th>
                                <th style={{ padding: '4px 6px' }}>Weight</th>
                                <th style={{ padding: '4px 6px' }}>Contribution</th>
                              </tr>
                            </thead>
                            <tbody>
                              {item.valueBreakdown.patterns.map((p, pIdx) => (
                                <tr key={pIdx} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                                  <td style={{ padding: '4px 6px', fontWeight: 600 }}>{p.patternName}</td>
                                  <td style={{ padding: '4px 6px' }}>{(p.skill * 100).toFixed(0)}%</td>
                                  <td style={{ padding: '4px 6px' }}>{p.need.toFixed(2)}</td>
                                  <td style={{ padding: '4px 6px' }}>{(p.retention * 100).toFixed(0)}%</td>
                                  <td style={{ padding: '4px 6px' }}>{p.linkWeight.toFixed(1)}</td>
                                  <td style={{ padding: '4px 6px', color: '#34d399', fontWeight: 600 }}>+{p.contribution.toFixed(2)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))
      )}

      {/* Failure Reason & Timing Modal (When outcome is With Help or Couldn't Solve) */}
      {activeItem && activeOutcome && activeOutcome !== 'SOLVED_INDEPENDENTLY' && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          padding: '20px'
        }}>
          <div style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-lg)',
            padding: '30px',
            maxWidth: '540px',
            width: '100%',
            boxShadow: 'var(--shadow-glow)'
          }}>
            <h2 style={{ fontSize: '1.35rem', fontWeight: 700, margin: '0 0 8px' }}>
              Record Failure Diagnosis
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '20px' }}>
              Diagnosing <strong>why</strong> you struggled with <em>{activeItem.title}</em> calibrates future follow-up problem assignments.
            </p>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px' }}>
                Primary Failure Reason:
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {[
                  { id: 'PATTERN_GAP', label: '📌 Didn’t recognize pattern', desc: 'Could not identify the underlying technique. Schedules an easier prerequisite.' },
                  { id: 'IMPLEMENTATION_GAP', label: '🔧 Knew approach, couldn’t implement', desc: 'Understood the algorithm but got stuck in code. Schedules code-from-scratch retry.' },
                  { id: 'EDGE_CASE_MISS', label: '⚠️ Missed edge cases', desc: 'Logic worked mostly but failed boundary inputs. Schedules edge-case check.' },
                  { id: 'TOO_SLOW', label: '🐢 Too slow (brute force only)', desc: 'Solved sub-optimally. Schedules timed retry with complexity target.' },
                ].map((reason) => (
                  <label
                    key={reason.id}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '10px',
                      padding: '10px 14px',
                      borderRadius: 'var(--radius-sm)',
                      background: selectedFailureReason === reason.id ? 'rgba(99, 102, 241, 0.12)' : 'rgba(255, 255, 255, 0.03)',
                      border: selectedFailureReason === reason.id ? '1px solid rgba(99, 102, 241, 0.4)' : '1px solid var(--border-subtle)',
                      cursor: 'pointer'
                    }}
                  >
                    <input
                      type="radio"
                      name="failureReason"
                      value={reason.id}
                      checked={selectedFailureReason === reason.id}
                      onChange={() => setSelectedFailureReason(reason.id as FailureReason)}
                      style={{ marginTop: '3px' }}
                    />
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>{reason.label}</div>
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '2px' }}>{reason.desc}</div>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Optional insight note */}
            <div style={{ marginBottom: '20px' }}>
              <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>
                Key Takeaway / Insight Note (Optional):
              </label>
              <textarea
                value={insightNote}
                onChange={(e) => setInsightNote(e.target.value)}
                placeholder="e.g. Always maintain monotonic decreasing order of heights..."
                rows={2}
                style={{
                  width: '100%',
                  padding: '10px',
                  borderRadius: 'var(--radius-sm)',
                  background: 'var(--bg-dark)',
                  border: '1px solid var(--border-subtle)',
                  color: '#fff',
                  fontSize: '0.85rem'
                }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                onClick={() => { setActiveItem(null); setActiveOutcome(null); }}
                className="btn btn-secondary btn-sm"
                disabled={submitting}
              >
                Cancel
              </button>
              <button
                onClick={() => submitAttempt(activeItem, activeOutcome, selectedFailureReason)}
                className="btn btn-primary btn-sm"
                disabled={submitting}
              >
                {submitting ? 'Saving...' : 'Confirm & Log Attempt'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Attempt Success / Pattern Feedback Modal */}
      {attemptFeedback && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          padding: '20px'
        }}>
          <div style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-lg)',
            padding: '28px',
            maxWidth: '500px',
            width: '100%',
            boxShadow: 'var(--shadow-glow)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
              <span style={{ fontSize: '1.6rem' }}>✅</span>
              <h2 style={{ fontSize: '1.3rem', fontWeight: 700, margin: 0 }}>Attempt Logged!</h2>
            </div>

            <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
              Your problem status has synced across sheets and your pattern memory model has been updated.
            </p>

            {/* Pattern updates list */}
            {attemptFeedback.patternUpdates && attemptFeedback.patternUpdates.length > 0 && (
              <div style={{ marginBottom: '16px', background: 'rgba(255,255,255,0.02)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#c7d2fe', marginBottom: '6px' }}>
                  Pattern Memory Updates:
                </div>
                {attemptFeedback.patternUpdates.map((pu) => (
                  <div key={pu.patternId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginBottom: '4px' }}>
                    <span>{pu.patternName}</span>
                    <span style={{ fontWeight: 600, color: pu.skillAfter >= pu.skillBefore ? '#34d399' : '#f87171' }}>
                      Skill: {(pu.skillBefore * 100).toFixed(0)}% → {(pu.skillAfter * 100).toFixed(0)}%
                    </span>
                  </div>
                ))}
              </div>
            )}

            {/* Scheduled Revision */}
            {attemptFeedback.revisionScheduled && (
              <div style={{ fontSize: '0.85rem', color: 'var(--text-main)', marginBottom: '20px' }}>
                📅 <strong>Next Revision Scheduled:</strong> {attemptFeedback.revisionScheduled.nextReviewDate}
                {attemptFeedback.revisionScheduled.followUpType && (
                  <div>🎯 <strong>Follow-Up Mode:</strong> {attemptFeedback.revisionScheduled.followUpType}</div>
                )}
              </div>
            )}

            <button
              onClick={() => setAttemptFeedback(null)}
              className="btn btn-primary"
              style={{ width: '100%' }}
            >
              Continue Today's Plan
            </button>
          </div>
        </div>
      )}

      {/* Skip Item Modal */}
      {skipItem && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          padding: '20px'
        }}>
          <div style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-lg)',
            padding: '24px',
            maxWidth: '440px',
            width: '100%'
          }}>
            <h3 style={{ fontSize: '1.2rem', marginBottom: '10px' }}>Skip "{skipItem.title}"?</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
              Skipping removes this item from today's plan without deleting the daily plan or refilling the budget.
            </p>

            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px' }}>
              Reason for skipping:
            </label>
            <input
              type="text"
              value={skipReason}
              onChange={(e) => setSkipReason(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 12px',
                borderRadius: 'var(--radius-sm)',
                background: 'var(--bg-dark)',
                border: '1px solid var(--border-subtle)',
                color: '#fff',
                marginBottom: '20px'
              }}
            />

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button onClick={() => setSkipItem(null)} className="btn btn-secondary btn-sm" disabled={skipping}>
                Cancel
              </button>
              <button onClick={handleSkipSubmit} className="btn btn-primary btn-sm" disabled={skipping}>
                {skipping ? 'Skipping...' : 'Confirm Skip'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
