// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;

/**
 * A repository of application build artifacts.
 *
 * @author mpolden
 */
public interface ArtifactRepository {

    /** Returns the system application package of the given version. */
    byte[] getSystemApplicationPackage(ApplicationId application, ZoneId zone, Version version);

    /** Returns the current OS release with the given major version and tag */
    OsRelease osRelease(int major, OsRelease.Tag tag);

}
