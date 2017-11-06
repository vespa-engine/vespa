// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Statistics about deployments on a platform version. This is immutable.
 *
 * @author bratseth
 */
public class DeploymentStatistics {
    
    private final Version version;
    private final ImmutableSet<ApplicationId> failing;
    private final ImmutableSet<ApplicationId> production;
    private final ImmutableSet<ApplicationId> deploying;

    /** DO NOT USE. Public for serialization purposes */
    public DeploymentStatistics(Version version, Collection<ApplicationId> failingApplications,
                                Collection<ApplicationId> production, Collection<ApplicationId> deploying) {
        this.version = version;
        this.failing = ImmutableSet.copyOf(failingApplications);
        this.production = ImmutableSet.copyOf(production);
        this.deploying = ImmutableSet.copyOf(deploying);
    }

    /** Returns a statistics instance with the values as 0 */
    public static DeploymentStatistics empty(Version version) { 
        return new DeploymentStatistics(version, ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of());
    }

    /** Returns the version these statistics are for */
    public Version version() { return version; }
    
    /** 
     * Returns the applications which have at least one job (of any type) which fails on this version, 
     * excluding errors known to not be caused by this version
     */
    public Set<ApplicationId> failing() { return failing; }
    
    /** Returns the applications which have this version in production in at least one zone */
    public Set<ApplicationId> production() { return production; }

    /** Returns the applications which are currently upgrading to this version */
    public Set<ApplicationId> deploying() { return deploying; }

    /** Returns a version of this with the given failing application added */
    public DeploymentStatistics withFailing(ApplicationId application) { 
        return new DeploymentStatistics(version, add(application, failing), production, deploying);
    }

    /** Returns a version of this with the given production application added */
    public DeploymentStatistics withProduction(ApplicationId application) {
        return new DeploymentStatistics(version, failing, add(application, production), deploying);
    }

    /** Returns a version of this with the given deploying application added */
    public DeploymentStatistics withDeploying(ApplicationId application) {
        return new DeploymentStatistics(version, failing, production, add(application, deploying));
    }
    
    private ImmutableSet<ApplicationId> add(ApplicationId application, ImmutableSet<ApplicationId> list) {
        ImmutableSet.Builder<ApplicationId> b = new ImmutableSet.Builder<>();
        b.addAll(list);
        b.add(application);
        return b.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeploymentStatistics)) return false;
        DeploymentStatistics that = (DeploymentStatistics) o;
        return Objects.equals(version, that.version) &&
               Objects.equals(failing, that.failing) &&
               Objects.equals(production, that.production);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, failing, production);
    }

}
