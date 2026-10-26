import React, { useState, useEffect } from 'react';
import api from '../api';
import { Pattern, ExtractedPattern, FeedbackExtractionResult, Interview } from '../types';

interface InterviewDebriefModalProps {
  interview: Interview;
  onClose: () => void;
  onSuccess: () => void;
}

export default function InterviewDebriefModal({
  interview,
  onClose,
  onSuccess,
}: InterviewDebriefModalProps) {
  const [rawFeedback, setRawFeedback] = useState(interview.rawFeedback || interview.notes || '');
  const [outcome, setOutcome] = useState<string>(interview.outcome || 'PENDING');
  const [extracting, setExtracting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [extractedPatterns, setExtractedPatterns] = useState<ExtractedPattern[]>([]);
  const [availablePatterns, setAvailablePatterns] = useState<Pattern[]>([]);
  const [selectedAddPatternId, setSelectedAddPatternId] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Fetch available patterns for manual tagging fallback
    api.get<Pattern[]>('/patterns')
      .then((res) => setAvailablePatterns(res.data))
      .catch((err) => console.warn('Could not load patterns list', err));
  }, []);

  const handleExtract = async () => {
    if (!rawFeedback.trim()) {
      setError('Please paste your interview notes or questions asked.');
      return;
    }
    setError(null);
    setExtracting(true);
    try {
      const res = await api.post<FeedbackExtractionResult>(
        `/interviews/${interview.id}/feedback`,
        { rawFeedback }
      );
      setExtractedPatterns(res.data.extractedPatterns || []);
      if (res.data.suggestedOutcome) {
        setOutcome(res.data.suggestedOutcome);
      }
    } catch (err: any) {
      console.error('Feedback extraction failed', err);
      setError(err.response?.data?.message || 'Failed to extract topics from feedback.');
    } finally {
      setExtracting(false);
    }
  };

  const handleToggleWeakness = (index: number) => {
    setExtractedPatterns((prev) =>
      prev.map((item, idx) =>
        idx === index ? { ...item, wasWeak: !item.wasWeak } : item
      )
    );
  };

  const handleRemovePattern = (index: number) => {
    setExtractedPatterns((prev) => prev.filter((_, idx) => idx !== index));
  };

  const handleAddManualPattern = () => {
    if (!selectedAddPatternId) return;
    const pat = availablePatterns.find((p) => p.id === Number(selectedAddPatternId));
    if (!pat) return;

    if (extractedPatterns.some((p) => p.patternId === pat.id)) {
      setSelectedAddPatternId('');
      return;
    }

    setExtractedPatterns((prev) => [
      ...prev,
      {
        patternId: pat.id,
        patternName: pat.name,
        patternSlug: pat.slug,
        wasWeak: true,
        confidence: 1.0,
      },
    ]);
    setSelectedAddPatternId('');
  };

  const handleConfirm = async () => {
    setConfirming(true);
    setError(null);
    try {
      await api.post(`/interviews/${interview.id}/confirm-topics`, {
        outcome,
        topics: extractedPatterns.map((p) => ({
          patternId: p.patternId,
          wasWeak: p.wasWeak,
        })),
      });
      onSuccess();
      onClose();
    } catch (err: any) {
      console.error('Confirming topics failed', err);
      setError(err.response?.data?.message || 'Failed to confirm interview topics.');
      setConfirming(false);
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
          maxWidth: '700px',
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
              <span style={{ fontSize: '1.25rem' }}>🎙️</span>
              <h2 style={{ fontSize: '1.35rem', fontWeight: 800, margin: 0, color: '#f8fafc' }}>
                Interview Debrief & Feedback
              </h2>
            </div>
            <div style={{ color: '#cbd5e1', fontSize: '0.92rem' }}>
              <strong>{interview.companyName}</strong> {interview.role ? `• ${interview.role}` : ''} {interview.roundName ? `(${interview.roundName})` : ''} — {interview.interviewDate}
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

        {/* Input Phase: Paste Notes */}
        <div style={{ marginBottom: '20px' }}>
          <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px' }}>
            Interview Notes & Questions Asked:
          </label>
          <textarea
            value={rawFeedback}
            onChange={(e) => setRawFeedback(e.target.value)}
            placeholder="e.g. Asked 2 questions: one on Monotonic Stack (Next Greater Element variant) where I had a bug in the while loop, and another on BFS Level Order which went smoothly..."
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
            disabled={extracting || confirming}
          />

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <label style={{ fontSize: '0.82rem', color: 'var(--text-muted)', fontWeight: 600 }}>Outcome:</label>
              <select
                value={outcome}
                onChange={(e) => setOutcome(e.target.value)}
                style={{
                  padding: '6px 12px',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: 'rgba(15, 23, 42, 0.8)',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  color: '#f8fafc',
                  fontSize: '0.85rem',
                  outline: 'none',
                }}
              >
                <option value="PASSED">Passed</option>
                <option value="FAILED">Failed</option>
                <option value="PENDING">Pending / Partial</option>
                <option value="UPCOMING">Upcoming</option>
              </select>
            </div>

            <button
              onClick={handleExtract}
              className="btn btn-primary btn-sm"
              disabled={extracting || !rawFeedback.trim()}
              style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
            >
              {extracting ? (
                <>
                  <span className="spinner" style={{ width: '12px', height: '12px' }}></span>
                  Extracting...
                </>
              ) : (
                <>✨ Extract Patterns</>
              )}
            </button>
          </div>
        </div>

        {/* Extracted Patterns Section */}
        {extractedPatterns.length > 0 && (
          <div
            className="animate-fade-in"
            style={{
              padding: '18px',
              borderRadius: 'var(--radius-md)',
              background: 'rgba(15, 23, 42, 0.6)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              marginBottom: '20px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <span style={{ fontSize: '0.9rem', fontWeight: 700, color: '#e2e8f0' }}>
                Detected Patterns ({extractedPatterns.length}):
              </span>
              <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                Click tag to toggle Strong / Weak
              </span>
            </div>

            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '16px' }}>
              {extractedPatterns.map((pat, idx) => (
                <div
                  key={idx}
                  onClick={() => handleToggleWeakness(idx)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    padding: '6px 12px',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    userSelect: 'none',
                    transition: 'all 0.2s',
                    background: pat.wasWeak ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                    border: pat.wasWeak
                      ? '1px solid rgba(239, 68, 68, 0.4)'
                      : '1px solid rgba(16, 185, 129, 0.4)',
                    color: pat.wasWeak ? '#fca5a5' : '#86efac',
                  }}
                  title="Click to toggle weakness"
                >
                  <span style={{ fontSize: '0.85rem' }}>{pat.wasWeak ? '⚠️ Struggled' : '✅ Solid'}</span>
                  <span style={{ fontWeight: 600, fontSize: '0.85rem' }}>{pat.patternName}</span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleRemovePattern(idx);
                    }}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'inherit',
                      cursor: 'pointer',
                      padding: '0 2px',
                      fontSize: '0.8rem',
                      opacity: 0.7,
                    }}
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>

            {/* Manual Tagging Fallback */}
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', paddingTop: '10px', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
              <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>Add pattern:</span>
              <select
                value={selectedAddPatternId}
                onChange={(e) => setSelectedAddPatternId(e.target.value ? Number(e.target.value) : '')}
                style={{
                  padding: '4px 8px',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: 'rgba(15, 23, 42, 0.8)',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  color: '#f8fafc',
                  fontSize: '0.8rem',
                  outline: 'none',
                }}
              >
                <option value="">Select pattern...</option>
                {availablePatterns.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
              <button
                onClick={handleAddManualPattern}
                disabled={!selectedAddPatternId}
                className="btn btn-secondary btn-sm"
                style={{ padding: '3px 8px', fontSize: '0.78rem' }}
              >
                + Add
              </button>
            </div>

            {/* Explanatory Callout */}
            <div
              style={{
                marginTop: '14px',
                padding: '10px 14px',
                borderRadius: 'var(--radius-sm)',
                background: 'rgba(99, 102, 241, 0.08)',
                borderLeft: '3px solid #6366f1',
                fontSize: '0.82rem',
                color: '#c7d2fe',
                lineHeight: 1.4,
              }}
            >
              🚀 <strong>Planner Boost:</strong> Each pattern tagged as <em>Struggled</em> receives an immediate <strong>+0.3 priority boost</strong> in your daily study knapsack, focusing your practice where it matters most, decaying naturally over 14 days.
            </div>
          </div>
        )}

        {/* Footer Actions */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button onClick={onClose} className="btn btn-secondary" disabled={confirming}>
            Cancel
          </button>
          <button
            onClick={handleConfirm}
            className="btn btn-primary"
            disabled={confirming || extractedPatterns.length === 0}
            style={{ minWidth: '170px' }}
          >
            {confirming ? 'Saving Boosts...' : 'Confirm Topics & Apply Boost'}
          </button>
        </div>
      </div>
    </div>
  );
}
