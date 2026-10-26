package com.preptrack.planner.engine;

import com.preptrack.planner.dto.PatternContribution;
import com.preptrack.planner.dto.ValueBreakdown;
import com.preptrack.planner.entity.PlanItemType;
import org.springframework.stereotype.Component;

@Component
public class ReasonFormatter {

    public String format(PlanCandidate item, double targetSkillLevel) {
        ValueBreakdown breakdown = item.getValueBreakdown();
        PatternContribution top = breakdown != null ? breakdown.getTopContributor() : null;

        StringBuilder reason = new StringBuilder();

        if (item.getType() == PlanItemType.RETRY) {
            reason.append("Couldn't solve yesterday — ");
        }

        if (top != null) {
            reason.append(top.getPatternName()).append(": ");

            if (item.getType() == PlanItemType.NEW_PROBLEM) {
                reason.append(top.getIndependentSolves())
                        .append(" of ")
                        .append(Math.max(1, top.getTotalInSheets()))
                        .append(" solved independently");
                double gap = targetSkillLevel - (top.getSkill() != null ? top.getSkill() : 0.0);
                if (gap > 0.3) {
                    reason.append(String.format(" — skill gap %.0f%%", gap * 100));
                }
            } else {
                if (top.getSkill() != null && top.getSkill() >= targetSkillLevel) {
                    reason.append("maintenance revision (skill above target)");
                } else {
                    reason.append("need=").append(String.format("%.2f", top.getNeed() != null ? top.getNeed() : 0.0));
                }
                reason.append(", retention=").append(String.format("%.0f%%", (top.getRetention() != null ? top.getRetention() : 0.0) * 100));
            }
        } else {
            reason.append(item.getProblem() != null ? item.getProblem().getCanonicalTitle() : "Problem");
        }

        if (item.getInterviewDays() != null && item.getInterviewDays() <= 14) {
            reason.append(". ")
                    .append(item.getInterviewCompany() != null ? item.getInterviewCompany() : "Interview")
                    .append(" interview in ")
                    .append(item.getInterviewDays())
                    .append(" days");
        }

        if (item.getFollowUpType() != null) {
            switch (item.getFollowUpType()) {
                case EASIER_PREREQUISITE ->
                        reason.append(". Follow-up: ")
                                .append(item.getFollowUpDetail() != null ? item.getFollowUpDetail() : "Try prerequisite first");
                case CODE_FROM_SCRATCH ->
                        reason.append(". Follow-up: code from scratch, no hints");
                case EDGE_CASE_LIST ->
                        reason.append(". Follow-up: list edge cases before coding");
                case TIMED_RETRY -> {
                    String target = (item.getProblem() != null && item.getProblem().getOptimalComplexity() != null)
                            ? item.getProblem().getOptimalComplexity()
                            : "optimal complexity";
                    reason.append(". Follow-up: timed retry, target ").append(target);
                }
            }
        }

        return reason.toString();
    }
}
