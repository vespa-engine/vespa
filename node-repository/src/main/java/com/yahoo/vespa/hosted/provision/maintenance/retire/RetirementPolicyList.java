// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author freva
 */
public class RetirementPolicyList implements RetirementPolicy {
    private final List<RetirementPolicy> retirementPolicies;

    public RetirementPolicyList(RetirementPolicy... retirementPolicies) {
        this.retirementPolicies = Stream.of(retirementPolicies)
                .map(RetirementPolicyCache::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isActive() {
        return retirementPolicies.stream().anyMatch(RetirementPolicy::isActive);
    }

    @Override
    public Optional<String> shouldRetire(Node node) {
        List<String> retirementReasons = retirementPolicies.stream()
                .filter(RetirementPolicy::isActive)
                .map(retirementPolicy -> retirementPolicy.shouldRetire(node))
                .flatMap(reason -> reason.map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toList());

        return retirementReasons.isEmpty() ? Optional.empty() :
                Optional.of("[" + String.join(", ", retirementReasons) + "]");
    }
}
