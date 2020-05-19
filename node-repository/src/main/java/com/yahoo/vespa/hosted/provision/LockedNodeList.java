// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.transaction.Mutex;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;

/**
 * A type-safe wrapper for {@link NodeList}. Callers that have a reference to this can safely be assumed to holding the
 * write lock for the node repository.
 *
 * This is typically used in situations where modifying a node object depends on inspecting a consistent state of other
 * nodes in the repository.
 *
 * @author mpolden
 */
public class LockedNodeList extends NodeList {

    private final Mutex lock;

    public LockedNodeList(List<Node> nodes, Mutex lock) {
        super(nodes, false);
        this.lock = Objects.requireNonNull(lock, "lock must be non-null");
    }

    public LockedNodeList filter(Predicate<Node> predicate) {
        return asList().stream()
                       .filter(predicate)
                       .collect(collectingAndThen(Collectors.toList(),
                                                  (nodes) -> new LockedNodeList(nodes, lock)));
    }

}
