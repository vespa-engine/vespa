// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

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
    private final Optional<Group> groupId;
    private final Optional<String> dockerImage;

    private ClusterSpec(Type type, Id id, Optional<Group> groupId, Optional<String> dockerImage) {
        this.type = type;
        this.id = id;
        this.groupId = groupId;
        this.dockerImage = dockerImage;
    }

    /** Returns the cluster type */
    public Type type() { return type; }

    /** Returns the cluster id */
    public Id id() { return id; }

    public Optional<String> dockerImage() { return dockerImage; }

    /** Returns the group within the cluster this specifies, or empty to specify the whole cluster */
    public Optional<Group> group() { return groupId; }

    public ClusterSpec changeGroup(Optional<Group> newGroup) { return new ClusterSpec(type, id, newGroup, dockerImage); }

    public static ClusterSpec from(Type type, Id id) {
        return new ClusterSpec(type, id, Optional.empty(), Optional.empty());
    }

    public static ClusterSpec from(Type type, Id id, Optional<Group> groupId) {
        return new ClusterSpec(type, id, groupId, Optional.empty());
    }

    public static ClusterSpec from(Type type, Id id, Optional<Group> groupId, Optional<String> dockerImage) {
        return new ClusterSpec(type, id, groupId, dockerImage);
    }

    @Override
    public String toString() {
        return type + " " + id + (groupId.isPresent() ? " " + groupId.get() : "");
    }

    @Override
    public int hashCode() { return type.hashCode() + 17 * id.hashCode() + 31 * groupId.hashCode(); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterSpec)) return false;
        ClusterSpec other = (ClusterSpec)o;
        if ( ! other.type.equals(this.type)) return false;
        if ( ! other.id.equals(this.id)) return false;
        if ( ! other.groupId.equals(this.groupId)) return false;
        if ( ! other.dockerImage.equals(this.dockerImage)) return false;
        return true;
    }

    /** Returns whether this is equal, disregarding the group value */
    public boolean equalsIgnoringGroup(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterSpec)) return false;
        ClusterSpec other = (ClusterSpec)o;
        if ( ! other.type.equals(this.type)) return false;
        if ( ! other.id.equals(this.id)) return false;
        if ( ! other.dockerImage.equals(this.dockerImage)) return false;
        return true;
    }

    /** A cluster type */
    public enum Type {

        // These enum values are stored in ZooKeeper - do not change
        admin,
        container,
        content;

        public static Type from(String typeName) {
            switch (typeName) {
                case "admin" : return admin;
                case "container" : return container;
                case "content" : return content;
                default: throw new IllegalArgumentException("Illegal cluster type '" + typeName + "'");
            }
        }

    }

    public static final class Id {

        private final String id;

        public Id(String id) {
            Objects.requireNonNull(id, "Id cannot be null");
            this.id = id;
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

        private final String id;

        public Group(String id) {
            Objects.requireNonNull(id, "Group id cannot be null");
            this.id = id;
        }

        public static Group from(String id) {
            return new Group(id);
        }

        public String value() { return id; }

        @Override
        public String toString() { return "group '" + id + "'"; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return ((Group)o).id.equals(this.id);
        }

        @Override
        public int hashCode() { return id.hashCode(); }

    }

}
