// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class SidecarImages {
    private final Map<String, DockerImage> images;

    public SidecarImages() {
        var props = new Properties();

        try (InputStream inputStream = SidecarImages.class.getResourceAsStream("/sidecar-images.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("sidecar-images.properties not found");
            }

            props.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load sidecar-images.properties", e);
        }

        images = props.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> DockerImage.fromString(e.getValue().toString())
                ));
    }

    public DockerImage getOrThrow(String key) {
        var image = images.get(key);

        if (image == null) {
            throw new IllegalStateException("Sidecar image '%s' is not configured in sidecar-images.properties".formatted(key));
        }

        return image;
    }

    public DockerImage getByRepositoryOrThrow(String repository) {
        return images.values().stream()
                .filter(image -> image.repository().equals(repository))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No sidecar image with repository '%s' configured in sidecar-images.properties".formatted(repository)));
    }
}
