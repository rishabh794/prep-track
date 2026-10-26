import React, { useState } from 'react';
import api from '../api';
import { RecallGrade, RecallCheckResponse } from '../types';

interface RecallModalProps {
  problemId: number;
  problemTitle: string;
  difficulty?: string;
  patterns?: string[];
  onClose: () => void;
  onSuccess: () => void;
}

export default function RecallModal({
  problemId,
  problemTitle,
  difficulty,
  patterns,
  onClose,
  onSuccess,
}: RecallModalProps) {
  const [userApproach, setUserApproach] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [result, setResult] = useState<RecallCheckResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleGradeRecall = async () => {
    if (!userApproach.trim() || userApproach.trim().length < 5) {
      setError('Please write at least a sentence explaining your approach.');
      return;
    }
    setError(null);
    setEvaluating(true);
    try {
      const res = await api.post<RecallCheckResponse>(
        `/revisions/problem/${problemId}/recall`,
        { userApproach }
      );
      setResult(res.data);
    } catch (err: any) {
      console.error('Recall grading failed', err);
      setError(err.response?.data?.message || 'Failed to grade recall attempt.');
    } finally {
      setEvaluating(false);
    }
  };

  const handleFinalizeReview = async (override?: RecallGrade) => {
    if (!result) return;
    setSubmitting(true);
    setError(null);
    try {
      await api.post(`/revisions/problem/${problemId}/review`, {
        recallCheckId: result.recallCheckId,
        userOverride: override || result.aiAssessment,
      });
      onSuccess();
      onClose();
    } catch (err: any) {
      console.error('Finalizing review failed', err);
      setError(err.response?.data?.message || 'Failed to complete revision.');
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(5, 8, 16, 0.8)',
        backdropFilter: 'blur(8px)',
        zIndex: 110,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
      }}
      onClick={onClose}
    >
      <div
        className="glass-panel animate-fade-in"
        style={{
          width: '100%',
          maxWidth: '650px',
          maxHeight: '90vh',
          overflowY: 'auto',
          padding: '28px',
          borderRadius: 'var(--radius-lg)',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 20px rgba(99, 102, 241, 0.2)',
          border: '1px solid rgba(255, 255, 255, 0.1)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
              <span style={{ fontSize: '1.2rem' }}>🧠</span>
              <h2 style={{ fontSize: '1.35rem', fontWeight: 800, margin: 0, color: '#f8fafc' }}>
                Active Recall Check
              </h2>
              <span
                style={{
                  fontSize: '0.72rem',
                  padding: '2px 8px',
                  borderRadius: '999px',
                  background: 'rgba(99, 102, 241, 0.15)',
                  color: '#a5b4fc',
                  border: '1px solid rgba(99, 102, 241, 0.3)',
                  fontWeight: 600,
                }}
              >
                ~5 Min Check
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
              <span style={{ fontWeight: 700, fontSize: '1.05rem', color: '#e2e8f0' }}>{problemTitle}</span>
              {difficulty && (
                <span
                  className={`badge ${
                    difficulty === 'EASY'
                      ? 'badge-easy'
                      : difficulty === 'MEDIUM'
                      ? 'badge-medium'
                      : 'badge-hard'
                  }`}
                >
                  {difficulty}
                </span>
              )}
            </div>
          </div>
          <button
            onClick={onClose}
            className="btn btn-secondary btn-sm"
            style={{ padding: '4px 10px', fontSize: '1rem', lineHeight: 1 }}
          >
            ✕
          </button>
        </div>

        {/* Pattern tags */}
        {patterns && patterns.length > 0 && (
          <div style={{ display: 'flex', gap: '6px', marginBottom: '18px', flexWrap: 'wrap' }}>
            {patterns.map((p, idx) => (
              <span
                key={idx}
                style={{
                  fontSize: '0.75rem',
                  padding: '2px 8px',
                  borderRadius: '4px',
                  background: 'rgba(99, 102, 241, 0.08)',
                  color: '#a5b4fc',
                  border: '1px solid rgba(99, 102, 241, 0.2)',
                }}
              >
                {p}
              </span>
            ))}
          </div>
        )}

        {/* Instructions */}
        <div
          style={{
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm)',
            background: 'rgba(99, 102, 241, 0.07)',
            borderLeft: '4px solid #6366f1',
            marginBottom: '20px',
            fontSize: '0.88rem',
            color: '#cbd5e1',
            lineHeight: 1.5,
          }}
        >
          <strong>The Recall Rule:</strong> Explain your approach in 2–3 sentences before viewing the solution. AI grades your understanding against the ground-truth reference approach.
        </div>

        {error && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 'var(--radius-sm)',
              background: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              color: '#f87171',
              fontSize: '0.85rem',
              marginBottom: '16px',
            }}
          >
            {error}
          </div>
        )}

        {/* Input Phase */}
        {!result ? (
          <div>
            <label
              style={{
                display: 'block',
                fontSize: '0.85rem',
                fontWeight: 600,
                color: 'var(--text-muted)',
                marginBottom: '8px',
              }}
            >
              Your Solution Approach:
            </label>
            <textarea
              value={userApproach}
              onChange={(e) => setUserApproach(e.target.value)}
              placeholder="e.g. Use a monotonic stack of indices. For each bar, pop while stack top is shorter. Calculate width as current index minus stack top minus 1..."
              rows={5}
              style={{
                width: '100%',
                padding: '12px 14px',
                borderRadius: 'var(--radius-md)',
                backgroundColor: 'rgba(15, 23, 42, 0.7)',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                color: '#f8fafc',
                fontSize: '0.9rem',
                fontFamily: 'inherit',
                resize: 'vertical',
                outline: 'none',
                boxSizing: 'border-box',
                lineHeight: 1.5,
              }}
              disabled={evaluating}
            />
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginTop: '6px',
                fontSize: '0.78rem',
                color: 'var(--text-dim)',
              }}
            >
              <span>Aim for core invariant, data structure, and complexity</span>
              <span>{userApproach.length} chars</span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
              <button onClick={onClose} className="btn btn-secondary" disabled={evaluating}>
                Cancel
              </button>
              <button
                onClick={handleGradeRecall}
                className="btn btn-primary"
                disabled={evaluating || userApproach.trim().length === 0}
                style={{
                  minWidth: '160px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                }}
              >
                {evaluating ? (
                  <>
                    <span className="spinner" style={{ width: '14px', height: '14px' }}></span>
                    Evaluating...
                  </>
                ) : (
                  <>✨ Grade with AI</>
                )}
              </button>
            </div>
          </div>
        ) : (
          /* Result & Confirmation Phase */
          <div className="animate-fade-in">
            {/* Grade Badge Banner */}
            <div
              style={{
                padding: '16px 20px',
                borderRadius: 'var(--radius-md)',
                background:
                  result.aiAssessment === 'PASS'
                    ? 'rgba(16, 185, 129, 0.12)'
                    : result.aiAssessment === 'PARTIAL'
                    ? 'rgba(245, 158, 11, 0.12)'
                    : 'rgba(239, 68, 68, 0.12)',
                border:
                  result.aiAssessment === 'PASS'
                    ? '1px solid rgba(16, 185, 129, 0.35)'
                    : result.aiAssessment === 'PARTIAL'
                    ? '1px solid rgba(245, 158, 11, 0.35)'
                    : '1px solid rgba(239, 68, 68, 0.35)',
                marginBottom: '18px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
                <span style={{ fontSize: '1.6rem' }}>
                  {result.aiAssessment === 'PASS'
                    ? '✅'
                    : result.aiAssessment === 'PARTIAL'
                    ? '🟡'
                    : '❌'}
                </span>
                <div>
                  <div
                    style={{
                      fontWeight: 800,
                      fontSize: '1.15rem',
                      color:
                        result.aiAssessment === 'PASS'
                          ? '#34d399'
                          : result.aiAssessment === 'PARTIAL'
                          ? '#fbbf24'
                          : '#f87171',
                    }}
                  >
                    AI Grade: {result.aiAssessment}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                    {result.aiAssessment === 'PASS'
                      ? 'Stability grows ×1.5 • Interval expands'
                      : result.aiAssessment === 'PARTIAL'
                      ? 'Stability maintained • Small skill boost'
                      : 'Stability reduced ×0.5 • Rescheduled tomorrow'}
                  </div>
                </div>
              </div>

              <div
                style={{
                  fontSize: '0.9rem',
                  color: '#e2e8f0',
                  lineHeight: 1.5,
                  padding: '10px 14px',
                  background: 'rgba(0,0,0,0.2)',
                  borderRadius: 'var(--radius-sm)',
                }}
              >
                <strong style={{ color: 'var(--text-muted)' }}>Feedback: </strong>
                {result.aiReasoning}
              </div>
            </div>

            {/* User Approach Review */}
            <div
              style={{
                fontSize: '0.82rem',
                color: 'var(--text-dim)',
                marginBottom: '20px',
                padding: '8px 12px',
                background: 'rgba(255,255,255,0.03)',
                borderRadius: 'var(--radius-sm)',
              }}
            >
              <strong>Your submitted recall: </strong>"{userApproach}"
            </div>

            {/* Action Buttons */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
                paddingTop: '12px',
                borderTop: '1px solid rgba(255, 255, 255, 0.1)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-muted)' }}>
                  Confirm outcome:
                </span>
                <button
                  onClick={() => handleFinalizeReview()}
                  className="btn btn-primary"
                  disabled={submitting}
                  style={{ minWidth: '150px' }}
                >
                  {submitting ? 'Saving...' : `Accept Grade (${result.aiAssessment})`}
                </button>
              </div>

              {result.canOverride && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    justifyContent: 'flex-end',
                    flexWrap: 'wrap',
                  }}
                >
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>Or override:</span>
                  <button
                    onClick={() => handleFinalizeReview('PASS')}
                    disabled={submitting}
                    className="btn btn-sm"
                    style={{
                      background: 'rgba(16, 185, 129, 0.15)',
                      color: '#34d399',
                      border: '1px solid rgba(16, 185, 129, 0.3)',
                    }}
                  >
                    Override: PASS
                  </button>
                  <button
                    onClick={() => handleFinalizeReview('PARTIAL')}
                    disabled={submitting}
                    className="btn btn-sm"
                    style={{
                      background: 'rgba(245, 158, 11, 0.15)',
                      color: '#fbbf24',
                      border: '1px solid rgba(245, 158, 11, 0.3)',
                    }}
                  >
                    Override: PARTIAL
                  </button>
                  <button
                    onClick={() => handleFinalizeReview('FAIL')}
                    disabled={submitting}
                    className="btn btn-sm"
                    style={{
                      background: 'rgba(239, 68, 68, 0.15)',
                      color: '#f87171',
                      border: '1px solid rgba(239, 68, 68, 0.3)',
                    }}
                  >
                    Override: FAIL
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
