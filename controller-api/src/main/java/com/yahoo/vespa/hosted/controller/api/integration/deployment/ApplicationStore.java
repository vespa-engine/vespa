package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;
/**
 * Store for the application package and application tester package.
 *
 * This interface will take over most of the responsibility from the ArtifactRepository with time.
 */
public interface ApplicationStore {

    /** Returns the tenant application package of the given version. */
    byte[] getApplicationPackage(ApplicationId application, String applicationVersion);

    /** Stores the given tenant application package of the given version. */
    void putApplicationPackage(ApplicationId application, String applicationVersion, byte[] applicationPackage);

    /** Stores the given tester application package of the given version. Does NOT contain the services.xml. */
    void putTesterPackage(ApplicationId tester, String applicationVersion, byte[] testerPackage);

    /** Returns the tester application package of the given version. Does NOT contain the services.xml. */
    byte[] getTesterPackage(ApplicationId tester, String applicationVersion);
}
