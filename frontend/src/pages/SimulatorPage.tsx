import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../api';
import { SimulationConfig, SimulationMetric, SimulationResult } from '../types/simulator';
import './SimulatorPage.css';

const defaults: SimulationConfig = {
  days: 30, learnerCount: 50, dailyBudgetMinutes: 90, maxNewProblems: 3,
  maxRevisions: 10, targetSkill: 0.8, seed: 42, sheetId: null,
};
const names: Record<string, string> = {
  SHEET_ORDER: 'Sheet order', RANDOM: 'Random + 7-day review', PLAIN_SM2: 'Plain SM-2', PREPTRACK: 'PrepTrack',
  EXPONENTIAL: 'Exponential forgetting', POWER_LAW: 'Power-law forgetting (model mismatch)',
};
const percent = (metric: SimulationMetric) => `${(metric.mean * 100).toFixed(1)}% ± ${(metric.stdDev * 100).toFixed(1)}%`;
const errorMessage = (error: any) => {
  const details = error.response?.data?.errors;
  return details ? Object.values(details).join('. ') : error.response?.data?.message || 'Unable to complete the simulation. Please try again.';
};

export default function SimulatorPage() {
  const [params, setParams] = useSearchParams();
  const runId = params.get('runId');
  const [config, setConfig] = useState<SimulationConfig>(defaults);
  const [result, setResult] = useState<SimulationResult | null>(null);
  const [sheets, setSheets] = useState<{ id: number; name: string }[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    api.get('/sheets/available', { signal: controller.signal }).then(response => setSheets(response.data)).catch(() => {});
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!runId) { setResult(null); return; }
    if (result?.runId === runId) return;
    const controller = new AbortController();
    setBusy(true);
    setError('');
    setResult(null);
    api.get<SimulationResult>(`/simulator/results/${encodeURIComponent(runId)}`, { signal: controller.signal })
      .then(response => { setResult(response.data); setConfig(response.data.config); })
      .catch(err => { if (!controller.signal.aborted) setError(errorMessage(err)); })
      .finally(() => { if (!controller.signal.aborted) setBusy(false); });
    return () => controller.abort();
  }, [runId]);

  const run = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const response = await api.post<SimulationResult>('/simulator/run', config);
      setResult(response.data);
      setParams({ runId: response.data.runId });
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };

  const download = (format: 'json' | 'txt') => {
    if (!result) return;
    const blob = new Blob([format === 'json' ? JSON.stringify(result, null, 2) : result.textSummary],
      { type: format === 'json' ? 'application/json;charset=utf-8' : 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `preptrack-simulation-${result.runId}.${format}`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const numeric = (key: keyof SimulationConfig, label: string, min: number, max: number, step = 1) => (
    <label key={key}>{label}
      <input className="form-input" type="number" required min={min} max={max} step={step}
        value={Number.isFinite(config[key]) ? config[key] : ''}
        onChange={event => setConfig({ ...config, [key]: event.target.valueAsNumber })} />
    </label>
  );

  return (
    <div className="simulator-page">
      <h1>Planner simulator</h1>
      <p>Compare four study strategies with the same learners, daily limits, and problem sheet. Both forgetting curves run together.</p>
      <form onSubmit={run} className="glass-panel simulator-panel">
        <fieldset disabled={busy}>
          <legend>Simulation settings</legend>
          <div className="simulator-fields">
            {numeric('days', 'Days', 1, 60)}
            {numeric('learnerCount', 'Learners', 1, 100)}
            {numeric('dailyBudgetMinutes', 'Minutes per day', 5, 240)}
            {numeric('maxNewProblems', 'New problems per day', 0, 5)}
            {numeric('maxRevisions', 'Reviews per day', 0, 10)}
            {numeric('targetSkill', 'Target skill (0–1)', 0.1, 1, 0.05)}
            {numeric('seed', 'Random seed', 0, Number.MAX_SAFE_INTEGER)}
            <label>Problem sheet
              <select className="form-input" value={config.sheetId ?? ''}
                onChange={event => setConfig({ ...config, sheetId: event.target.value ? Number(event.target.value) : null })}>
                <option value="">NeetCode 150 (default)</option>
                {sheets.map(sheet => <option key={sheet.id} value={sheet.id}>{sheet.name}</option>)}
              </select>
            </label>
          </div>
          <p className="simulator-note">Use the same seed and sheet to reproduce a run. Larger runs may take a little time; oversized requests are rejected.</p>
          <button className="btn btn-primary" type="submit">Run comparison</button>
        </fieldset>
      </form>
      {busy && <p role="status" aria-live="polite">{runId && !result ? 'Loading saved results…' : 'Running all four strategies under both forgetting curves…'}</p>}
      {error && <p role="alert" className="simulator-error">{error}</p>}
      {!result && !busy && !error && <p>No results yet. Run a comparison to see measured retention, coverage, and study time.</p>}
      {result && <section aria-label="Simulation results" className="glass-panel simulator-panel">
        <h2>Day {result.config.days} results</h2>
        <p>{result.config.learnerCount} paired learners · {result.sheetName} · {result.problemCount} problems · {result.patternCount} patterns · seed {result.config.seed}</p>
        <p className="simulator-note">Mean ± sample standard deviation across learners, measured 24 hours after study. Untouched patterns count as zero retention.</p>
        <div className="simulator-actions">
          <button className="btn btn-secondary" onClick={() => download('json')}>Download JSON</button>
          <button className="btn btn-secondary" onClick={() => download('txt')}>Download text report</button>
        </div>
        {['EXPONENTIAL', 'POWER_LAW'].map(curve => <div key={curve}>
          <h3>{names[curve]}</h3>
          <div className="simulator-table-scroll">
            <table>
              <thead><tr><th scope="col">Strategy</th><th scope="col">Retention</th><th scope="col">Retained skill</th><th scope="col">Solved coverage</th><th scope="col">Study minutes</th></tr></thead>
              <tbody>{result.results.filter(row => row.forgettingCurve === curve).map(row => (
                <tr key={row.strategy} className={row.strategy === 'PREPTRACK' ? 'simulator-highlight' : ''}>
                  <th scope="row">{names[row.strategy]}</th><td>{percent(row.retention)}</td>
                  <td>{percent(row.retainedSkill)}</td><td>{percent(row.coverage)}</td>
                  <td>{row.minutesStudied.mean.toFixed(0)} ± {row.minutesStudied.stdDev.toFixed(0)}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <ul>{result.comparisons.filter(row => row.forgettingCurve === curve).map(row => (
            <li key={row.baseline}>PrepTrack vs {names[row.baseline]}: {row.retentionDifference > 0 ? '+' : ''}{(row.retentionDifference * 100).toFixed(2)} percentage points in retention.</li>
          ))}</ul>
        </div>)}
        <details><summary>Assumptions and report caveats</summary>
          <ul>{result.caveats.map(caveat => <li key={caveat}>{caveat}</li>)}</ul>
        </details>
        <p className="simulator-note">Synthetic comparisons do not establish real-world performance. PrepTrack may win or lose under different assumptions.</p>
      </section>}
    </div>
  );
}
