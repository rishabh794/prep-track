package com.preptrack.simulator.engine;

import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.problem.entity.Problem;
import java.util.List;
import java.util.Map;

/** Detached, ordered catalog shared by all strategies. No learner data comes from real users. */
public record SimulationCatalog(String sheetName, List<Problem> problems,
                                List<Pattern> patterns, Map<Long, List<ProblemPattern>> links) {}
