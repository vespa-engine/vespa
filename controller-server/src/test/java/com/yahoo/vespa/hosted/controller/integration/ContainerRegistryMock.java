// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.vespa.hosted.controller.api.integration.container.ContainerImage;
import com.yahoo.vespa.hosted.controller.api.integration.container.ContainerRegistry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class ContainerRegistryMock implements ContainerRegistry {

    private static final Comparator<ContainerImage> comparator = Comparator.comparing(ContainerImage::registry)
                                                                           .thenComparing(ContainerImage::repository)
                                                                           .thenComparing(ContainerImage::version);

    private final Map<String, ContainerImage> images = new HashMap<>();

    public ContainerRegistryMock add(ContainerImage image) {
        images.put(image.id(), image);
        return this;
    }

    @Override
    public void deleteAll(List<ContainerImage> images) {
        images.forEach(image -> this.images.remove(image.id()));
    }

    @Override
    public List<ContainerImage> list() {
        return images.values().stream().sorted(comparator).collect(Collectors.toUnmodifiableList());
    }

}
