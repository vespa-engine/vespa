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

    private final Map<Integer, Artifact> repository = new HashMap<>();

    public ArtifactRepositoryMock put(ApplicationId applicationId, ApplicationPackage applicationPackage,
                                      String applicationVersion) {
        repository.put(artifactHash(applicationId, applicationVersion),
                       new Artifact(applicationPackage.zippedContent()));
        return this;
    }

    public int hits(ApplicationId applicationId, String applicationVersion) {
        Artifact artifact = repository.get(artifactHash(applicationId, applicationVersion));
        return artifact == null ? 0 : artifact.hits;
    }

    public ArtifactRepository resetHits() {
        repository.values().forEach(Artifact::resetHits);
        return this;
    }

    @Override
    public byte[] getApplicationPackage(ApplicationId applicationId, String applicationVersion) {
        int artifactHash = artifactHash(applicationId, applicationVersion);
        if (!repository.containsKey(artifactHash)) {
            throw new IllegalArgumentException("No application package found for " + applicationId + " with version "
                                               + applicationVersion);
        }
        Artifact artifact = repository.get(artifactHash);
        artifact.recordHit();
        return artifact.data;
    }

    private static int artifactHash(ApplicationId applicationId, String applicationVersion) {
        return Objects.hash(applicationId, applicationVersion);
    }

    private class Artifact {

        private final byte[] data;
        private int hits = 0;

        private Artifact(byte[] data) {
            this.data = data;
        }

        private void recordHit() {
            hits++;
        }

        private void resetHits() {
            hits = 0;
        }

    }

}
