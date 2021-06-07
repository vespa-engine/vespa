// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.InstanceList;

import java.time.Instant;
import java.time.ZoneOffset;

import static com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;

/**
 * Information about a particular Vespa version.
 * VespaVersions are identified by their version number and ordered by increasing version numbers.
 *
 * This is immutable.
 * 
 * @author bratseth
 */
public class VespaVersion implements Comparable<VespaVersion> {

    private final Version version;
    private final String releaseCommit;
    private final Instant committedAt;
    private final boolean isControllerVersion;
    private final boolean isSystemVersion;
    private final boolean isReleased;
    private final NodeVersions nodeVersions;
    private final Confidence confidence;

    public VespaVersion(Version version, String releaseCommit, Instant committedAt,
                        boolean isControllerVersion, boolean isSystemVersion, boolean isReleased,
                        NodeVersions nodeVersions,
                        Confidence confidence) {
        this.version = version;
        this.releaseCommit = releaseCommit;
        this.committedAt = committedAt;
        this.isControllerVersion = isControllerVersion;
        this.isSystemVersion = isSystemVersion;
        this.isReleased = isReleased;
        this.nodeVersions = nodeVersions;
        this.confidence = confidence;
    }

    public static Confidence confidenceFrom(DeploymentStatistics statistics, Controller controller) {
        InstanceList all = InstanceList.from(controller.jobController().deploymentStatuses(ApplicationList.from(controller.applications().asList())
                                                                                                          .withProductionDeployment()));
        // 'production on this': All production deployment jobs upgrading to this version have completed without failure
        InstanceList productionOnThis = all.matching(instance -> statistics.productionSuccesses().stream().anyMatch(run -> run.id().application().equals(instance)))
                                           .not().failingUpgrade()
                                           .not().upgradingTo(statistics.version());
        InstanceList failingOnThis = all.matching(instance -> statistics.failingUpgrades().stream().anyMatch(run -> run.id().application().equals(instance)));

        // 'broken' if any Canary fails
        if  ( ! failingOnThis.with(UpgradePolicy.canary).isEmpty())
            return Confidence.broken;

        // 'broken' if 4 non-canary was broken by this, and that is at least 10% of all
        if (nonCanaryApplicationsBroken(statistics.version(), failingOnThis, productionOnThis))
            return Confidence.broken;

        // 'low' unless all canary applications are upgraded
        if (productionOnThis.with(UpgradePolicy.canary).size() < all.withProductionDeployment().with(UpgradePolicy.canary).size())
            return Confidence.low;

        // 'high' if 90% of all default upgrade applications upgraded
        if (productionOnThis.with(UpgradePolicy.defaultPolicy).size() >=
            all.withProductionDeployment().with(UpgradePolicy.defaultPolicy).size() * 0.9)
            return Confidence.high;

        return Confidence.normal;
    }

    /** Returns the version number of this Vespa version */
    public Version versionNumber() { return version; }

    /** Returns the sha of the release tag commit for this version in git */
    public String releaseCommit() { return releaseCommit; }
    
    /** Returns the time of the release commit */
    public Instant committedAt() { return committedAt; }
    
    /** Returns whether this is the current version of controllers in this system (the lowest version across all
     * controllers) */
    public boolean isControllerVersion() {
        return isControllerVersion;
    }

    /**
     * Returns whether this is the current version of the infrastructure of the system
     * (i.e the lowest version across all controllers and all config servers in all zones).
     * A goal of the controllers is to eventually (limited by safety and upgrade capacity) drive
     * all applications to this version.
     * 
     * Note that the self version may be higher than the current system version if
     * all config servers are not yet upgraded to the version of the controllers.
     */
    public boolean isSystemVersion() { return isSystemVersion; }

    /** Returns whether the artifacts of this release are available in the configured maven repository. */
    public boolean isReleased() { return isReleased; }

    /** Returns the versions of nodes allocated to system applications (across all zones) */
    public NodeVersions nodeVersions() {
        return nodeVersions;
    }

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

    /** The confidence of a version. */
    public enum Confidence {

        /** This version has been proven defective */
        broken,
        
        /** We don't have sufficient evidence that this version is working */
        low,
        
        /** We have sufficient evidence that this version is working */
        normal,
        
        /** We have overwhelming evidence that this version is working */
        high;
        
        /** Returns true if this confidence is at least as high as the given confidence */
        public boolean equalOrHigherThan(Confidence other) {
            return this.compareTo(other) >= 0;
        }

        /** Returns true if this can be changed to target at given instant */
        public boolean canChangeTo(Confidence target, SystemName system, Instant instant) {
            if (this.equalOrHigherThan(normal)) return true; // Confidence can always change from >= normal
            if (!target.equalOrHigherThan(normal)) return true; // Confidence can always change to < normal

            var hourOfDay = instant.atZone(ZoneOffset.UTC).getHour();
            var dayOfWeek = instant.atZone(ZoneOffset.UTC).getDayOfWeek();
            var hourEnd = system == SystemName.Public ? 13 : 11;
            // Confidence can only be raised between 05:00:00 and 11:59:59Z (13:59:59Z for public), and not during weekends or Friday.
            return    hourOfDay >= 5 && hourOfDay <= hourEnd
                   && dayOfWeek.getValue() < 5;
        }

    }

    private static boolean nonCanaryApplicationsBroken(Version version,
                                                       InstanceList failingOnThis,
                                                       InstanceList productionOnThis) {
        InstanceList failingNonCanaries = failingOnThis.startedFailingOn(version).not().with(UpgradePolicy.canary);
        InstanceList productionNonCanaries = productionOnThis.not().with(UpgradePolicy.canary);

        if (productionNonCanaries.size() + failingNonCanaries.size() == 0) return false;

        // 'broken' if 4 non-canary was broken by this, and that is at least 10% of all
        int brokenByThisVersion = failingNonCanaries.size();
        return brokenByThisVersion >= 4 && brokenByThisVersion >= productionOnThis.size() * 0.1;
     }

}
