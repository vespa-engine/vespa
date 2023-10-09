// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Filters nodes based on their cloud account.
 *
 * @author gjoranv
 */
public class CloudAccountFilter {

    private CloudAccountFilter() { }

    /** Creates a node filter which removes the nodes from the given cloud accounts */
    public static Predicate<Node> from(Collection<CloudAccount> unwantedAccounts, boolean enabled) {
        Objects.requireNonNull(unwantedAccounts, "unwantedAccounts cannot be null");
        return node -> {
            if (unwantedAccounts.isEmpty()) return true;
            if (! enabled) return true;
            return ! unwantedAccounts.contains(node.cloudAccount());
        };
    }

}
