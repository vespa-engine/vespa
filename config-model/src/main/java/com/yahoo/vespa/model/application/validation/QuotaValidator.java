// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Checks that the generated model does not have resources that exceeds the given quota.
 *
 * @author ogronnesby
 */
public class QuotaValidator extends Validator {
    @Override
    public void validate(VespaModel model, DeployState deployState) {
        var quota = deployState.getProperties().quota();
        quota.maxClusterSize().ifPresent(maxClusterSize -> validateMaxClusterSize(maxClusterSize, model));
        quota.budgetAsDecimal().ifPresent(budget -> validateBudget(budget, model));
    }

    private void validateBudget(BigDecimal budget, VespaModel model) {
        var spend = model.allClusters().stream()
                .map(clusterId -> model.provisioned().all().get(clusterId))
                .map(Capacity::maxResources)
                .map(clusterCapacity -> clusterCapacity.nodeResources().cost() * clusterCapacity.nodes())
                .reduce(0.0, Double::sum);

        if (budget.doubleValue() < spend) {
            throwBudgetExceeded(spend, budget);
        }
    }

    /** Check that all clusters in the application do not exceed the quota max cluster size. */
    private void validateMaxClusterSize(int maxClusterSize, VespaModel model) {
        var invalidClusters = model.allClusters().stream()
                .filter(clusterId -> {
                    var cluster = model.provisioned().all().get(clusterId);
                    var clusterSize = cluster.maxResources().nodes();
                    return clusterSize > maxClusterSize;
                })
                .map(ClusterSpec.Id::value)
                .collect(Collectors.toList());

        if (!invalidClusters.isEmpty()) {
            var clusterNames = String.join(", ", invalidClusters);
            throw new IllegalArgumentException("Clusters " + clusterNames + " exceeded max cluster size of " + maxClusterSize);
        }
    }

    private void throwBudgetExceeded(double spend, BigDecimal budget) {
        var message = String.format(Locale.US, "Hourly spend for maximum specified resources ($%.2f) exceeds budget from quota ($%.2f)!", spend, budget);
        throw new IllegalArgumentException(message);
    }
}
