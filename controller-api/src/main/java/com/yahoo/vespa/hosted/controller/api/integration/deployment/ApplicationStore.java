// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;

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
    byte[] get(ApplicationId application, ApplicationVersion applicationVersion);

    /** Stores the given tenant application package of the given version. */
    void put(ApplicationId application, ApplicationVersion applicationVersion, byte[] applicationPackage);

    /** Removes applications older than the given version, for the given application, and returns whether something was removed. */
    boolean prune(ApplicationId application, ApplicationVersion olderThanVersion);

    /** Removes all application packages for the given application, including any development package. */
    void removeAll(ApplicationId application);

    /** Returns the tester application package of the given version. Does NOT contain the services.xml. */
    byte[] get(TesterId tester, ApplicationVersion applicationVersion);

    /** Stores the given tester application package of the given version. Does NOT contain the services.xml. */
    void put(TesterId tester, ApplicationVersion applicationVersion, byte[] testerPackage);

    /** Removes tester packages older than the given version, for the given tester, and returns whether something was removed. */
    boolean prune(TesterId tester, ApplicationVersion olderThanVersion);

    /** Removes all tester packages for the given tester. */
    void removeAll(TesterId tester);

    /** Stores the given application package as the development package for the given application. */
    void putDev(ApplicationId application, byte[] applicationPackage);

    /** Returns the development package for the given application. */
    byte[] getDev(ApplicationId application);

}
