// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.vespa.hosted.controller.api.integration.artifact.Artifact;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.ArtifactRegistry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mpolden
 */
public class ArtifactRegistryMock implements ArtifactRegistry {

    private static final Comparator<Artifact> comparator = Comparator.comparing(Artifact::registry)
                                                                     .thenComparing(Artifact::repository)
                                                                     .thenComparing(Artifact::version);

    private final Map<String, Artifact> images = new HashMap<>();

    public ArtifactRegistryMock add(Artifact image) {
        if (images.containsKey(image.id())) throw new IllegalArgumentException("Image with ID '" + image.id() + "' already exists");
        images.put(image.id(), image);
        return this;
    }

    @Override
    public void deleteAll(List<Artifact> artifacts) {
        artifacts.forEach(image -> this.images.remove(image.id()));
    }

    @Override
    public List<Artifact> list() {
        return images.values().stream().sorted(comparator).toList();
    }

}
