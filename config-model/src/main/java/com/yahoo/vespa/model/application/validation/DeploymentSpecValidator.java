// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerModel;

import java.io.Reader;
import java.util.List;
import java.util.Optional;

/**
 * Validates that deployment spec (deployment.xml) has valid values (for now
 * only global-service-id is validated)
 *
 * @author hmusum
 * @author bratseth
 */
public class DeploymentSpecValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        Optional<Reader> deployment = deployState.getApplicationPackage().getDeployment();
        if ( deployment.isEmpty()) return;

        Reader deploymentReader = deployment.get();
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml(deploymentReader);
        List<ContainerModel> containers = model.getRoot().configModelRepo().getModels(ContainerModel.class);
        for (DeploymentInstanceSpec instance : deploymentSpec.instances()) {
            instance.globalServiceId().ifPresent(globalServiceId -> {
                if ( containers.stream().noneMatch(container -> container.getCluster().getName().equals(globalServiceId)))
                    throw new IllegalArgumentException("The global-service-id in " + instance + ", '" + globalServiceId +
                                                       "' specified in deployment.xml does not match any container cluster id");
            });
        }
    }

}
