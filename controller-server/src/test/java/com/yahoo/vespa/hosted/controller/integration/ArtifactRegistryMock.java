// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.vespa.hosted.controller.api.integration.artifact.Artifact;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.ArtifactRegistry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class ArtifactRegistryMock implements ArtifactRegistry {

    private static final Comparator<Artifact> comparator = Comparator.comparing((Artifact artifact) -> artifact.registry().orElse(""))
                                                                           .thenComparing(artifact -> artifact.repository().orElse(""))
                                                                           .thenComparing(Artifact::version);

    private final Map<String, Artifact> images = new HashMap<>();

    public ArtifactRegistryMock add(Artifact image) {
        images.put(image.id(), image);
        return this;
    }

    @Override
    public void deleteAll(List<Artifact> images) {
        images.forEach(image -> this.images.remove(image.id()));
    }

    @Override
    public List<Artifact> list() {
        return images.values().stream().sorted(comparator).collect(Collectors.toUnmodifiableList());
    }

}
