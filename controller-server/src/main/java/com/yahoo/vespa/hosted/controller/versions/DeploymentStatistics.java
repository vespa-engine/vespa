// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;

import java.util.List;
import java.util.Objects;

/**
 * Statistics about deployments on a platform version. This is immutable.
 *
 * @author bratseth
 */
public class DeploymentStatistics {
    
    private final Version version;
    private final ImmutableList<ApplicationId> failing;
    private final ImmutableList<ApplicationId> production;

    /** DO NOT USE. Public for serialization purposes */
    public DeploymentStatistics(Version version, List<ApplicationId> failingApplications,
                                List<ApplicationId> production) {
        this.version = version;
        this.failing = ImmutableList.copyOf(failingApplications);
        this.production = ImmutableList.copyOf(production);
    }

    /** Returns a statistics instance with the values as 0 */
    public static DeploymentStatistics empty(Version version) { 
        return new DeploymentStatistics(version, ImmutableList.of(), ImmutableList.of()); 
    }

    /** Returns the version these statistics are for */
    public Version version() { return version; }
    
    /** 
     * Returns the applications which have at least one job (of any type) which fails on this version, 
     * excluding errors known to not be caused by this version
     */
    public List<ApplicationId> failing() { return failing; }
    
    /** Returns the applications which have this version in production in at least one zone */
    public List<ApplicationId> production() { return production; }
    
    /** Returns a version of this with the given failing application added */
    public DeploymentStatistics withFailing(ApplicationId application) { 
        return new DeploymentStatistics(version, add(application, failing), production);
    }

    /** Returns a version of this with the given production application added */
    public DeploymentStatistics withProduction(ApplicationId application) {
        return new DeploymentStatistics(version, failing, add(application, production));
    }
    
    private ImmutableList<ApplicationId> add(ApplicationId application, ImmutableList<ApplicationId> list) {
        ImmutableList.Builder<ApplicationId> b = new ImmutableList.Builder<>();
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
