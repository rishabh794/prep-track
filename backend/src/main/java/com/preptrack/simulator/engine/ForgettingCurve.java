package com.preptrack.simulator.engine;

public interface ForgettingCurve {
    double compute(double daysSincePractice, double stability);
    String name();
}
