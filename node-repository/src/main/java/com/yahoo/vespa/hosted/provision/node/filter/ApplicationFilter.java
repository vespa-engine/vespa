// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A node filter which matches a set of applications.
 *
 * @author bratseth
 */
public class ApplicationFilter {

    private ApplicationFilter() {}

    /** Creates a node filter which filters using the given host filter */
    private static Predicate<Node> makePredicate(Set<ApplicationId> applications) {
        Objects.requireNonNull(applications, "Applications set cannot be null, use an empty set");
        if (applications.isEmpty()) return node -> true;
        return node -> node.allocation().isPresent() && applications.contains(node.allocation().get().owner());
    }

    public static Predicate<Node> from(ApplicationId applicationId) {
        return makePredicate(Set.of(applicationId));
    }

    public static Predicate<Node> from(Set<ApplicationId> applicationIds) {
        return makePredicate(Set.copyOf(applicationIds));
    }

    public static Predicate<Node> from(String applicationIds) {
        return makePredicate(StringUtilities.split(applicationIds).stream()
                .map(ApplicationId::fromFullString)
                .collect(Collectors.toUnmodifiableSet()));
    }

}
