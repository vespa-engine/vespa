// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.container.http.HttpFilterChain;

import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

/**
 * Validates that only allowed-listed cloud applications can set up user-specified filter chains
 *
 * @author bjorncs
 */
public class CloudUserFilterValidator implements Validator {

    @Override
    public void validate(Context context) {
        if (!context.deployState().isHostedTenantApplication(context.model().getAdmin().getApplicationType())) return;
        if (context.deployState().getProperties().allowUserFilters()) return;
        record Violation(String cluster, String chain) { }
        var violations = new TreeSet<Violation>(comparing(Violation::chain).thenComparing(Violation::cluster));
        for (var cluster : context.model().getContainerClusters().values()) {
            if (cluster.getHttp() == null) continue;
            for (var chain : cluster.getHttp().getFilterChains().allChains().allComponents()) {
                if (chain.type() == HttpFilterChain.Type.USER) {
                    var msg = "Found filter chain violation - chain '%s' in cluster '%s'".formatted(cluster.name(), chain.id());
                    context.deployState().getDeployLogger().log(Level.WARNING, msg);
                    violations.add(new Violation(cluster.name(), chain.id()));
                }
            }
        }
        if (violations.isEmpty()) return;
        var violationsStr = violations.stream()
                .map(v -> "chain '%s' in cluster '%s'".formatted(v.chain(), v.cluster()))
                .collect(Collectors.joining(", ", "[", "]"));
        var msg = ("HTTP filter chains are currently not supported in Vespa Cloud (%s)").formatted(violationsStr);
        context.illegal(msg);
    }

}
