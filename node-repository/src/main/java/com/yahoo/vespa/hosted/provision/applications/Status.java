// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import java.util.Objects;

/**
 * An application's status
 *
 * @author bratseth
 */
public class Status {

    private final double currentReadShare;
    private final double maxReadShare;

    /** Do not use */
    public Status(double currentReadShare, double maxReadShare) {
        this.currentReadShare = currentReadShare;
        this.maxReadShare = maxReadShare;
    }

    public Status withCurrentReadShare(double currentReadShare) {
        return new Status(currentReadShare, maxReadShare);
    }

    /**
     * Returns the current fraction of the global traffic to this application that is received by the
     * deployment in this zone.
     */
    public double currentReadShare() { return currentReadShare; }

    public Status withMaxReadShare(double maxReadShare) {
        return new Status(currentReadShare, maxReadShare);
    }

    /**
     * Returns an estimate of the max fraction of the global traffic to this application that may possibly
     * be received by the deployment in this zone.
     */
    public double maxReadShare() { return maxReadShare; }

    public static Status initial() { return new Status(0, 0); }

    @Override
    public int hashCode() {
        return Objects.hash(currentReadShare, maxReadShare);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Status)) return false;
        Status other = (Status)o;
        if ( other.currentReadShare != this.currentReadShare) return false;
        if ( other.maxReadShare != this.maxReadShare) return false;
        return true;
    }

    @Override
    public String toString() {
        return "application status: [" +
               "currentReadShare: " + currentReadShare + ", " +
               "maxReadShare: " + maxReadShare +
               "]";
    }

}
