import React, { useState, useEffect } from 'react';
import api from '../api';
import ProblemDetailModal from '../components/ProblemDetailModal';
import { AvailableSheet, ProblemDifficulty } from '../types';

interface SheetProblemItem {
  id: number;
  canonicalTitle: string;
  difficulty: ProblemDifficulty;
  category?: string;
}

export default function ExploreSheetsPage() {
  const [sheets, setSheets] = useState<AvailableSheet[]>([]);
  const [selectedSheetId, setSelectedSheetId] = useState<number | null>(null);
  const [problems, setProblems] = useState<SheetProblemItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [selectedProblemId, setSelectedProblemId] = useState<string | null>(null);

  useEffect(() => {
    api.get<AvailableSheet[]>('/sheets/available')
      .then((res) => {
        setSheets(res.data);
        if (res.data.length > 0) {
          setSelectedSheetId(res.data[0].id);
        }
      })
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!selectedSheetId) return;

    api.get<SheetProblemItem[]>(`/user/sheets/${selectedSheetId}/problems`)
      .then((res) => setProblems(res.data))
      .catch((err) => console.error(err));
  }, [selectedSheetId]);

  return (
    <div style={{ maxWidth: '1280px', margin: '40px auto', padding: '0 24px' }}>
      <div style={{ marginBottom: '32px' }}>
        <h1 style={{ fontSize: '2rem', color: '#fff', marginBottom: '8px' }}>
          Explore Curated DSA Sheets
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
          Browse available coding sheets with canonical cross-platform mapping and pattern tags
        </p>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
          Loading sheet catalog...
        </div>
      ) : (
        <>
          {/* Sheet Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px', marginBottom: '36px' }}>
            {sheets.map((s) => {
              const active = s.id === selectedSheetId;
              return (
                <div
                  key={s.id}
                  className="glass-panel"
                  onClick={() => setSelectedSheetId(s.id)}
                  style={{
                    padding: '24px',
                    cursor: 'pointer',
                    background: active ? 'rgba(99, 102, 241, 0.15)' : 'var(--bg-card)',
                    border: `1.5px solid ${active ? 'var(--accent-primary)' : 'var(--border-subtle)'}`,
                    transition: 'all 0.25s ease'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <h3 style={{ fontSize: '1.25rem', color: '#fff' }}>{s.name}</h3>
                    <span className="badge badge-pattern" style={{ color: 'var(--accent-cyan)' }}>
                      {s.totalProblems} Problems
                    </span>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '16px', lineHeight: 1.5 }}>
                    {s.description}
                  </p>
                  <div style={{ fontSize: '0.825rem', fontWeight: 600, color: active ? 'var(--accent-primary)' : 'var(--text-dim)' }}>
                    {active ? '● Viewing Sheet Below' : 'Click to preview questions ➔'}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Sheet Questions Preview */}
          <div className="glass-panel" style={{ padding: '28px' }}>
            <h2 style={{ fontSize: '1.3rem', color: '#fff', marginBottom: '20px' }}>
              Questions in {sheets.find((s) => s.id === selectedSheetId)?.name || 'Selected Sheet'}
            </h2>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {problems.map((p) => (
                <div
                  key={p.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '12px 16px',
                    borderRadius: 'var(--radius-sm)',
                    background: 'rgba(255, 255, 255, 0.02)',
                    border: '1px solid var(--border-subtle)',
                    cursor: 'pointer'
                  }}
                  onClick={() => setSelectedProblemId(p.id.toString())}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span className={`badge badge-${p.difficulty.toLowerCase()}`}>
                      {p.difficulty}
                    </span>
                    <strong style={{ color: '#fff', fontSize: '0.925rem' }}>{p.canonicalTitle}</strong>
                    {p.category && (
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>• {p.category}</span>
                    )}
                  </div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--accent-cyan)' }}>Inspect ➔</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {selectedProblemId && (
        <ProblemDetailModal
          problemId={selectedProblemId}
          onClose={() => setSelectedProblemId(null)}
          onSelectProblem={(id) => setSelectedProblemId(id)}
        />
      )}
    </div>
  );
}
