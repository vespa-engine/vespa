// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.yahoo.config.provision.DockerImage;

import java.util.Objects;

/**
 * @author stiankri
 */
// TODO: Move this to node-admin when docker-api module can be removed
public class Container {
    private final ContainerId id;
    public final String hostname;
    public final DockerImage image;
    public final ContainerResources resources;
    public final ContainerName name;
    public final State state;
    public final int pid;

    public Container(
            final ContainerId id,
            final String hostname,
            final DockerImage image,
            final ContainerResources resources,
            final ContainerName containerName,
            final State state,
            final int pid) {
        this.id = id;
        this.hostname = hostname;
        this.image = image;
        this.resources = resources;
        this.name = containerName;
        this.state = state;
        this.pid = pid;
    }

    public ContainerId id() {
        return id;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Container)) {
            return false;
        }
        final Container other = (Container) obj;
        return Objects.equals(id, other.id)
                && Objects.equals(hostname, other.hostname)
                && Objects.equals(image, other.image)
                && Objects.equals(resources, other.resources)
                && Objects.equals(name, other.name)
                && Objects.equals(pid, other.pid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, image, resources, name, pid);
    }

    @Override
    public String toString() {
        return "Container {"
                + " id=" + id
                + " hostname=" + hostname
                + " image=" + image
                + " resources=" + resources
                + " name=" + name
                + " state=" + state
                + " pid=" + pid
                + "}";
    }

    public enum State {
        CREATED, RESTARTING, RUNNING, REMOVING, PAUSED, EXITED, DEAD, UNKNOWN, CONFIGURED, STOPPED;

        public boolean isRunning() {
            return this == RUNNING;
        }
    }
}
