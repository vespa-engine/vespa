// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;

/**
 * Information about a container as a result of listing containers (aka "docker ps").
 *
 * @author hakonhall
 */
public class ContainerLite {
    private final String id;
    private final String imageId;
    // state is one of: "running"
    private final String state;

    public ContainerLite(String id, String imageId, String state) {
        this.id = id;
        this.imageId = imageId;
        this.state = state;
    }

    /** Of format: "94a66101b8dfbf485f4f77a448b079684ea704927aa39e31d824de708cfa3373" */
    public String id() {
        return id;
    }

    /** Of format: "sha256:7f3abbbbb17d135840a1f185ac291c87f7b90651e65b6021e820abaf397dd282" */
    public String imageId() {
        return imageId;
    }

    /** Whether the container is running. */
    public boolean isRunning() {
        return "running".equals(state);
    }

    @Override
    public String toString() {
        return "ContainerLite{" +
                "id='" + id + '\'' +
                ", imageId='" + imageId + '\'' +
                ", state='" + state + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerLite that = (ContainerLite) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(imageId, that.imageId) &&
                Objects.equals(state, that.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, imageId, state);
    }
}
