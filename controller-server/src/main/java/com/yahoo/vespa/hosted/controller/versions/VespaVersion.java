// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/**
 * Information about a particular Vespa version.
 * VespaVersions are identified by their version number and ordered by increasing version numbers.
 *
 * This is immutable.
 * 
 * @author bratseth
 */
public class VespaVersion implements Comparable<VespaVersion> {
    
    private final String releaseCommit;
    private final Instant releasedAt;
    private final boolean isCurrentSystemVersion;
    private final DeploymentStatistics statistics;
    private final Confidence confidence;
    private final ImmutableSet<String> configServerHostnames;

    public VespaVersion(DeploymentStatistics statistics, String releaseCommit, Instant releasedAt, 
                        boolean isCurrentSystemVersion, Collection<String> configServerHostnames,
                        Controller controller) {
        this.statistics = statistics;
        this.releaseCommit = releaseCommit;
        this.releasedAt = releasedAt;
        this.isCurrentSystemVersion = isCurrentSystemVersion;
        this.configServerHostnames = ImmutableSet.copyOf(configServerHostnames);
        this.confidence = deduceConfidenceFrom(statistics, controller, releasedAt);
    }

    private static Confidence deduceConfidenceFrom(DeploymentStatistics statistics, 
                                                   Controller controller, 
                                                   Instant releasedAt) {
        // 'production on this': All deployment jobs upgrading to this version have completed without failure
        ApplicationList productionOnThis = ApplicationList.from(statistics.production(), controller.applications())
                                                          .notUpgradingTo(statistics.version())
                                                          .notFailing();
        ApplicationList failingOnThis = ApplicationList.from(statistics.failing(), controller.applications());
        ApplicationList all = ApplicationList.from(controller.applications().asList())
                .hasDeployment()
                .notPullRequest();

        // 'broken' if any Canary fails
        if  ( ! failingOnThis.with(UpgradePolicy.canary).isEmpty())
            return Confidence.broken;

        // 'broken' if too many non-canary apps fail
        if (nonCanaryApplicationsBroken(failingOnThis, productionOnThis, releasedAt, controller.curator()))
            return Confidence.broken;

        // 'low' unless all canary applications are upgraded
        if (productionOnThis.with(UpgradePolicy.canary).size() < all.with(UpgradePolicy.canary).size())
            return Confidence.low;

        // 'high' if 90% of all default upgrade applications upgraded
        if (productionOnThis.with(UpgradePolicy.defaultPolicy).size() >=
            all.with(UpgradePolicy.defaultPolicy).size() * 0.9)
            return Confidence.high;

        return Confidence.normal;
    }

    /** Returns the version number of this Vespa version */
    public Version versionNumber() { return statistics.version(); }

    /** Returns the sha of the release tag commit for this version in git */
    public String releaseCommit() { return releaseCommit; }
    
    /** Returns the time of the release commit */
    public Instant releasedAt() { return releasedAt; }
    
    /** Statistics about deployment of this version */
    public DeploymentStatistics statistics() { return statistics; }

    /** Returns whether this is the version currently running on this controller */
    public boolean isSelfVersion() { return versionNumber().equals(Vtag.currentVersion); }

    /**
     * Returns whether this is the current version of the infrastructure of the system
     * (i.e the lowest version across this controller and all config servers in all zones).
     * A goal of the controller is to eventually (limited by safety and upgrade capacity) drive 
     * all applications to this version.
     * 
     * Note that the self version may be higher than the current system version if
     * all config servers are not yet upgraded to the version of this controller.
     */
    public boolean isCurrentSystemVersion() { return isCurrentSystemVersion; }

    /** Returns the host names of the config servers (across all zones) which are currently of this version */
    public Set<String> configServerHostnames() { return configServerHostnames; }
    
    /** Returns the confidence we have in this versions suitability for production */
    public Confidence confidence() { return confidence; }

    @Override
    public int compareTo(VespaVersion other) {
        return this.versionNumber().compareTo(other.versionNumber());
    }
    
    @Override
    public int hashCode() { return versionNumber().hashCode(); }
    
    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof VespaVersion)) return false;
        return ((VespaVersion)other).versionNumber().equals(this.versionNumber());
    }

    public enum Confidence {

        /** This version has been proven defective */
        broken,
        
        /** We don't have sufficient evidence that this version is working */
        low,
        
        /** We have sufficient evidence that this version is working */
        normal,
        
        /** We have overwhelming evidence that this version is working */
        high

    }

    private static boolean nonCanaryApplicationsBroken(ApplicationList failingOnThis,
                                                       ApplicationList productionOnThis,
                                                       Instant releasedAt,
                                                       CuratorDb curator) {
        int failing = failingOnThis.without(UpgradePolicy.canary).startedFailingAfter(releasedAt).size();
        int production = productionOnThis.without(UpgradePolicy.canary).size();
        int all = production + failing;

        if (all == 0) return false;

        int applicationsGivingMinConfidence = curator.readApplicationsGivingMinConfidence();
        int applicationsGivingMaxConfidence = curator.readApplicationsGivingMaxConfidence();
        double failureRatioAtMaxConfidence = curator.readFailureRatioAtMaxConfidence();

        double confidence = (double) (all - applicationsGivingMinConfidence) /
                (double) (applicationsGivingMaxConfidence - applicationsGivingMinConfidence);
        if (confidence > 1.0) confidence = 1.0;
        if (confidence < 0.0) confidence = 0.0;

        double failureToleranceRatio = 1 - confidence * (1 - failureRatioAtMaxConfidence);
        double brokenRatio = (double) failing / (double) all;

        return (brokenRatio > failureToleranceRatio);
    }

}
