// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;

/**
 * Type-safe value wrapper for docker image reference.
 *
 * @author bakksjo
 */
public class DockerImage {
    private final String imageId;

    public DockerImage(final String imageId) {
        this.imageId = Objects.requireNonNull(imageId);
    }

    public String asString() {
        return imageId;
    }

    @Override
    public int hashCode() {
        return imageId.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof DockerImage)) {
            return false;
        }

        final DockerImage other = (DockerImage) o;

        return Objects.equals(imageId, other.imageId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " imageId=" + imageId
                + " }";
    }
}
