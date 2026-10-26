export type ProblemDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type ProblemStatus = 'UNSOLVED' | 'ATTEMPTED' | 'SOLVED_WITH_HELP' | 'SOLVED_INDEPENDENTLY' | 'NOT_STARTED';

export type AttemptOutcome = 'SOLVED_INDEPENDENTLY' | 'SOLVED_WITH_HELP' | 'COULD_NOT_SOLVE';

export type FailureReason = 'PATTERN_GAP' | 'IMPLEMENTATION_GAP' | 'EDGE_CASE_MISS' | 'TOO_SLOW';

export type FollowUpType = 'EASIER_PREREQUISITE' | 'CODE_FROM_SCRATCH' | 'EDGE_CASE_LIST' | 'TIMED_RETRY';

export type PlanItemType = 'RETRY' | 'REVISION' | 'NEW_PROBLEM';

export interface UserSettings {
  id?: number;
  dailyTimeBudgetMin: number;
  maxNewProblemsDay: number;
  maxRevisionsDay: number;
  targetSkillLevel: number;
  onboardingComplete: boolean;
}

export interface User {
  id: number;
  email: string;
  displayName: string;
  settings?: UserSettings;
}

export interface Pattern {
  id: number;
  name: string;
  slug: string;
  parentTopic: string;
  description?: string;
  difficultyTier?: number;
}

export interface PatternState {
  patternId: number;
  name: string;
  slug: string;
  parentTopic: string;
  skill: number;
  retention: number;
  stability: number;
  lastPracticedAt: string | null;
  totalAttempts: number;
  independentSolves: number;
}

export interface AvailableSheet {
  id: number;
  name: string;
  description: string;
  sourceUrl?: string;
  totalProblems: number;
}

export interface UserSheet {
  id: number;
  sheetId: number;
  sheetName: string;
  description?: string;
  totalProblems: number;
  solvedProblems: number;
  progressPercent: number;
}

export interface SheetProblem {
  id: number;
  canonicalTitle: string;
  difficulty: ProblemDifficulty;
  estimatedMinutes: number;
  category?: string;
  displayOrder: number;
  status: ProblemStatus;
  patterns: string[];
}

export interface PatternContribution {
  patternId?: number;
  patternName: string;
  need: number;
  retention: number;
  skill: number;
  linkWeight: number;
  contribution: number;
  independentSolves?: number;
  totalInSheets?: number;
}

export interface ValueBreakdown {
  patterns: PatternContribution[];
  interviewRelevance: number;
  retryBoost: number;
  followUpMultiplier: number;
  diversityPenalty: number;
  totalValue: number;
}

export interface PlanItem {
  planItemId: number;
  problemId: number;
  title: string;
  difficulty: ProblemDifficulty;
  estimatedMinutes: number;
  patterns: string[];
  followUpType?: FollowUpType | null;
  followUpDetail?: string | null;
  reason: string;
  valueBreakdown?: ValueBreakdown;
  completed: boolean;
  completedAt?: string | null;
  skipped: boolean;
  skippedReason?: string | null;
}

export interface PlanSection {
  type: PlanItemType;
  items: PlanItem[];
}

export interface OverdueSummary {
  totalOverdue: number;
  showingToday: number;
  message: string;
}

export interface DailyPlan {
  planId: number;
  date: string;
  totalEstimatedMinutes: number;
  dailyBudgetMinutes: number;
  completedMinutes: number;
  completedCount: number;
  totalCount: number;
  overdueSummary: OverdueSummary;
  sections: PlanSection[];
}

export interface CreateAttemptRequest {
  problemId: number;
  outcome: AttemptOutcome;
  failureReason?: FailureReason | null;
  timeTakenSec?: number;
  hintsUsed?: number;
  isRevision?: boolean;
  notes?: string;
  insightNote?: string;
}

export interface AttemptResponse {
  attemptId: number;
  statusUpdate: {
    problemId: number;
    newStatus: ProblemStatus;
    sheetsAffected: string[];
  };
  patternUpdates: Array<{
    patternId: number;
    patternName: string;
    skillBefore: number;
    skillAfter: number;
    retentionNow: number;
  }>;
  revisionScheduled?: {
    nextReviewDate: string;
    followUpType?: FollowUpType | null;
  };
  planItemCompleted?: boolean;
}

export interface Interview {
  id: number;
  companyName: string;
  role?: string;
  interviewDate: string;
  outcome?: string;
  roundName?: string;
  notes?: string;
}


export interface ProblemSource {
  platform: 'LEETCODE' | 'GFG' | string;
  externalUrl: string;
  externalId: string;
}

export interface ProblemRelation {
  relatedProblemId: string;
  relatedProblemTitle: string;
  relationType: 'EASIER_PREREQUISITE' | 'HARDER_VARIANT' | string;
  notes?: string;
}

export interface ProblemDetail {
  id: number;
  canonicalTitle: string;
  difficulty: ProblemDifficulty;
  optimalComplexity?: string;
  referenceApproach?: string;
  sources?: ProblemSource[];
  patterns?: Pattern[];
  relations?: ProblemRelation[];
}

export type TriageStatus = 'ON_TRACK' | 'IMPROVING' | 'AT_RISK' | 'CONSIDER_DROPPING';

export interface PatternProjection {
  pattern: string;
  currentSkill: number;
  projectedSkill: number;
  currentRetention: number;
  projectedRetention: number;
  status: TriageStatus;
}

export interface ReadinessForecast {
  interviewId: number;
  interviewDate: string;
  companyName: string;
  daysRemaining: number;
  overallReadiness: number;
  patternProjections: PatternProjection[];
  recommendations: string[];
}

