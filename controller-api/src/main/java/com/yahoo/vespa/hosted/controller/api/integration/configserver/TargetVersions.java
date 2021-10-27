// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Target versions for node upgrades in a zone.
 *
 * @author mpolden
 */
public class TargetVersions {

    public static final TargetVersions EMPTY = new TargetVersions(Map.of(), Map.of());

    private final Map<NodeType, Version> vespaVersions;
    private final Map<NodeType, Version> osVersions;

    public TargetVersions(Map<NodeType, Version> vespaVersions, Map<NodeType, Version> osVersions) {
        this.vespaVersions = Map.copyOf(Objects.requireNonNull(vespaVersions, "vespaVersions must be non-null"));
        this.osVersions = Map.copyOf(Objects.requireNonNull(osVersions, "osVersions must be non-null"));
    }

    /** Returns a copy of this with Vespa version set for given node type */
    public TargetVersions withVespaVersion(NodeType nodeType, Version version) {
        var vespaVersions = new HashMap<>(this.vespaVersions);
        vespaVersions.put(nodeType, version);
        return new TargetVersions(vespaVersions, osVersions);
    }

    /** Returns a copy of this with OS version set for given node type */
    public TargetVersions withOsVersion(NodeType nodeType, Version version) {
        var osVersions = new HashMap<>(this.osVersions);
        osVersions.put(nodeType, version);
        return new TargetVersions(vespaVersions, osVersions);
    }

    /** Returns the target OS version of given node type, if any */
    public Optional<Version> osVersion(NodeType type) {
        return Optional.ofNullable(osVersions.get(type));
    }

    /** Returns the target Vespa version of given node type, if any */
    public Optional<Version> vespaVersion(NodeType type) {
        return Optional.ofNullable(vespaVersions.get(type));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TargetVersions that = (TargetVersions) o;
        return vespaVersions.equals(that.vespaVersions) &&
               osVersions.equals(that.osVersions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vespaVersions, osVersions);
    }

}
