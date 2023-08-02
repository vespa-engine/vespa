// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

/**
 * @author hakonhall
 *
 * @param allowedDown      the maximum number of services (nodes) that are allowed to be down.
 * @param allowedDownRatio the maximum ratio of services (nodes) that are allowed to be down.
 */
public record SuspensionLimit(int allowedDown, double allowedDownRatio) {
    public SuspensionLimit {
        if (allowedDown < 0)
            throw new IllegalArgumentException("allowedDown cannot be negative: " + allowedDown);
        if (allowedDownRatio < 0.0 || allowedDownRatio > 1.0)
            throw new IllegalArgumentException("allowedDownRatio must be between 0.0 and 1.0: " + allowedDownRatio);
    }

    public static SuspensionLimit fromAllowedDown(int allowedDown) {
        return new SuspensionLimit(allowedDown, 0);
    }

    public static SuspensionLimit fromAllowedDownRatio(double allowedDownRatio) {
        return new SuspensionLimit(0, allowedDownRatio);
    }

    public int allowedDownPercentage() {
        return (int) Math.round(allowedDownRatio * 100.0);
    }
}
