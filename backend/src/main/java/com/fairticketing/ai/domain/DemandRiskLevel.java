package com.fairticketing.ai.domain;

/**
 * How oversubscribed an upcoming event is expected to be. HIGH is the
 * threshold that turns the waiting room on.
 */
public enum DemandRiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static DemandRiskLevel fromRatio(double demandRatio) {
        if (demandRatio >= 1.0) {
            return HIGH;
        }
        if (demandRatio >= 0.7) {
            return MEDIUM;
        }
        return LOW;
    }

    public boolean shouldOpenWaitingRoom() {
        return this == HIGH;
    }
}
