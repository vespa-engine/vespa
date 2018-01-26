// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author mpolden
 */
public class ArtifactRepositoryMock implements ArtifactRepository {

    private final Map<Integer, byte[]> repository = new HashMap<>();

    public ArtifactRepositoryMock put(ApplicationId applicationId, ApplicationPackage applicationPackage,
                                      String applicationVersion) {
        repository.put(artifactHash(applicationId, applicationVersion), applicationPackage.zippedContent());
        return this;
    }

    @Override
    public byte[] getApplicationPackage(ApplicationId applicationId, String applicationVersion) {
        int artifactHash = artifactHash(applicationId, applicationVersion);
        if (!repository.containsKey(artifactHash)) {
            throw new IllegalArgumentException("No application package found for " + applicationId + " with version "
                                               + applicationVersion);
        }
        return repository.get(artifactHash);
    }

    private static int artifactHash(ApplicationId applicationId, String applicationVersion) {
        return Objects.hash(applicationId, applicationVersion);
    }

}
