// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Provides access to sidecar images configured in sidecar-images.properties.
 *
 * The properties file is expected to have entries of the form
 * <pre>
 *     key=registry/repository:tag
 * </pre>
 * where the key is an arbitrary identifier for the image, and the value is a container image reference.
 *
 * @author glebashnik
 */
public class SidecarImages {
    public static final String SIDECAR_REPOSITORY_PREFIX = "sidecar/";
    private final Map<String, DockerImage> images;

    SidecarImages(Map<String, DockerImage> images) {
        this.images = images;
    }

    public static SidecarImages readFromProperties() {
        var props = new Properties();

        try (InputStream inputStream = SidecarImages.class.getResourceAsStream("/sidecar-images.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("sidecar-images.properties not found");
            }

            props.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load sidecar-images.properties", e);
        }

        return new SidecarImages(props.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> DockerImage.fromString(e.getValue().toString()))));
    }

    public DockerImage getOrThrow(String key) {
        var image = images.get(key);

        if (image == null) {
            throw new IllegalStateException(
                    Text.format("Sidecar image '%s' is not configured in sidecar-images.properties", key));
        }

        return image;
    }

    public DockerImage getByRepositoryOrThrow(String repository) {
        return images.values().stream()
                .filter(image -> image.repository().equals(repository))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(Text.format(
                        "No sidecar image with repository '%s' configured in sidecar-images.properties", repository)));
    }

    /**
     * Returns repository without SIDECAR_REPOSITORY_PREFIX,
     * e.g. given "sidecar/nvidia/tritonserver" returns "nvidia/tritonserver".
     */
    public static String removeSidecarRepositoryPrefix(String sidecarRepository) {
        if (!sidecarRepository.startsWith(SIDECAR_REPOSITORY_PREFIX)) {
            throw new IllegalArgumentException(Text.format(
                    "Sidecar repository '%s' does not start with expected prefix '%s'",
                    sidecarRepository, SIDECAR_REPOSITORY_PREFIX));
        }

        return sidecarRepository.substring(SIDECAR_REPOSITORY_PREFIX.length());
    }
}