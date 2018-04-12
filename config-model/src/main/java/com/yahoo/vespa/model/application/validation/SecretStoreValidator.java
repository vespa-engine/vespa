// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.IdentityProvider;
import com.yahoo.vespa.model.container.component.Component;

/**
 * Validates the requirements for setting up a secret store.
 *
 * @author gjoranv
 */
public class SecretStoreValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if (! deployState.isHosted()) return;
        if (model.getAdmin().getApplicationType() != ApplicationType.DEFAULT) return;

        for (ContainerCluster cluster : model.getContainerClusters().values()) {
            if (cluster.getSecretStore().isPresent() && ! hasIdentityProvider(cluster)) {
                throw new IllegalArgumentException(String.format(
                        "Container cluster '%s' uses a secret store, so an Athenz domain and an Athenz service" +
                                " must be declared in deployment.xml.", cluster.getName()));
            }
        }
    }

    private boolean hasIdentityProvider(ContainerCluster cluster) {
        for (Component<?, ?> component : cluster.getAllComponents()) {
            if (component instanceof IdentityProvider) return true;
        }
        return false;
    }
}
