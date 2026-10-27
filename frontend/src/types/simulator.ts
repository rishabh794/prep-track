export interface SimulationConfig {
  days: number;
  learnerCount: number;
  dailyBudgetMinutes: number;
  maxNewProblems: number;
  maxRevisions: number;
  targetSkill: number;
  seed: number;
  sheetId: number | null;
}

export interface SimulationMetric { mean: number; stdDev: number }
export interface SimulationResult {
  runId: string;
  createdAt: string;
  modelVersion: string;
  config: SimulationConfig;
  sheetName: string;
  problemCount: number;
  patternCount: number;
  results: {
    strategy: string;
    forgettingCurve: string;
    retention: SimulationMetric;
    retainedSkill: SimulationMetric;
    coverage: SimulationMetric;
    minutesStudied: SimulationMetric;
    dailySnapshots: { day: number; retention: SimulationMetric; retainedSkill: SimulationMetric }[];
  }[];
  comparisons: {
    forgettingCurve: string;
    baseline: string;
    retentionDifference: number;
    pairedDifferenceStdDev: number;
    prepTrackHigher: boolean;
  }[];
  caveats: string[];
  enginePolicy?: { policyVersion: string; [key: string]: unknown } | null;
  textSummary: string;
}
