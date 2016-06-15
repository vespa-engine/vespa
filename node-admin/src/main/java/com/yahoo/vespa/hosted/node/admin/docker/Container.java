// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Objects;

/**
 * @author stiankri
 */
public class Container {
    public final HostName hostname;
    public final DockerImage image;
    public final ContainerName name;
    public final boolean isRunning;

    public Container(
            final HostName hostname,
            final DockerImage image,
            final ContainerName containerName,
            final boolean isRunning) {
        this.hostname = hostname;
        this.image = image;
        this.name = containerName;
        this.isRunning = isRunning;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Container)) {
            return false;
        }
        final Container other = (Container) obj;
        return Objects.equals(hostname, other.hostname)
                && Objects.equals(image, other.image)
                && Objects.equals(name, other.name)
                && Objects.equals(isRunning, other.isRunning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, image, name, isRunning);
    }

    @Override
    public String toString() {
        return "Container {"
                + " hostname=" + hostname
                + " image=" + image
                + " name=" + name
                + " isRunning=" + isRunning
                + "}";
    }
}
