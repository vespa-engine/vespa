// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CapacityPolicies;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Exclusivity;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.QuotaExceededException;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.Context;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Checks that the generated model does not have resources that exceeds the given quota.
 *
 * @author ogronnesby
 */
public class QuotaValidator implements Validator {

    private static final Logger log = Logger.getLogger(QuotaValidator.class.getName());
    private static final Capacity zeroCapacity = Capacity.from(new ClusterResources(0, 0, NodeResources.zero()));

    @Override
    public void validate(Context context) {
        var zone = context.deployState().zone();
        var exclusivity = new Exclusivity(zone, context.deployState().featureFlags().sharedHosts());
        var capacityPolicies = new CapacityPolicies(zone, exclusivity, context.model().applicationPackage().getApplicationId(),
                                                    context.deployState().featureFlags().adminClusterArchitecture());
        var quota = context.deployState().getProperties().quota();
        quota.maxClusterSize().ifPresent(maxClusterSize -> validateMaxClusterSize(maxClusterSize, context.model()));
        quota.budgetAsDecimal().ifPresent(budget -> validateBudget(budget, context, capacityPolicies));
    }

    private void validateBudget(BigDecimal budget, Context context,
                                CapacityPolicies capacityPolicies) {
        var zone = context.deployState().getProperties().zone();
        var application = context.model().applicationPackage().getApplicationId();

        var maxSpend = 0.0;
        for (var id : context.model().allClusters()) {
            if (adminClusterIds(context.model()).contains(id)) continue;
            var cluster = context.model().provisioned().clusters().get(id);
            var capacity = context.model().provisioned().capacities().getOrDefault(id, zeroCapacity);
            maxSpend += capacityPolicies.applyOn(capacity, cluster.isExclusive()).maxResources().cost();
        }

        var actualSpend = context.model().allocatedHosts().getHosts().stream()
                         .filter(hostSpec -> hostSpec.membership().get().cluster().type() != ClusterSpec.Type.admin)
                         .mapToDouble(hostSpec -> hostSpec.advertisedResources().cost())
                         .sum();

        if (Math.abs(actualSpend) < 0.01) {
            log.warning("Deploying application " + application + " with zero budget use.  This is suspicious, but not blocked");
            return;
        }

        throwIfBudgetNegative(actualSpend, budget, zone.system());
        throwIfBudgetExceeded(actualSpend, budget, zone.system(), true);
        if ( ! zone.environment().isTest()) // Usage is constant after deploy in test zones
            throwIfBudgetExceeded(maxSpend, budget, zone.system(), false);
    }

    private Set<ClusterSpec.Id> adminClusterIds(VespaModel model) {
        return model.allocatedHosts().getHosts().stream()
                .map(hostSpec -> hostSpec.membership().orElseThrow().cluster())
                .filter(cluster -> cluster.type() == ClusterSpec.Type.admin)
                .map(ClusterSpec::id)
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

    /** Check that all clusters in the application do not exceed the quota max cluster size. */
    private void validateMaxClusterSize(int maxClusterSize, VespaModel model) {
        var invalidClusters = model.provisioned().capacities().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> {
                    var cluster = entry.getValue();
                    var clusterSize = cluster.maxResources().nodes();
                    return clusterSize > maxClusterSize;
                })
                .map(Map.Entry::getKey)
                .map(ClusterSpec.Id::value)
                .toList();

        if (!invalidClusters.isEmpty()) {
            var clusterNames = String.join(", ", invalidClusters);
            throw new QuotaExceededException("Clusters " + clusterNames + " exceeded max cluster size of " + maxClusterSize);
        }
    }

    private static void throwIfBudgetNegative(double spend, BigDecimal budget, SystemName systemName) {
        if (budget.doubleValue() < 0) {
            throw new QuotaExceededException(quotaMessage("Please free up some capacity.", systemName, spend, budget, true));
        }
    }

    private static void throwIfBudgetExceeded(double spend, BigDecimal budget, SystemName systemName, boolean actual) {
        if (budget.doubleValue() < spend) {
            throw new QuotaExceededException(quotaMessage("Contact support to upgrade your plan.", systemName, spend, budget, actual));
        }
    }

    private static String quotaMessage(String message, SystemName system, double spend, BigDecimal budget, boolean actual) {
        String quotaDescription = String.format(Locale.ENGLISH,
                                                "The %s cost $%.2f but your quota is $%.2f",
                                                actual ? "resources used" : "max resources specified",
                                                spend,
                                                budget);
        return (system == SystemName.Public ? "" : system.value() + ": ") + quotaDescription + ": " + message;
    }

}
