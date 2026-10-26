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

  const fetchForecast = async () => {
    if (!interviewId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<ReadinessForecast>(`/forecast/interviews/${interviewId}`);
      setForecast(res.data);
    } catch (err: any) {
      console.error('Failed to load forecast', err);
      setError(err.response?.data?.message || 'Failed to load readiness forecast. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchForecast();
  }, [interviewId]);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
          <p className="text-slate-400 font-medium">Forward-simulating preparation timeline...</p>
        </div>
      </div>
    );
  }

  if (error || !forecast) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6">
        <div className="bg-slate-900 border border-rose-800/60 rounded-xl p-8 max-w-md w-full text-center">
          <div className="w-12 h-12 bg-rose-950/70 text-rose-400 border border-rose-800 rounded-full flex items-center justify-center mx-auto mb-4 text-xl font-bold">
            !
          </div>
          <h2 className="text-xl font-bold text-white mb-2">Unable to Load Forecast</h2>
          <p className="text-slate-400 text-sm mb-6">{error || 'Interview forecast could not be found.'}</p>
          <div className="flex justify-center gap-3">
            <Link
              to="/interviews"
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium rounded-lg transition-colors"
            >
              Back to Interviews
            </Link>
            <button
              onClick={fetchForecast}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-lg transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  const overallPercent = Math.round((forecast.overallReadiness || 0) * 100);
  const readinessColor =
    overallPercent >= 75
      ? 'text-emerald-400 border-emerald-500'
      : overallPercent >= 50
      ? 'text-amber-400 border-amber-500'
      : 'text-rose-400 border-rose-500';

  const readinessBg =
    overallPercent >= 75
      ? 'from-emerald-500/10 to-teal-500/5'
      : overallPercent >= 50
      ? 'from-amber-500/10 to-yellow-500/5'
      : 'from-rose-500/10 to-orange-500/5';

  const filteredProjections = forecast.patternProjections.filter((p) => {
    if (statusFilter === 'ALL') return true;
    return p.status === statusFilter;
  });

  const statusCounts = {
    ALL: forecast.patternProjections.length,
    ON_TRACK: forecast.patternProjections.filter((p) => p.status === 'ON_TRACK').length,
    IMPROVING: forecast.patternProjections.filter((p) => p.status === 'IMPROVING').length,
    AT_RISK: forecast.patternProjections.filter((p) => p.status === 'AT_RISK').length,
    CONSIDER_DROPPING: forecast.patternProjections.filter((p) => p.status === 'CONSIDER_DROPPING').length,
  };

  const getStatusBadge = (status: TriageStatus) => {
    switch (status) {
      case 'ON_TRACK':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-950/80 text-emerald-300 border border-emerald-700/60">
            <span>✅</span> ON TRACK
          </span>
        );
      case 'IMPROVING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-sky-950/80 text-sky-300 border border-sky-700/60">
            <span>📈</span> IMPROVING
          </span>
        );
      case 'AT_RISK':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-950/80 text-amber-300 border border-amber-700/60">
            <span>⚠️</span> AT RISK
          </span>
        );
      case 'CONSIDER_DROPPING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-900 text-slate-400 border border-slate-700">
            <span>💤</span> CONSIDER DROPPING
          </span>
        );
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10 font-sans">
      <div className="max-w-6xl mx-auto space-y-8">
        {/* Navigation & Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <Link
              to="/interviews"
              className="inline-flex items-center gap-1 text-sm text-slate-400 hover:text-indigo-400 font-medium mb-2 transition-colors"
            >
              ← Back to Upcoming Interviews
            </Link>
            <div className="flex items-center gap-3">
              <h1 className="text-3xl font-extrabold tracking-tight text-white">
                Readiness Forecast — {forecast.companyName}
              </h1>
              <span className="px-3 py-1 bg-indigo-950/70 text-indigo-300 border border-indigo-700/50 rounded-full text-xs font-semibold">
                {forecast.daysRemaining} days left
              </span>
            </div>
            <p className="text-slate-400 text-sm mt-1">
              Interview Date: <span className="text-slate-200 font-medium">{forecast.interviewDate}</span>
            </p>
          </div>

          <button
            onClick={fetchForecast}
            className="self-start md:self-auto px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-sm font-semibold rounded-lg transition-colors flex items-center gap-2"
          >
            <span>⟳</span> Re-simulate
          </button>
        </div>

        {/* Top Summary: Readiness Gauge & Recommendations Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Readiness Gauge Card */}
          <div className={`bg-gradient-to-br ${readinessBg} bg-slate-900/90 border border-slate-800 rounded-2xl p-6 flex flex-col justify-between shadow-xl`}>
            <div>
              <span className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Overall Interview Readiness
              </span>
              <div className="mt-6 flex items-center justify-center">
                <div className={`relative w-36 h-36 rounded-full border-8 ${readinessColor} flex flex-col items-center justify-center bg-slate-950/80 shadow-inner`}>
                  <span className="text-4xl font-extrabold tracking-tight text-white">{overallPercent}%</span>
                  <span className="text-xs uppercase font-semibold text-slate-400 mt-0.5">Coverage</span>
                </div>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-800/80 text-xs text-slate-400 space-y-1">
              <p>• Day-by-day simulated knapsack planner</p>
              <p>• Memory retention decay & company pattern relevance</p>
            </div>
          </div>

          {/* Actionable Recommendations Panel */}
          <div className="lg:col-span-2 bg-slate-900/90 border border-slate-800 rounded-2xl p-6 shadow-xl">
            <div className="flex items-center gap-2 mb-4">
              <span className="text-lg">🎯</span>
              <h2 className="text-base font-bold uppercase tracking-wider text-slate-200">
                Strategic Triage & Action Items
              </h2>
            </div>

            <div className="space-y-3">
              {forecast.recommendations.map((rec, idx) => (
                <div
                  key={idx}
                  className="p-4 rounded-xl bg-slate-950/60 border border-slate-800/80 flex items-start gap-3 hover:border-slate-700 transition-colors"
                >
                  <span className="flex-shrink-0 w-6 h-6 rounded-full bg-indigo-950 text-indigo-400 border border-indigo-700/60 flex items-center justify-center text-xs font-bold">
                    {idx + 1}
                  </span>
                  <p className="text-sm font-medium text-slate-200 leading-relaxed">{rec}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Pattern Projections Section */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold text-white">Pattern Projections</h2>
              <p className="text-slate-400 text-xs mt-0.5">
                Current mastery vs projected skill and retention on interview day ({forecast.interviewDate})
              </p>
            </div>

            {/* Filter Pills */}
            <div className="flex flex-wrap gap-2">
              {(['ALL', 'AT_RISK', 'IMPROVING', 'ON_TRACK', 'CONSIDER_DROPPING'] as const).map((status) => (
                <button
                  key={status}
                  onClick={() => setStatusFilter(status)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    statusFilter === status
                      ? 'bg-indigo-600 text-white shadow-md'
                      : 'bg-slate-800/80 text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                  }`}
                >
                  {status.replace('_', ' ')} ({statusCounts[status]})
                </button>
              ))}
            </div>
          </div>

          {/* Projections Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-800 text-slate-400 text-xs uppercase font-semibold tracking-wider">
                  <th className="py-3 px-4">Pattern</th>
                  <th className="py-3 px-4">Current Skill</th>
                  <th className="py-3 px-4">Projected Skill</th>
                  <th className="py-3 px-4">Current Retention</th>
                  <th className="py-3 px-4">Projected Retention</th>
                  <th className="py-3 px-4 text-right">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-sm">
                {filteredProjections.map((p, idx) => {
                  const curSkillPct = Math.round(p.currentSkill * 100);
                  const projSkillPct = Math.round(p.projectedSkill * 100);
                  const curRetPct = Math.round(p.currentRetention * 100);
                  const projRetPct = Math.round(p.projectedRetention * 100);
                  const skillDelta = projSkillPct - curSkillPct;

                  return (
                    <tr key={idx} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-3.5 px-4 font-semibold text-slate-100">{p.pattern}</td>
                      <td className="py-3.5 px-4 text-slate-300">
                        <div className="flex items-center gap-2">
                          <span className="w-10 font-mono text-xs">{curSkillPct}%</span>
                          <div className="w-16 bg-slate-800 h-1.5 rounded-full overflow-hidden">
                            <div className="bg-slate-400 h-full rounded-full" style={{ width: `${curSkillPct}%` }} />
                          </div>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-2">
                          <span className="w-10 font-mono text-xs font-bold text-white">{projSkillPct}%</span>
                          <div className="w-16 bg-slate-800 h-1.5 rounded-full overflow-hidden">
                            <div
                              className={`h-full rounded-full ${
                                projSkillPct >= 75 ? 'bg-emerald-400' : projSkillPct >= 50 ? 'bg-sky-400' : 'bg-amber-400'
                              }`}
                              style={{ width: `${projSkillPct}%` }}
                            />
                          </div>
                          {skillDelta > 0 && (
                            <span className="text-[11px] font-semibold text-emerald-400">+{skillDelta}%</span>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 font-mono text-xs text-slate-400">{curRetPct}%</td>
                      <td className="py-3.5 px-4 font-mono text-xs text-slate-300">
                        <span className={projRetPct >= 70 ? 'text-emerald-400 font-bold' : 'text-slate-400'}>
                          {projRetPct}%
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-right">{getStatusBadge(p.status)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {filteredProjections.length === 0 && (
              <div className="text-center py-10 text-slate-500 text-sm">
                No patterns matching status filter <span className="font-semibold">{statusFilter}</span>.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
