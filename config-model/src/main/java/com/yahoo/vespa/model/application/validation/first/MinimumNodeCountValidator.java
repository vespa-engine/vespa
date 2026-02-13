// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.stream.Stream;

/**
 * Validates that applications in prod zones have at least 2 nodes in each cluster (without a validation override).
 *
 * @author hmusum
 */
public class MinimumNodeCountValidator implements Validator {

    private static final int MINIMUM_NODE_COUNT = 2;

    /** Validate on first deployment. */
    @Override
    public void validate(Context context) {
        if ( ! shouldValidate(context.deployState())) return;

        clustersWithTooFewNodes(context.model()).forEach(clusterName ->
            invalidNodeCount(clusterName, context));
    }

    private boolean shouldValidate(DeployState deployState) {
        return deployState.isHosted() && deployState.zone().environment().isProduction();
    }

    private Stream<String> clustersWithTooFewNodes(VespaModel model) {
        Stream<String> contentClusters = model.getContentClusters().values().stream()
                .filter(this::hasTooFewNodes)
                .map(cluster -> "content cluster '" + cluster.id().value() + "'");

        Stream<String> containerClusters = model.getContainerClusters().values().stream()
                .filter(this::hasTooFewNodes)
                .map(cluster -> "container cluster '" + cluster.id().value() + "'");

        return Stream.concat(contentClusters, containerClusters);
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
