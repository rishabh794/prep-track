package com.preptrack.planner.engine;

import com.preptrack.planner.entity.PlanItemType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
public class KnapsackSolver {

    private static final int MAX_CANDIDATES = 200;

    /**
     * Solves bounded 0-1 knapsack with 1-minute resolution across 3 capacity dimensions:
     * 1. Time budget (T minutes)
     * 2. Max review slots (R items - shared by RETRY and REVISION)
     * 3. Max new problem slots (K items - NEW_PROBLEM)
     *
     * Uses a rolling 3D DP value table and a compact BitSet for backtracking to prevent
     * JVM OutOfMemoryError.
     */
    public List<PlanCandidate> solve(
            List<PlanCandidate> candidates,
            int budgetMinutes,
            int maxReviewSlots,
            int maxNew
    ) {
        if (candidates == null || candidates.isEmpty() || budgetMinutes <= 0) {
            return Collections.emptyList();
        }

        // Cap candidates to top 200 to bound DP memory and runtime
        List<PlanCandidate> filteredCandidates = candidates;
        if (filteredCandidates.size() > MAX_CANDIDATES) {
            filteredCandidates = new ArrayList<>(candidates);
            filteredCandidates.sort(Comparator.comparingDouble((PlanCandidate c) ->
                    c.getValue() / Math.max(1, c.getTimeMinutes())).reversed());
            filteredCandidates = filteredCandidates.subList(0, MAX_CANDIDATES);
        }

        int reviewCandidateCount = (int) filteredCandidates.stream()
                .filter(c -> c.getType() == PlanItemType.REVISION || c.getType() == PlanItemType.RETRY)
                .count();
        int newCandidateCount = (int) filteredCandidates.stream()
                .filter(c -> c.getType() == PlanItemType.NEW_PROBLEM)
                .count();

        int T = Math.min(480, Math.max(0, budgetMinutes));
        int R = Math.min(Math.max(0, maxReviewSlots), reviewCandidateCount);
        int K = Math.min(Math.max(0, maxNew), newCandidateCount);
        int n = filteredCandidates.size();

        if (T <= 0 || (R == 0 && K == 0) || n == 0) {
            return Collections.emptyList();
        }

        int statesPerItem = (T + 1) * (R + 1) * (K + 1);
        BitSet picked = new BitSet(n * statesPerItem);
        double[][][] dp = new double[T + 1][R + 1][K + 1];

        for (int i = 0; i < n; i++) {
            PlanCandidate c = filteredCandidates.get(i);
            int timeCost = Math.max(1, c.getTimeMinutes());
            int rCost = (c.getType() == PlanItemType.REVISION || c.getType() == PlanItemType.RETRY) ? 1 : 0;
            int kCost = (c.getType() == PlanItemType.NEW_PROBLEM) ? 1 : 0;
            double val = c.getValue();

            int itemOffset = i * statesPerItem;

            for (int t = T; t >= timeCost; t--) {
                int tOffset = t * (R + 1);
                for (int r = R; r >= rCost; r--) {
                    int trOffset = (tOffset + r) * (K + 1);
                    for (int k = K; k >= kCost; k--) {
                        double withItem = dp[t - timeCost][r - rCost][k - kCost] + val;
                        if (withItem > dp[t][r][k] + 1e-9) {
                            dp[t][r][k] = withItem;
                            picked.set(itemOffset + trOffset + k);
                        }
                    }
                }
            }
        }

        // Backtrack from optimal state
        List<PlanCandidate> selected = new ArrayList<>();
        int curT = T;
        int curR = R;
        int curK = K;

        for (int i = n - 1; i >= 0; i--) {
            PlanCandidate c = filteredCandidates.get(i);
            int timeCost = Math.max(1, c.getTimeMinutes());
            int rCost = (c.getType() == PlanItemType.REVISION || c.getType() == PlanItemType.RETRY) ? 1 : 0;
            int kCost = (c.getType() == PlanItemType.NEW_PROBLEM) ? 1 : 0;

            if (curT >= timeCost && curR >= rCost && curK >= kCost) {
                int bitIdx = i * statesPerItem + ((curT * (R + 1) + curR) * (K + 1) + curK);
                if (picked.get(bitIdx)) {
                    selected.add(c);
                    curT -= timeCost;
                    curR -= rCost;
                    curK -= kCost;
                }
            }
        }

        Collections.reverse(selected);

        // Sort selected items logically: RETRY first, then REVISION, then NEW_PROBLEM
        selected.sort(Comparator.comparingInt(c -> switch (c.getType()) {
            case RETRY -> 0;
            case REVISION -> 1;
            case NEW_PROBLEM -> 2;
        }));

        return selected;
    }
}
