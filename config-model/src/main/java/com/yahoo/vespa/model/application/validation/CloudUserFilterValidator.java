// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.http.HttpFilterChain;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Validates that only allowed-listed cloud applications can set up user-specified filter chains
 *
 * @author bjorncs
 */
public class CloudUserFilterValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState state) {
        if (!state.isHostedTenantApplication(model.getAdmin().getApplicationType())) return;
        if (state.getProperties().allowUserFilters()) return;
        var violations   = new TreeSet<Violation>();
        for (var cluster : model.getContainerClusters().values()) {
            if (cluster.getHttp() == null) continue;
            for (var chain : cluster.getHttp().getFilterChains().allChains().allComponents()) {
                if (chain.type() == HttpFilterChain.Type.USER) {
                    var msg = "Found filter chain violation - chain '%s' in cluster '%s'".formatted(cluster.name(), chain.id());
                    state.getDeployLogger().log(Level.WARNING, msg);
                    violations.add(new Violation(cluster.name(), chain.id()));
                }
            }
        }
        if (violations.isEmpty()) return;
        var violationsStr = violations.stream()
                .map(v -> "chain '%s' in cluster '%s'".formatted(v.chain(), v.cluster()))
                .collect(Collectors.joining(", ", "[", "]"));
        var msg = ("HTTP filter chains are currently not supported in Vespa Cloud (%s)").formatted(violationsStr);
        throw new IllegalArgumentException(msg);
    }

    private record Violation(String cluster, String chain) implements Comparable<Violation> {
        @Override
        public int compareTo(Violation other) {
            return Comparator.comparing(Violation::chain).thenComparing(Violation::cluster).compare(this, other);
        }
    }

}
