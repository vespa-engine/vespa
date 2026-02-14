// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.application.validation.change.ChangeValidator;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Validates that applications in prod zones have at least 2 nodes in each cluster (without a validation override).
 *
 * @author hmusum
 */
public class MinimumNodeCountValidator implements Validator, ChangeValidator {

    private static final int MINIMUM_NODE_COUNT = 2;

    /** Validate on first deployment. */
    @Override
    public void validate(Context context) {
        if ( ! shouldValidate(context.deployState())) return;

        validateContentClusters(context.model(), null, context);
        validateContainerClusters(context.model(), null, context);
    }

    /** Validate on change. */
    @Override
    public void validate(ChangeContext context) {
        if ( ! shouldValidate(context.deployState())) return;

        validateContentClusters(context.model(), context.previousModel(), context);
        validateContainerClusters(context.model(), context.previousModel(), context);
    }

    private boolean shouldValidate(DeployState deployState) {
        return deployState.isHosted() && deployState.zone().environment().isProduction();
    }

    private void validateContentClusters(VespaModel model, VespaModel previousModel, Context context) {
        model.getContentClusters().values().stream()
                .filter(this::hasTooFewNodes)
                .filter(cluster -> previousModel == null || ! hasTooFewNodes(previousModel.getContentClusters().get(cluster.id().value())))
                .forEach(cluster -> invalidNodeCount("content cluster '" + cluster.id().value() + "'", context));
    }

    private void validateContainerClusters(VespaModel model, VespaModel previousModel, Context context) {
        model.getContainerClusters().values().stream()
                .filter(this::hasTooFewNodes)
                .filter(cluster -> previousModel == null || ! hasTooFewNodes(previousModel.getContainerClusters().get(cluster.id().value())))
                .forEach(cluster -> invalidNodeCount("container cluster '" + cluster.id().value() + "'", context));
    }

    private boolean hasTooFewNodes(ContentCluster cluster) {
        if (cluster == null) return false;
        return cluster.getRootGroup().countNodes(false) < MINIMUM_NODE_COUNT;
    }

    private boolean hasTooFewNodes(ApplicationContainerCluster cluster) {
        if (cluster == null) return false;
        return cluster.getContainers().size() < MINIMUM_NODE_COUNT;
    }

    private void invalidNodeCount(String clusterName, Context context) {
        context.invalid(ValidationId.minimumNodeCount,
                        clusterName + " has less than " + MINIMUM_NODE_COUNT + " nodes, " +
                        "which may cause service disruption during node failures or upgrades. " +
                        "This requires an override on first deployment in a production zone");
    }

}
