package com.preptrack.simulator.engine;

public final class PowerLawForgetting implements ForgettingCurve {
    public double compute(double t, double stability) {
        return t <= 0 ? 1.0 : Math.pow(1.0 + t / Math.max(0.01, stability), -0.5);
    }
    public String name() { return "POWER_LAW"; }
}
