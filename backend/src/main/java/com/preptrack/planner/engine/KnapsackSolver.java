package com.preptrack.planner.engine;

import com.preptrack.planner.entity.PlanItemType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

        int T = Math.min(360, budgetMinutes);
        int R = Math.max(0, maxReviewSlots);
        int K = Math.max(0, maxNew);
        int n = filteredCandidates.size();

        double[][][][] dp = new double[n + 1][T + 1][R + 1][K + 1];

        for (int i = 0; i < n; i++) {
            PlanCandidate c = filteredCandidates.get(i);
            int timeCost = c.getTimeMinutes();
            int rCost = (c.getType() == PlanItemType.REVISION || c.getType() == PlanItemType.RETRY) ? 1 : 0;
            int kCost = (c.getType() == PlanItemType.NEW_PROBLEM) ? 1 : 0;

            for (int t = 0; t <= T; t++) {
                for (int r = 0; r <= R; r++) {
                    for (int k = 0; k <= K; k++) {
                        // Do not pick item i
                        double maxVal = dp[i][t][r][k];

                        // Pick item i if within remaining capacity
                        if (t >= timeCost && r >= rCost && k >= kCost) {
                            double withItem = dp[i][t - timeCost][r - rCost][k - kCost] + c.getValue();
                            if (withItem > maxVal) {
                                maxVal = withItem;
                            }
                        }
                        dp[i + 1][t][r][k] = maxVal;
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
            int timeCost = c.getTimeMinutes();
            int rCost = (c.getType() == PlanItemType.REVISION || c.getType() == PlanItemType.RETRY) ? 1 : 0;
            int kCost = (c.getType() == PlanItemType.NEW_PROBLEM) ? 1 : 0;

            if (curT >= timeCost && curR >= rCost && curK >= kCost) {
                double valWithItem = dp[i][curT - timeCost][curR - rCost][curK - kCost] + c.getValue();
                if (Math.abs(dp[i + 1][curT][curR][curK] - valWithItem) < 1e-6 &&
                        dp[i + 1][curT][curR][curK] > dp[i][curT][curR][curK]) {
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
