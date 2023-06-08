// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.QuotaExceededException;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;

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
public class QuotaValidator extends Validator {

    private static final Logger log = Logger.getLogger(QuotaValidator.class.getName());
    private static final Capacity zeroCapacity = Capacity.from(new ClusterResources(0, 0, NodeResources.zero()));

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        var quota = deployState.getProperties().quota();
        quota.maxClusterSize().ifPresent(maxClusterSize -> validateMaxClusterSize(maxClusterSize, model));
        quota.budgetAsDecimal().ifPresent(budget -> validateBudget(budget, model, deployState.getProperties().zone()));
    }

    private void validateBudget(BigDecimal budget, VespaModel model, Zone zone) {
        var maxSpend = model.allClusters().stream()
                .filter(id -> !adminClusterIds(model).contains(id))
                .map(id -> model.provisioned().all().getOrDefault(id, zeroCapacity))
                .mapToDouble(c -> c.maxResources().cost()) // TODO: This may be unspecified -> 0
                .sum();

        var actualSpend = model.allocatedHosts().getHosts().stream()
                         .filter(hostSpec -> hostSpec.membership().get().cluster().type() != ClusterSpec.Type.admin)
                         .mapToDouble(hostSpec -> hostSpec.advertisedResources().cost())
                         .sum();

        if (Math.abs(actualSpend) < 0.01) {
            log.warning("Deploying application " + model.applicationPackage().getApplicationId() + " with zero budget use.  This is suspicious, but not blocked");
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
        var invalidClusters = model.provisioned().all().entrySet().stream()
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
