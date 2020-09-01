// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.time.Instant;
import java.util.Collection;
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
    byte[] get(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion);

    /** Find application package by given build number */
    Optional<byte[]> find(TenantName tenant, ApplicationName application, long buildNumber);

    /** Stores the given tenant application package of the given version. */
    void put(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion, byte[] applicationPackage);

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

    /** Stores the given application package as the development package for the given application and zone. */
    void putDev(ApplicationId application, ZoneId zone, byte[] applicationPackage);

    /** Returns the development package for the given application and zone. */
    byte[] getDev(ApplicationId application, ZoneId zone);

    /** Stores the given application meta data with the current time as part of the path. */
    void putMeta(TenantName tenant, ApplicationName application, Instant now, byte[] metaZip);

    /** Marks the given application as deleted, and eligible for meta data GC at a later time. */
    void putMetaTombstone(TenantName tenant, ApplicationName application, Instant now);

    /** Prunes meta data such that only what was active at the given instant, and anything newer, is retained. */
    void pruneMeta(Instant oldest);

}
