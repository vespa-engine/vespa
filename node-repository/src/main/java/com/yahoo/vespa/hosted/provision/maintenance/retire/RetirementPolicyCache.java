// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.Optional;

/**
 * @author freva
 */
public class RetirementPolicyCache implements RetirementPolicy {
    private final RetirementPolicy policy;
    private final boolean isActiveCached;

    RetirementPolicyCache(RetirementPolicy policy) {
        this.policy = policy;
        this.isActiveCached = policy.isActive();
    }

    @Override
    public boolean isActive() {
        return isActiveCached;
    }

    public Optional<String> shouldRetire(Node node) {
        return policy.shouldRetire(node);
    }
}
