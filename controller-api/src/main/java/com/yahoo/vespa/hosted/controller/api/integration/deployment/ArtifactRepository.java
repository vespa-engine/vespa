// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;

/**
 * A repository of application build artifacts.
 *
 * @author mpolden
 */
public interface ArtifactRepository {

    byte[] getApplicationPackage(ApplicationId application, String applicationVersion);

}
