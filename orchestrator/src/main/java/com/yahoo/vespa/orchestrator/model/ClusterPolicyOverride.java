// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.orchestrator.policy.SuspensionLimit;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * @author hakonhall
 */
public record ClusterPolicyOverride(int deployedSize, OptionalInt expectedSize, OptionalInt allowedDown, OptionalDouble allowedDownRatio) {
    public ClusterPolicyOverride {
        if (deployedSize <= 0)
            throw new IllegalArgumentException("deployedSize must be positive");

        if (expectedSize.isPresent() && expectedSize.getAsInt() <= 0)
            throw new IllegalArgumentException("expectedSize must be positive");

        if (allowedDown.isPresent()) {
            if (allowedDown.getAsInt() <= 0)
                throw new IllegalArgumentException("allowedDown must be positive: " + allowedDown.getAsInt());
            if (expectedSize.isPresent() && allowedDown.getAsInt() > expectedSize.getAsInt())
                throw new IllegalArgumentException("allowedDown must be less than or equal to expectedSize (" + expectedSize.getAsInt() +
                                                   "): " + allowedDown.getAsInt());
        }

        if (allowedDownRatio.isPresent() && (allowedDownRatio.getAsDouble() < 0.0 || allowedDownRatio.getAsDouble() > 1.0))
            throw new IllegalArgumentException("allowedDownRatio must be between 0.0 and 1.0: " + allowedDownRatio.getAsDouble());

    }

    public static ClusterPolicyOverride fromDeployedSize(int deployedSize) {
        return new ClusterPolicyOverride(deployedSize, OptionalInt.empty(), OptionalInt.empty(), OptionalDouble.empty());
    }

    public Optional<SuspensionLimit> getSuspensionLimit() {
        return allowedDown.isPresent() || allowedDownRatio.isPresent() ?
               Optional.of(new SuspensionLimit(allowedDown.orElse(0), allowedDownRatio.orElse(0.0))) :
               Optional.empty();
    }

    public OptionalInt allowedDownPercentage() {
        return allowedDownRatio.isPresent() ?
               OptionalInt.of((int) Math.round(allowedDownRatio.getAsDouble() * 100.0)) :
               OptionalInt.empty();
    }

}
