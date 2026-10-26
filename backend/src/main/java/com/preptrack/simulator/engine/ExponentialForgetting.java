package com.preptrack.simulator.engine;

public final class ExponentialForgetting implements ForgettingCurve {
    public double compute(double t, double stability) {
        return t <= 0 ? 1.0 : Math.exp(-t / Math.max(0.01, stability));
    }
    public String name() { return "EXPONENTIAL"; }
}
