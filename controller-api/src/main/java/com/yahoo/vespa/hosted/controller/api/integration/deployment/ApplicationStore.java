// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;
/**
 * Store for the application package and application tester package.
 *
 * This interface will take over most of the responsibility from the ArtifactRepository with time.
 */
public interface ApplicationStore {

    /** Returns the tenant application package of the given version. */
    byte[] getApplicationPackage(ApplicationId application, ApplicationVersion applicationVersion);

    /** Stores the given tenant application package of the given version. */
    void putApplicationPackage(ApplicationId application, ApplicationVersion applicationVersion, byte[] applicationPackage);

    /** Stores the given tester application package of the given version. Does NOT contain the services.xml. */
    void putTesterPackage(ApplicationId tester, ApplicationVersion applicationVersion, byte[] testerPackage);

    /** Returns the tester application package of the given version. Does NOT contain the services.xml. */
    byte[] getTesterPackage(ApplicationId tester, ApplicationVersion applicationVersion);

    /** Removes applications older than the given version, for the given application, and returns whether something was removed. */
    boolean pruneApplicationPackages(ApplicationId application, ApplicationVersion olderThanVersion);

    /** Removes tester packages older than the given version, for the given tester, and returns whether something was removed. */
    boolean pruneTesterPackages(ApplicationId tester, ApplicationVersion olderThanVersion);

}
