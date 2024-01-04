// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.application.validation.change.ChangeValidator;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.stream.Stream;

/**
 * Validates that applications in prod zones do not have redundancy 1 (without a validation override).
 *
 * @author bratseth
 */
public class RedundancyValidator implements Validator, ChangeValidator {

    /** Validate on first deployment. */
    @Override
    public void validate(Context context) {
        if ( ! shouldValidate(context.deployState())) return;
        clustersWithRedundancyOne(context.model()).forEach(cluster -> invalidRedundancy(cluster, context));
    }

    /** Validate on change. */
    @Override
    public void validate(ChangeContext context) {
        if ( ! shouldValidate(context.deployState())) return;

        clustersWithRedundancyOne(context.model())
                .filter(cluster -> ! hasRedundancyOne(context.previousModel().getContentClusters().get(cluster.id().value())))
                .forEach(cluster -> invalidRedundancy(cluster, context));
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

    private void invalidRedundancy(ContentCluster cluster, Context context) {
        context.invalid(ValidationId.redundancyOne,
                        cluster + " has redundancy 1, which will cause it to lose data " +
                        "if a node fails. This requires an override on first deployment " +
                        "in a production zone");
    }

}
