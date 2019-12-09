// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;

/**
 * Filter nodes by their OS version.
 *
 * @author mpolden
 */
public class NodeOsVersionFilter extends NodeFilter {

    private final Version version;

    private NodeOsVersionFilter(Version version, NodeFilter next) {
        super(next);
        this.version = Objects.requireNonNull(version, "version cannot be null");
    }

    @Override
    public boolean matches(Node node) {
        if (!version.isEmpty() && !node.status().osVersion().matches(version)) {
            return false;
        }
        return nextMatches(node);
    }

    public static NodeOsVersionFilter from(String version, NodeFilter filter) {
        return new NodeOsVersionFilter(Version.fromString(version), filter);
    }

}
