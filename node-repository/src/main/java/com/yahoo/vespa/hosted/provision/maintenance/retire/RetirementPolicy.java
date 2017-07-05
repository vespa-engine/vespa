// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.Optional;

/**
 * @author freva
 */
public interface RetirementPolicy {

    /**
     * Returns whether the policy is currently active. NodeRetirer ask every time before executing.
     */
    boolean isActive();

    /**
     * Returns reason for retiring the node, empty if node should not be retired
     */
    Optional<String> shouldRetire(Node node);
}
