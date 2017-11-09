// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.Identity;

import java.util.Map;

/**
 * Validates that the identity name starts with tenant name to ensure
 * ownership of the service, i.e. the tenant is authorized to launch the service
 *
 * @author mortent
 */
public class IdentityValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        Map<String, ContainerCluster> containerClusters = model.getContainerClusters();

        containerClusters.values()
                .forEach(cluster -> validateCluster(cluster, deployState.getProperties().applicationId()));
    }

    private void validateCluster(ContainerCluster cluster, ApplicationId applicationId) {
        Identity identity = (Identity) cluster.getComponentsMap().get(ComponentId.fromString(Identity.CLASS));

        if (identity != null) {
            if (!identity.getService().startsWith(applicationId.tenant().value())) {
                throw new IllegalArgumentException(
                        String.format("Invalid service name [%1$s] for tenant [%2$s]. Service must start with tenant name, e.g: %2$s.%1$s",
                                      identity.getService(), applicationId.tenant().value()));
            }
        }
    }
}
