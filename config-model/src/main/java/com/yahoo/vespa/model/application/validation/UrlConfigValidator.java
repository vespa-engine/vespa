// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

/**
 * Validates that config using s3:// urls is used in public system and with nodes that are exclusive.
 *
 * @author hmusum
 */
public class UrlConfigValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState state) {
        if (! state.isHostedTenantApplication(model.getAdmin().getApplicationType())) return;

        model.getContainerClusters().forEach((__, cluster) -> {
            var isExclusive = hasExclusiveNodes(model, cluster);
            validateS3UlsInConfig(state, cluster, isExclusive);
        });
    }

    private static boolean hasExclusiveNodes(VespaModel model, ApplicationContainerCluster cluster) {
        return model.hostSystem().getHosts()
                .stream()
                .flatMap(hostResource -> hostResource.spec().membership().stream())
                .filter(membership -> membership.cluster().id().equals(cluster.id()))
                .anyMatch(membership -> membership.cluster().isExclusive());
    }

    private static void validateS3UlsInConfig(DeployState state, ApplicationContainerCluster cluster, boolean isExclusive) {
        if (hasS3UrlInConfig(cluster)) {
            // TODO: Would be even better if we could add which config/field the url is set for in the error message
            String message = "Found s3:// urls in config for container cluster " + cluster.getName();
            if ( ! state.zone().system().isPublic())
                throw new IllegalArgumentException(message + ". This is only supported in public systems");
            else if ( ! isExclusive)
                throw new IllegalArgumentException(message + ". Nodes in the cluster need to be 'exclusive'," +
                                                           " see https://cloud.vespa.ai/en/reference/services#nodes");
        }
    }

    private static boolean hasS3UrlInConfig(ApplicationContainerCluster cluster) {
        return cluster.userConfiguredUrls().all().stream()
                .anyMatch(url -> url.startsWith("s3://"));
    }

}
