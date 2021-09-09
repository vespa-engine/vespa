// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.time.Instant;
import java.util.Optional;

/**
 * Store for the application and tester packages.
 *
 * This will replace ArtifactRepository for tenant applications.
 *
 * @author smorgrav
 * @author jonmv
 */
public interface ApplicationStore {

    /** Returns the tenant application package of the given version. */
    byte[] get(DeploymentId deploymentId, ApplicationVersion applicationVersion);

    /** Returns the application package diff, compared to the previous build, for the given tenant, application and build number */
    Optional<byte[]> getDiff(TenantName tenantName, ApplicationName applicationName, long buildNumber);

    /** Removes diffs for packages before the given build number */
    void pruneDiffs(TenantName tenantName, ApplicationName applicationName, long beforeBuildNumber);

    /** Find application package by given build number */
    Optional<byte[]> find(TenantName tenant, ApplicationName application, long buildNumber);

    /** Stores the given tenant application package of the given version and diff since previous version. */
    void put(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion, byte[] applicationPackage, byte[] diff);

    /** Removes applications older than the given version, for the given application, and returns whether something was removed. */
    boolean prune(TenantName tenant, ApplicationName application, ApplicationVersion olderThanVersion);

    /** Removes all application packages for the given application, including any development package. */
    void removeAll(TenantName tenant, ApplicationName application);

    /** Returns the tester application package of the given version. Does NOT contain the services.xml. */
    byte[] getTester(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion);

    /** Stores the given tester application package of the given version. Does NOT contain the services.xml. */
    void putTester(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion, byte[] testerPackage);

    /** Removes tester packages older than the given version, for the given tester, and returns whether something was removed. */
    boolean pruneTesters(TenantName tenant, ApplicationName application, ApplicationVersion olderThanVersion);

    /** Removes all tester packages for the given tester. */
    void removeAllTesters(TenantName tenant, ApplicationName application);

    /** Returns the application package diff, compared to the previous build, for the given deployment and build number */
    Optional<byte[]> getDevDiff(DeploymentId deploymentId, long buildNumber);

    /** Removes diffs for dev packages before the given build number */
    void pruneDevDiffs(DeploymentId deploymentId, long beforeBuildNumber);

    /** Stores the given application package as the development package for the given deployment and version and diff since previous version. */
    void putDev(DeploymentId deploymentId, ApplicationVersion version, byte[] applicationPackage, byte[] diff);

    /** Stores the given application meta data with the current time as part of the path. */
    void putMeta(TenantName tenant, ApplicationName application, Instant now, byte[] metaZip);

    /** Marks the given application as deleted, and eligible for meta data GC at a later time. */
    void putMetaTombstone(TenantName tenant, ApplicationName application, Instant now);

    /** Stores the given manual deployment meta data with the current time as part of the path. */
    void putMeta(DeploymentId id, Instant now, byte[] metaZip);

    /** Marks the given manual deployment as deleted, and eligible for meta data GC at a later time. */
    void putMetaTombstone(DeploymentId id, Instant now);

    /** Prunes meta data such that only what was active at the given instant, and anything newer, is retained. */
    void pruneMeta(Instant oldest);

}
