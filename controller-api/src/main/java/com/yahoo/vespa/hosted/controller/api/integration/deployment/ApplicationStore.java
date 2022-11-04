// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Store for the application and test packages, diffs, and other metadata.
 *
 * @author smorgrav
 * @author jonmv
 */
public interface ApplicationStore {

    /** Returns the application package of the given revision. */
    default byte[] get(DeploymentId deploymentId, RevisionId revisionId) {
        try (InputStream stream = stream(deploymentId, revisionId)) {
            return stream.readAllBytes();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InputStream stream(DeploymentId deploymentId, RevisionId revisionId);

    /** Returns the application package diff, compared to the previous build, for the given tenant, application and build number */
    Optional<byte[]> getDiff(TenantName tenantName, ApplicationName applicationName, long buildNumber);

    /** Removes diffs for packages before the given build number */
    void pruneDiffs(TenantName tenantName, ApplicationName applicationName, long beforeBuildNumber);

    /** Find prod application package by given build number */
    Optional<byte[]> find(TenantName tenant, ApplicationName application, long buildNumber);

    /** Whether the prod application package with the given number is stored. */
    default boolean hasBuild(TenantName tenant, ApplicationName application, long buildNumber) {
        return find(tenant, application, buildNumber).isPresent();
    }

    /** Stores the given tenant application and test packages of the given revision, and diff since previous version. */
    void put(TenantName tenant, ApplicationName application, RevisionId revision, byte[] applicationPackage, byte[] testPackage, byte[] diff);

    /** Removes application and test packages older than the given revision, for the given application. */
    void prune(TenantName tenant, ApplicationName application, RevisionId revision);

    /** Removes all application and test packages for the given application, including any development package. */
    void removeAll(TenantName tenant, ApplicationName application);

    /** Returns the tester application package of the given revision. Does NOT contain the services.xml. */
    default byte[] getTester(TenantName tenant, ApplicationName application, RevisionId revision) {
        try (InputStream stream = streamTester(tenant, application, revision)) {
            return stream.readAllBytes();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InputStream streamTester(TenantName tenantName, ApplicationName applicationName, RevisionId revision);

    /** Returns the application package diff, compared to the previous build, for the given deployment and build number */
    Optional<byte[]> getDevDiff(DeploymentId deploymentId, long buildNumber);

    /** Removes diffs for dev packages before the given build number */
    void pruneDevDiffs(DeploymentId deploymentId, long beforeBuildNumber);

    /** Stores the given application package as the development package for the given deployment and revision and diff since previous version. */
    void putDev(DeploymentId deploymentId, RevisionId revision, byte[] applicationPackage, byte[] diff);

    /** Stores the given application metadata with the current time as part of the path. */
    void putMeta(TenantName tenant, ApplicationName application, Instant now, byte[] metaZip);

    /** Marks the given application as deleted, and eligible for metadata GC at a later time. */
    void putMetaTombstone(TenantName tenant, ApplicationName application, Instant now);

    /** Stores the given manual deployment metadata with the current time as part of the path. */
    void putMeta(DeploymentId id, Instant now, byte[] metaZip);

    /** Marks the given manual deployment as deleted, and eligible for metadata GC at a later time. */
    void putMetaTombstone(DeploymentId id, Instant now);

    /** Prunes metadata such that only what was active at the given instant, and anything newer, is retained. */
    void pruneMeta(Instant oldest);

}
