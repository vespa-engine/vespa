// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;

/**
 * @author stiankri
 */
public class Container {
    public final String hostname;
    public final DockerImage image;
    public final ContainerName name;
    public final State state;
    public final int pid;

    public Container(
            final String hostname,
            final DockerImage image,
            final ContainerName containerName,
            final State state,
            final int pid) {
        this.hostname = hostname;
        this.image = image;
        this.name = containerName;
        this.state = state;
        this.pid = pid;
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
                && Objects.equals(pid, other.pid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, image, name, pid);
    }

    @Override
    public String toString() {
        return "Container {"
                + " hostname=" + hostname
                + " image=" + image
                + " name=" + name
                + " state=" + state
                + " pid=" + pid
                + "}";
    }

    public enum State {
        CREATED, RESTARTING, RUNNING, PAUSED, EXITED, DEAD;

        public boolean isRunning() {
            return this == RUNNING;
        }
    }
}
