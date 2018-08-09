// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

/**
 * A repository of application build artifacts.
 *
 * @author mpolden
 */
public interface ArtifactRepository {

    /** Returns the tenant application package of the given version. */
    byte[] getApplicationPackage(ApplicationId application, String applicationVersion);

    /** Stores the given tenant application package of the given version. */
    void putApplicationPackage(ApplicationId application, String applicationVersion, byte[] applicationPackage);

    /** Returns the system application package of the given version. */
    byte[] getSystemApplicationPackage(ApplicationId application, ZoneId zone, Version version);

    /** Stores the given tester application package of the given version. Does NOT contain the services.xml. */
    void putTesterPackage(ApplicationId tester, String applicationVersion, byte[] testerPackage);

    /** Returns the tester application package of the given version. Does NOT contain the services.xml. */
    byte[] getTesterPackage(ApplicationId tester, String applicationVersion);

}
