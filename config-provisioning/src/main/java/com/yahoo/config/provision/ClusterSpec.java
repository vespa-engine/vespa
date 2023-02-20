// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;

import java.util.Objects;
import java.util.Optional;

/**
 * A specification of a cluster - or group in a grouped cluster - to be run on a set of hosts.
 * This is a value object.
 *
 * @author bratseth
 */
public final class ClusterSpec {

    private final Type type;
    private final Id id;

    /** The group id of these hosts, or empty if this is represents a request for hosts */
    private final Optional<Group> groupId;

    private final Version vespaVersion;
    private final boolean exclusive;
    private final Optional<Id> combinedId;
    private final Optional<DockerImage> dockerImageRepo;
    private final ZoneEndpoint zoneEndpoint;
    private final boolean stateful;

    private ClusterSpec(Type type, Id id, Optional<Group> groupId, Version vespaVersion, boolean exclusive,
                        Optional<Id> combinedId, Optional<DockerImage> dockerImageRepo,
                        ZoneEndpoint zoneEndpoint, boolean stateful) {
        this.type = type;
        this.id = id;
        this.groupId = groupId;
        this.vespaVersion = Objects.requireNonNull(vespaVersion, "vespaVersion cannot be null");
        this.exclusive = exclusive;
        if (type == Type.combined) {
            if (combinedId.isEmpty()) throw new IllegalArgumentException("combinedId must be set for cluster of type " + type);
        } else {
            if (combinedId.isPresent()) throw new IllegalArgumentException("combinedId must be empty for cluster of type " + type);
        }
        this.combinedId = combinedId;
        if (dockerImageRepo.isPresent() && dockerImageRepo.get().tag().isPresent())
            throw new IllegalArgumentException("dockerImageRepo is not allowed to have a tag");
        this.dockerImageRepo = dockerImageRepo;
        if (type.isContent() && !stateful) {
            throw new IllegalArgumentException("Cluster of type " + type + " must be stateful");
        }
        this.zoneEndpoint = Objects.requireNonNull(zoneEndpoint);
        this.stateful = stateful;
    }

    /** Returns the cluster type */
    public Type type() { return type; }

    /** Returns the cluster id */
    public Id id() { return id; }

    /** Returns the docker image repository part of a docker image we want this cluster to run */
    public Optional<DockerImage> dockerImageRepo() { return dockerImageRepo; }

    /** Returns the docker image (repository + vespa version) we want this cluster to run */
    public Optional<String> dockerImage() { return dockerImageRepo.map(repo -> repo.withTag(vespaVersion).asString()); }

    /** Returns any additional zone endpoint settings for application container clusters. */
    public ZoneEndpoint zoneEndpoint() { return zoneEndpoint; }

    /** Returns the version of Vespa that we want this cluster to run */
    public Version vespaVersion() { return vespaVersion; }

    /** Returns the group within the cluster this specifies, or empty to specify the whole cluster */
    public Optional<Group> group() { return groupId; }

    /** Returns the ID of the container cluster that is combined with this. This is only present for combined clusters */
    public Optional<Id> combinedId() {
        return combinedId;
    }

    /**
     * Returns whether the physical hosts running the nodes of this application can
     * also run nodes of other applications. Using exclusive nodes for containers increases security and cost.
     */
    public boolean isExclusive() { return exclusive; }

    /** Returns whether this cluster has state */
    public boolean isStateful() { return stateful; }

    public ClusterSpec with(Optional<Group> newGroup) {
        return new ClusterSpec(type, id, newGroup, vespaVersion, exclusive, combinedId, dockerImageRepo, zoneEndpoint, stateful);
    }

    public ClusterSpec withExclusivity(boolean exclusive) {
        return new ClusterSpec(type, id, groupId, vespaVersion, exclusive, combinedId, dockerImageRepo, zoneEndpoint, stateful);
    }

    public ClusterSpec exclusive(boolean exclusive) {
        return new ClusterSpec(type, id, groupId, vespaVersion, exclusive, combinedId, dockerImageRepo, zoneEndpoint, stateful);
    }

    /** Creates a ClusterSpec when requesting a cluster */
    public static Builder request(Type type, Id id) {
        return new Builder(type, id, false);
    }

    /** Creates a ClusterSpec for an existing cluster, group id and Vespa version needs to be set */
    public static Builder specification(Type type, Id id) {
        return new Builder(type, id, true);
    }

    public static class Builder {

        private final Type type;
        private final Id id;
        private final boolean specification;

        private Optional<Group> groupId = Optional.empty();
        private Optional<DockerImage> dockerImageRepo = Optional.empty();
        private Version vespaVersion;
        private boolean exclusive = false;
        private Optional<Id> combinedId = Optional.empty();
        private ZoneEndpoint zoneEndpoint = ZoneEndpoint.defaultEndpoint;
        private boolean stateful;

        private Builder(Type type, Id id, boolean specification) {
            this.type = type;
            this.id = id;
            this.specification = specification;
            this.stateful = type.isContent(); // Default to true for content clusters
        }

        public ClusterSpec build() {
            if (specification) {
                if (groupId.isEmpty()) throw new IllegalArgumentException("groupId is required to be set when creating a ClusterSpec with specification()");
                if (vespaVersion == null) throw new IllegalArgumentException("vespaVersion is required to be set when creating a ClusterSpec with specification()");
            } else
                if (groupId.isPresent()) throw new IllegalArgumentException("groupId is not allowed to be set when creating a ClusterSpec with request()");
            return new ClusterSpec(type, id, groupId, vespaVersion, exclusive, combinedId, dockerImageRepo, zoneEndpoint, stateful);
        }

        public Builder group(Group groupId) {
            this.groupId = Optional.ofNullable(groupId);
            return this;
        }

        public Builder vespaVersion(Version vespaVersion) {
            this.vespaVersion = vespaVersion;
            return this;
        }

        public Builder vespaVersion(String vespaVersion) {
            this.vespaVersion = Version.fromString(vespaVersion);
            return this;
        }

        public Builder exclusive(boolean exclusive) {
            this.exclusive = exclusive;
            return this;
        }

        public Builder combinedId(Optional<Id> combinedId) {
            this.combinedId = combinedId;
            return this;
        }

        public Builder dockerImageRepository(Optional<DockerImage> dockerImageRepo) {
            this.dockerImageRepo = dockerImageRepo;
            return this;
        }

        public Builder loadBalancerSettings(ZoneEndpoint zoneEndpoint) {
            this.zoneEndpoint = zoneEndpoint;
            return this;
        }

        public Builder stateful(boolean stateful) {
            this.stateful = stateful;
            return this;
        }

    }

    @Override
    public String toString() {
        return type + " " + id + " " + groupId.map(group -> group + " ").orElse("") + vespaVersion + (dockerImageRepo.map(repo -> " " + repo).orElse(""));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterSpec that = (ClusterSpec) o;
        return exclusive == that.exclusive &&
               stateful == that.stateful &&
               type == that.type &&
               id.equals(that.id) &&
               groupId.equals(that.groupId) &&
               vespaVersion.equals(that.vespaVersion) &&
               combinedId.equals(that.combinedId) &&
               dockerImageRepo.equals(that.dockerImageRepo) &&
               zoneEndpoint.equals(that.zoneEndpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, groupId, vespaVersion, exclusive, combinedId, dockerImageRepo, zoneEndpoint, stateful);
    }

    /**
     * Returns whether this satisfies other for allocation purposes. Only considers cluster ID and type, other fields
     * are ignored.
     */
    public boolean satisfies(ClusterSpec other) {
        if ( ! other.id.equals(this.id)) return false; // ID mismatch
        if (other.type.isContent() || this.type.isContent()) // Allow seamless transition between content and combined
            return other.type.isContent() == this.type.isContent();
        return other.type.equals(this.type);
    }

    /** A cluster type */
    public enum Type {

        // These enum names are written to ZooKeeper - do not change
        admin,
        container,
        content,
        combined;

        /** Returns whether this runs a content cluster */
        public boolean isContent() {
            return this == content || this == combined;
        }

        /** Returns whether this runs a container cluster */
        public boolean isContainer() {
            return this == container || this == combined;
        }

        public static Type from(String typeName) {
            return switch (typeName) {
                case "admin" -> admin;
                case "container" -> container;
                case "content" -> content;
                case "combined" -> combined;
                default -> throw new IllegalArgumentException("Illegal cluster type '" + typeName + "'");
            };
        }

    }

    public static final class Id {

        private final String id;

        public Id(String id) {
            this.id = Objects.requireNonNull(id, "Id cannot be null");
        }

        public static Id from(String id) {
            return new Id(id);
        }

        public String value() { return id; }

        @Override
        public String toString() { return "cluster '" + id + "'"; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return ((Id)o).id.equals(this.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

    }

    /** Identifier of a group within a cluster */
    public static final class Group {

        private final int index;

        private Group(int index) {
            this.index = index;
        }

        public static Group from(int index) { return new Group(index); }

        public int index() { return index; }

        @Override
        public String toString() { return "group " + index; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return ((Group)o).index == this.index;
        }

        @Override
        public int hashCode() { return index; }

    }

}
