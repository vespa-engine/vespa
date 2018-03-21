// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

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

    private boolean exclusive;

    private ClusterSpec(Type type, Id id, Optional<Group> groupId, Version vespaVersion, boolean exclusive) {
        this.type = type;
        this.id = id;
        this.groupId = groupId;
        this.vespaVersion = vespaVersion;
        this.exclusive = exclusive;
    }

    /** Returns the cluster type */
    public Type type() { return type; }

    /** Returns the cluster id */
    public Id id() { return id; }

    public Version vespaVersion() { return vespaVersion; }

    /** Returns the group within the cluster this specifies, or empty to specify the whole cluster */
    public Optional<Group> group() { return groupId; }

    /**
     * Returns whether the physical hosts running the nodes of this application can
     * also run nodes of other applications. Using exclusive nodes for containers increases security
     * and increases cost.
     */
    public boolean isExclusive() { return exclusive; }

    // TODO: Remove after April 2018
    public ClusterSpec changeGroup(Optional<Group> newGroup) {
        return with(newGroup);
    }

    public ClusterSpec with(Optional<Group> newGroup) {
        return new ClusterSpec(type, id, newGroup, vespaVersion, exclusive);
    }

    public ClusterSpec exclusive(boolean exclusive) {
        return new ClusterSpec(type, id, groupId, vespaVersion, exclusive);
    }

    // TODO: Remove after April 2018
    public static ClusterSpec request(Type type, Id id, Version vespaVersion) {
        return request(type, id, vespaVersion, false);
    }
    public static ClusterSpec request(Type type, Id id, Version vespaVersion, boolean exclusive) {
        return new ClusterSpec(type, id, Optional.empty(), vespaVersion, exclusive);
    }

    // TODO: Remove after April 2018
    public static ClusterSpec from(Type type, Id id, Group groupId, Version vespaVersion) {
        return new ClusterSpec(type, id, Optional.of(groupId), vespaVersion, false);
    }
    public static ClusterSpec from(Type type, Id id, Group groupId, Version vespaVersion, boolean exclusive) {
        return new ClusterSpec(type, id, Optional.of(groupId), vespaVersion, exclusive);
    }

    @Override
    public String toString() {
        return String.join(" ", type.toString(), id.toString(),
                           groupId.map(Group::toString).orElse(""),
                           vespaVersion.toString());
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
        if ( ! other.vespaVersion.equals(this.vespaVersion)) return false;
        return true;
    }

    /** Returns whether this is equal, disregarding the group value and wanted Vespa version */
    public boolean equalsIgnoringGroupAndVespaVersion(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterSpec)) return false;
        ClusterSpec other = (ClusterSpec)o;
        if ( ! other.type.equals(this.type)) return false;
        if ( ! other.id.equals(this.id)) return false;
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
    @SuppressWarnings("deprecation")
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
