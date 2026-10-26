package com.preptrack.simulator.engine;

import com.preptrack.simulator.dto.SimulationConfig;
import java.util.List;
import static com.preptrack.simulator.engine.SyntheticLearner.*;

public interface PlannerStrategy {
    String name();
    List<StudyItem> generatePlan(SimulationCatalog catalog, SyntheticLearner learner, SimulationConfig config, int day);
    default void observe(SyntheticLearner learner, Observation observation) {}
}
