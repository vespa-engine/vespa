package com.yahoo.config.provision;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sidecar container specification provided to host-admin by config-server or constructed based on a feature flag.
 * @param id Unique identifier of the sidecar in a specific node assigned by config-server or in a feature flag.
 *           Should be a positive integer from 0 to 99 inclusive. Used as part of a sidecar container name and hostname.
 * @param name User-defined sidecar name. Must be unique within a node.
 *             Used inside a Vespa container to make calls to a sidecar container, e.g. `curl sidecar0:8000`.
 * @param image Image to use for the sidecar container.
 * @param resources Compute resources to use for the sidecar container.
 * @param volumeMounts List of paths in the sidecar container that will be mounted as volumes from host and shared with Vespa container.
 * @param envs Environment variables to set in the sidecar container.
 * @param command Command to run in the sidecar container where the first element is the executable and the rest are arguments.
 *
 * @author glebashnik
 */
public record SidecarSpec(
        long id,
        String name,
        DockerImage image,
        SidecarResources resources,
        List<String> volumeMounts,
        Map<String, String> envs,
        List<String> command) {

    public SidecarSpec {
        if (id < 0 || id > 99) {
            throw new IllegalArgumentException("Sidecar id must be from 0 to 99 inclusive");
        }
        Objects.requireNonNull(name);
        Objects.requireNonNull(image);
        Objects.requireNonNull(volumeMounts);
        Objects.requireNonNull(envs);
        Objects.requireNonNull(command);
    }

    public boolean matchesByIdOrName(SidecarSpec other) {
        return id == other.id || name.equals(other.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SidecarSpec that = (SidecarSpec) o;
        return id == that.id
                && Objects.equals(name, that.name)
                && Objects.equals(image, that.image)
                && Objects.equals(resources, that.resources)
                && Objects.equals(volumeMounts, that.volumeMounts)
                && Objects.equals(envs, that.envs)
                && Objects.equals(command, that.command);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder {
        private long id;
        private String name;
        private DockerImage image;
        private SidecarResources resources = new SidecarResources(0, 0, 0, false);
        private List<String> volumeMounts = List.of();
        private Map<String, String> envs = Map.of();
        private List<String> command = List.of();

        public Builder() {}

        public Builder(SidecarSpec spec) {
            this.id = spec.id;
            this.name = spec.name;
            this.image = spec.image;
            this.resources = spec.resources;
            this.volumeMounts = spec.volumeMounts;
            this.envs = spec.envs;
            this.command = spec.command;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder image(DockerImage image) {
            this.image = image;
            return this;
        }

        public Builder resources(SidecarResources resources) {
            this.resources = resources;
            return this;
        }

        public Builder volumeMounts(List<String> volumeMounts) {
            this.volumeMounts = List.copyOf(volumeMounts);
            return this;
        }

        public Builder envs(Map<String, String> envs) {
            this.envs = Map.copyOf(envs);
            return this;
        }

        public Builder command(List<String> command) {
            this.command = List.copyOf(command);
            return this;
        }

        public Builder maxCpu(double maxCpu) {
            this.resources = resources.withMaxCpu(maxCpu);
            return this;
        }

        public Builder minCpu(double minCpu) {
            this.resources = resources.withMinCpu(minCpu);
            return this;
        }

        public Builder memoryGiB(double memoryGiB) {
            this.resources = resources.withMemoryGiB(memoryGiB);
            return this;
        }

        public Builder hasGpu(boolean hasGpu) {
            this.resources = resources.withGpu(hasGpu);
            return this;
        }

        public SidecarSpec build() {
            return new SidecarSpec(id, name, image, resources, volumeMounts, envs, command);
        }
    }
}
