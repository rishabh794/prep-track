import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import ProblemDetailModal from '../components/ProblemDetailModal';
import { useAuth } from '../context/AuthContext';

export default function SheetsPage() {
  const { user } = useAuth();
  const [userSheets, setUserSheets] = useState([]);
  const [selectedSheetId, setSelectedSheetId] = useState(null);
  const [problems, setProblems] = useState([]);
  const [loadingSheets, setLoadingSheets] = useState(true);
  const [loadingProblems, setLoadingProblems] = useState(false);
  const [selectedProblemId, setSelectedProblemId] = useState(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState('ALL');

  useEffect(() => {
    setLoadingSheets(true);
    api.get('/user/sheets')
      .then((res) => {
        setUserSheets(res.data);
        if (res.data.length > 0) {
          setSelectedSheetId(res.data[0].sheetId);
        }
      })
      .catch((err) => console.error(err))
      .finally(() => setLoadingSheets(false));
  }, []);

  useEffect(() => {
    if (!selectedSheetId) return;

    setLoadingProblems(true);
    api.get(`/user/sheets/${selectedSheetId}/problems`)
      .then((res) => {
        setProblems(res.data);
      })
      .catch((err) => console.error(err))
      .finally(() => setLoadingProblems(false));
  }, [selectedSheetId]);

  const filteredProblems = problems.filter((p) => {
    const matchesSearch = p.canonicalTitle.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.category?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.patterns?.some((pat) => pat.name.toLowerCase().includes(searchQuery.toLowerCase()));

    const matchesDifficulty = difficultyFilter === 'ALL' || p.difficulty === difficultyFilter;
    return matchesSearch && matchesDifficulty;
  });

  if (loadingSheets) {
    return (
      <div style={{ maxWidth: '1280px', margin: '60px auto', textAlign: 'center', color: 'var(--text-muted)' }}>
        Loading your preparation sheets...
      </div>
    );
  }

  if (userSheets.length === 0) {
    return (
      <div style={{ maxWidth: '700px', margin: '80px auto', textAlign: 'center', padding: '0 20px' }}>
        <div className="glass-panel" style={{ padding: '48px 32px' }}>
          <div style={{ fontSize: '3rem', marginBottom: '16px' }}>📚</div>
          <h2 style={{ fontSize: '1.8rem', color: '#fff', marginBottom: '8px' }}>
            No Sheets Selected Yet
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginBottom: '28px' }}>
            To begin, select your target sheets in the onboarding wizard. PrepTrack will automatically deduplicate shared questions across LeetCode and GFG.
          </p>
          <Link to="/onboarding" className="btn btn-primary" style={{ padding: '12px 28px' }}>
            Start Setup & Pick Sheets ➔
          </Link>
        </div>
      </div>
    );
  }

  const currentSheet = userSheets.find((s) => s.sheetId === selectedSheetId);

  return (
    <div style={{ maxWidth: '1280px', margin: '30px auto', padding: '0 24px' }}>
      {/* Top Banner with Sheet Tabs */}
      <div style={{ marginBottom: '28px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '14px' }}>
          <div>
            <h1 style={{ fontSize: '1.85rem', color: '#fff', marginBottom: '4px' }}>
              My Preparation Catalog
            </h1>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
              Multi-sheet deduplicated problems with canonical pattern linkages
            </p>
          </div>
          <Link to="/onboarding" className="btn btn-secondary btn-sm">
            ⚙️ Adjust Sheets & Budget
          </Link>
        </div>

        {/* Sheet Tabs */}
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          {userSheets.map((s) => {
            const active = s.sheetId === selectedSheetId;
            return (
              <button
                key={s.sheetId}
                onClick={() => setSelectedSheetId(s.sheetId)}
                style={{
                  padding: '12px 20px',
                  borderRadius: 'var(--radius-md)',
                  background: active ? 'rgba(99, 102, 241, 0.2)' : 'rgba(255, 255, 255, 0.03)',
                  border: `1.5px solid ${active ? 'var(--accent-primary)' : 'var(--border-subtle)'}`,
                  color: active ? '#fff' : 'var(--text-muted)',
                  fontWeight: 600,
                  fontSize: '0.925rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  transition: 'all 0.2s ease'
                }}
              >
                <span>{s.sheetName}</span>
                <span style={{
                  padding: '2px 8px',
                  borderRadius: '10px',
                  background: active ? 'var(--accent-primary)' : 'rgba(255, 255, 255, 0.08)',
                  fontSize: '0.75rem',
                  color: '#fff'
                }}>
                  {s.totalProblems}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="glass-panel" style={{ padding: '16px 20px', marginBottom: '24px', display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ flex: '1 1 280px' }}>
          <input
            type="text"
            className="form-input"
            placeholder="Search problems, categories, or patterns..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)', fontWeight: 600 }}>DIFFICULTY:</span>
          {['ALL', 'EASY', 'MEDIUM', 'HARD'].map((diff) => (
            <button
              key={diff}
              onClick={() => setDifficultyFilter(diff)}
              style={{
                padding: '6px 12px',
                borderRadius: 'var(--radius-sm)',
                fontSize: '0.8rem',
                fontWeight: 600,
                border: 'none',
                cursor: 'pointer',
                background: difficultyFilter === diff ? 'var(--accent-primary)' : 'rgba(255, 255, 255, 0.05)',
                color: difficultyFilter === diff ? '#fff' : 'var(--text-muted)'
              }}
            >
              {diff}
            </button>
          ))}
        </div>
      </div>

      {/* Problems List */}
      {loadingProblems ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
          Loading sheet questions...
        </div>
      ) : filteredProblems.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
          No problems match your current filter.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {filteredProblems.map((prob) => (
            <div
              key={prob.id}
              className="glass-panel"
              style={{
                padding: '16px 20px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '16px',
                cursor: 'pointer',
                transition: 'all 0.2s ease'
              }}
              onClick={() => setSelectedProblemId(prob.id)}
              onMouseEnter={(e) => e.currentTarget.style.transform = 'translateX(4px)'}
              onMouseLeave={(e) => e.currentTarget.style.transform = 'translateX(0)'}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: 1 }}>
                <span style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.825rem',
                  color: 'var(--text-dim)',
                  minWidth: '32px'
                }}>
                  #{prob.displayOrder || prob.id}
                </span>

                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px' }}>
                    <span className={`badge badge-${prob.difficulty.toLowerCase()}`}>
                      {prob.difficulty}
                    </span>
                    <strong style={{ fontSize: '0.975rem', color: '#fff' }}>
                      {prob.canonicalTitle}
                    </strong>
                    {prob.category && (
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                        • {prob.category}
                      </span>
                    )}
                  </div>

                  {/* Patterns */}
                  <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                    {prob.patterns?.map((pat) => (
                      <span key={pat.id} className="badge badge-pattern" style={{ fontSize: '0.7rem' }}>
                        {pat.name}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <button
                className="btn btn-secondary btn-sm"
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedProblemId(prob.id);
                }}
              >
                Inspect ➔
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Problem Detail Modal */}
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
