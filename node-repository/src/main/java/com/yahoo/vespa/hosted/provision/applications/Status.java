// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import java.util.Objects;

/**
 * An application's status
 *
 * @author bratseth
 */
public class Status {

    private final double currentTrafficFraction;
    private final double maxTrafficFraction;

    /** Do not use */
    public Status(double currentTrafficFraction, double maxTrafficFraction) {
        this.currentTrafficFraction = currentTrafficFraction;
        this.maxTrafficFraction = maxTrafficFraction;
    }

    public Status withCurrentTrafficFraction(double currentTrafficFraction) {
        return new Status(currentTrafficFraction, maxTrafficFraction);
    }

    /**
     * Returns the current fraction of the global traffic to this application that is received by the
     * deployment in this zone.
     */
    public double currentTrafficFraction() { return currentTrafficFraction; }

    public Status withMaxTrafficFraction(double maxTrafficFraction) {
        return new Status(currentTrafficFraction, maxTrafficFraction);
    }

    /**
     * Returns an estimate of the max fraction of the global traffic to this application that may possibly
     * be received by the deployment in this zone.
     */
    public double maxTrafficFraction() { return maxTrafficFraction; }

    public static Status initial() { return new Status(0, 0); }

    @Override
    public int hashCode() {
        return Objects.hash(currentTrafficFraction, maxTrafficFraction);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Status)) return false;
        Status other = (Status)o;
        if ( other.currentTrafficFraction != this.currentTrafficFraction) return false;
        if ( other.maxTrafficFraction != this.maxTrafficFraction) return false;
        return true;
    }

    @Override
    public String toString() {
        return "application status: [" +
               "currentTrafficFraction: " + currentTrafficFraction + ", " +
               "maxTrafficFraction: " + maxTrafficFraction +
               "]";
    }

}
