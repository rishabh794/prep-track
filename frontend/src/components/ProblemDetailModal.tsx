import React, { useEffect, useState } from 'react';
import api from '../api';
import { ProblemDetail } from '../types';

interface ProblemDetailModalProps {
  problemId: string | null;
  onClose: () => void;
  onSelectProblem?: (id: string) => void;
}

export default function ProblemDetailModal({ problemId, onClose, onSelectProblem }: ProblemDetailModalProps) {
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!problemId) return;

    let isMounted = true;
    setLoading(true);
    setError(null);

    api.get<ProblemDetail>(`/problems/${problemId}`)
      .then((res) => {
        if (isMounted) {
          setProblem(res.data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.response?.data?.message || 'Failed to load problem details');
          setLoading(false);
        }
      });

    return () => { isMounted = false; };
  }, [problemId]);

  if (!problemId) return null;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(5, 8, 16, 0.75)',
        backdropFilter: 'blur(8px)',
        zIndex: 100,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px'
      }}
      onClick={onClose}
    >
      <div
        className="glass-panel animate-fade-in"
        style={{
          width: '100%',
          maxWidth: '750px',
          maxHeight: '90vh',
          overflowY: 'auto',
          padding: '30px',
          position: 'relative',
          backgroundColor: '#0d1322'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          style={{
            position: 'absolute',
            top: '20px',
            right: '20px',
            background: 'rgba(255, 255, 255, 0.08)',
            border: 'none',
            color: 'var(--text-muted)',
            width: '32px',
            height: '32px',
            borderRadius: '50%',
            cursor: 'pointer',
            fontSize: '1.2rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.2s ease'
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = '#fff')}
          onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-muted)')}
        >
          &times;
        </button>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
            <div style={{ fontSize: '1.1rem', marginBottom: '8px' }}>Loading canonical problem details...</div>
          </div>
        ) : error ? (
          <div style={{ padding: '20px', color: '#f43f5e', background: 'rgba(244, 63, 94, 0.1)', borderRadius: 'var(--radius-md)' }}>
            {error}
          </div>
        ) : problem ? (
          <div>
            {/* Header */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px', flexWrap: 'wrap' }}>
              <span className={`badge badge-${problem.difficulty.toLowerCase()}`}>
                {problem.difficulty}
              </span>
              <h2 style={{ fontSize: '1.6rem', color: '#fff' }}>
                {problem.canonicalTitle}
              </h2>
            </div>

            {/* Optimal Complexity Banner */}
            {problem.optimalComplexity && (
              <div style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '6px 12px',
                background: 'rgba(99, 102, 241, 0.1)',
                border: '1px solid rgba(99, 102, 241, 0.25)',
                borderRadius: 'var(--radius-sm)',
                fontSize: '0.825rem',
                fontFamily: 'var(--font-mono)',
                color: '#a5b4fc',
                marginBottom: '20px'
              }}>
                <span>⚡ Target Complexity:</span>
                <strong>{problem.optimalComplexity}</strong>
              </div>
            )}

            {/* Platform Links */}
            <div style={{ marginBottom: '24px' }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px', textTransform: 'uppercase' }}>
                Available Platforms
              </div>
              <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                {problem.sources?.map((s, idx) => (
                  <a
                    key={idx}
                    href={s.externalUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="btn btn-secondary btn-sm"
                    style={{ textDecoration: 'none' }}
                  >
                    <span>{s.platform === 'LEETCODE' ? 'LeetCode ↗' : 'GeeksforGeeks ↗'}</span>
                    <span style={{ opacity: 0.6 }}>#{s.externalId}</span>
                  </a>
                ))}
              </div>
            </div>

            {/* Editorial Reference Approach */}
            {problem.referenceApproach && (
              <div style={{
                background: 'rgba(15, 23, 42, 0.8)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '16px',
                marginBottom: '24px'
              }}>
                <div style={{
                  fontSize: '0.8rem',
                  fontWeight: 700,
                  color: 'var(--accent-cyan)',
                  marginBottom: '8px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}>
                  <span>💡 REFERENCE EDITORIAL APPROACH</span>
                </div>
                <p style={{ fontSize: '0.925rem', color: '#e2e8f0', lineHeight: 1.6 }}>
                  {problem.referenceApproach}
                </p>
              </div>
            )}

            {/* Pattern Associations */}
            <div style={{ marginBottom: '24px' }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px', textTransform: 'uppercase' }}>
                Associated Patterns (Pattern-Level Memory Model)
              </div>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {problem.patterns?.map((p) => (
                  <span key={p.id} className="badge badge-pattern">
                    {p.name} {p.parentTopic && <span style={{ opacity: 0.6 }}>({p.parentTopic})</span>}
                  </span>
                ))}
              </div>
            </div>

            {/* Problem Relations (Prerequisites & Variants) */}
            {problem.relations && problem.relations.length > 0 && (
              <div>
                <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '10px', textTransform: 'uppercase' }}>
                  Related Learning Graph (Prerequisites & Variants)
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {problem.relations.map((rel, idx) => (
                    <div
                      key={idx}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '10px 14px',
                        background: 'rgba(255, 255, 255, 0.03)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-sm)',
                        cursor: onSelectProblem ? 'pointer' : 'default',
                        transition: 'all 0.2s ease'
                      }}
                      onClick={() => onSelectProblem && onSelectProblem(rel.relatedProblemId)}
                    >
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span
                            className="badge"
                            style={{
                              fontSize: '0.7rem',
                              background: rel.relationType === 'EASIER_PREREQUISITE' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                              color: rel.relationType === 'EASIER_PREREQUISITE' ? '#34d399' : '#a5b4fc',
                              borderColor: rel.relationType === 'EASIER_PREREQUISITE' ? 'rgba(16, 185, 129, 0.3)' : 'rgba(99, 102, 241, 0.3)'
                            }}
                          >
                            {rel.relationType === 'EASIER_PREREQUISITE' ? 'Warmup Prerequisite' : 'Problem Variant'}
                          </span>
                          <strong style={{ fontSize: '0.9rem', color: '#fff' }}>
                            {rel.relatedProblemTitle}
                          </strong>
                        </div>
                        {rel.notes && (
                          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                            {rel.notes}
                          </div>
                        )}
                      </div>
                      {onSelectProblem && (
                        <span style={{ fontSize: '0.8rem', color: 'var(--accent-cyan)' }}>
                          View ➔
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
}
