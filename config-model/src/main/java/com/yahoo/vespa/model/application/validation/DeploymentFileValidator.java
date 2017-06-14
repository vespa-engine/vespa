// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;

import java.io.Reader;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that deployment file (deployment.xml) has valid values (for now
 * only global-service-id is validated)
 *
 * @author hmusum
 */
public class DeploymentFileValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        Optional<Reader> deployment = deployState.getApplicationPackage().getDeployment();

        if (deployment.isPresent()) {
            Reader deploymentReader = deployment.get();
            DeploymentSpec deploymentSpec = DeploymentSpec.fromXml(deploymentReader);
            final Optional<String> globalServiceId = deploymentSpec.globalServiceId();
            if (globalServiceId.isPresent()) {
                Set<ContainerCluster> containerClusters = model.getRoot().configModelRepo().getModels(ContainerModel.class).stream().
                        map(ContainerModel::getCluster).filter(cc -> cc.getName().equals(globalServiceId.get())).collect(Collectors.toSet());
                if (containerClusters.size() != 1) {
                    throw new IllegalArgumentException("global-service-id '" + globalServiceId.get() + "' specified in deployment.xml does not match any container cluster id");
                }
            }
        }
    }
}
