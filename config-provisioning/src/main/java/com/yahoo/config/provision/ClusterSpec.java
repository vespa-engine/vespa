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

    /** The group id of these hosts, or empty if this is represents a request for hosts */
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

    /** @deprecated pass a docker image or empty. TODO: Remove when no model older than 6.29 is in use */
    @Deprecated
    public static ClusterSpec from(Type type, Id id) {
        return new ClusterSpec(type, id, Optional.empty(), Optional.empty());
    }

    /** @deprecated either pass a group or not. TODO: Remove when no model older than 6.29 is in use */
    @Deprecated 
    public static ClusterSpec from(Type type, Id id, Optional<Group> groupId) {
        return new ClusterSpec(type, id, groupId, Optional.empty());
    }

    /** @deprecated pass a docker image or empty. TODO: Remove when no model older than 6.29 is in use */
    @Deprecated
    public static ClusterSpec from(Type type, Id id, Optional<Group> groupId, Optional<String> dockerImage) {
        return new ClusterSpec(type, id, groupId, dockerImage);
    }

    /** Create a specification <b>specifying</b> an existing cluster group having these attributes */
    public static ClusterSpec from(Type type, Id id, Group groupId, Optional<String> dockerImage) {
        return new ClusterSpec(type, id, Optional.of(groupId), dockerImage);
    }

    /** Create a specification <b>requesting</b> a cluster with these attributes */
    public static ClusterSpec request(Type type, Id id, Optional<String> dockerImage) {
        return new ClusterSpec(type, id, Optional.empty(), dockerImage);
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
    @SuppressWarnings("deprecation")
    public static final class Group {

        private final int index;

        /** @deprecated pass a group index instead. TODO: Remove when no older config models than 6.29 remains */
        @Deprecated
        public Group(String id) {
            this(Integer.parseInt(id));
        }
        
        private Group(int index) {
            this.index = index;
        }

        /** @deprecated pass a group index instead. TODO: Remove when no older config models than 6.29 remains */
        @Deprecated
        public static Group from(String id) { return new Group(id); }
        
        public static Group from(int index) { return new Group(index); }

        /** @deprecated use index() instead. TODO: Remove when no older config models than 6.29 remains */
        @Deprecated
        public String value() { return String.valueOf(index); }
        
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
