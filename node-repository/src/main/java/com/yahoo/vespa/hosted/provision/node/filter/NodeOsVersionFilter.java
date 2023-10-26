// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Filter nodes by their OS version.
 *
 * @author mpolden
 */
public class NodeOsVersionFilter {

    private NodeOsVersionFilter() {}

    private static Predicate<Node> makePredicate(Version version) {
        Objects.requireNonNull(version, "version cannot be null");
        if (version.isEmpty()) return node -> true;
        return node -> node.status().osVersion().matches(version);
    }

    public static Predicate<Node> from(String version) {
        return makePredicate(Version.fromString(version));
    }

}
