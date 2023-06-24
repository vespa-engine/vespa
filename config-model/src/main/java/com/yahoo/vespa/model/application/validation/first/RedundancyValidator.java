// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.application.validation.change.ChangeValidator;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.List;
import java.util.stream.Stream;

/**
 * Validates that applications in prod zones do not have redundancy 1 (without a validation override).
 *
 * @author bratseth
 */
public class RedundancyValidator extends Validator implements ChangeValidator {

    /** Validate on first deployment. */
    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if ( ! shouldValidate(deployState)) return;
        clustersWithRedundancyOne(model).forEach(cluster -> invalidRedundancy(cluster, deployState));
    }

    /** Validate on change. */
    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, DeployState deployState) {
        if ( ! shouldValidate(deployState)) return List.of();

        clustersWithRedundancyOne(next)
                .filter(cluster -> ! hasRedundancyOne(current.getContentClusters().get(cluster.id().value())))
                .forEach(cluster -> invalidRedundancy(cluster, deployState));
        return List.of();
    }

    private boolean shouldValidate(DeployState deployState) {
        return deployState.isHosted() && deployState.zone().environment().isProduction();
    }

    private Stream<ContentCluster> clustersWithRedundancyOne(VespaModel model) {
        return model.getContentClusters().values().stream().filter(cluster -> hasRedundancyOne(cluster));
    }

    private boolean hasRedundancyOne(ContentCluster cluster) {
        return cluster != null && cluster.getRedundancy().finalRedundancy() == 1 && cluster.getRedundancy().groups() == 1;
    }

    private void invalidRedundancy(ContentCluster cluster, DeployState deployState) {
        deployState.validationOverrides().invalid(ValidationId.redundancyOne,
                                                  cluster + " has redundancy 1, which will cause it to lose data " +
                                                  "if a node fails. This requires an override on first deployment " +
                                                  "in a production zone",
                                                  deployState.now());
    }

}
