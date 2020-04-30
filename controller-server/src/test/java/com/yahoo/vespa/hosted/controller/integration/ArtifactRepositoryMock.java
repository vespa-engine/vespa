// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;

/**
 * @author mpolden
 */
public class ArtifactRepositoryMock extends AbstractComponent implements ArtifactRepository {

    @Override
    public byte[] getSystemApplicationPackage(ApplicationId application, ZoneId zone, Version version) {
        return new byte[0];
    }

}
