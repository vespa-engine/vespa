// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.container.ContainerModel;

import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validate deployment spec (deployment.xml).
 *
 * @author hmusum
 * @author bratseth
 */
public class DeploymentSpecValidator implements Validator {

    @Override
    public void validate(Context context) {
        Optional<Reader> deployment = context.deployState().getApplicationPackage().getDeployment();
        if ( deployment.isEmpty()) return;

        Reader deploymentReader = deployment.get();
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml(deploymentReader);
        List<ContainerModel> containers = context.model().getRoot().configModelRepo().getModels(ContainerModel.class);
        requireUniqueInstanceIds(context, deploymentSpec.instances());
        for (DeploymentInstanceSpec instance : deploymentSpec.instances()) {
            instance.endpoints().forEach(endpoint -> {
                requireClusterId(context, containers, instance.name(),
                                 "Endpoint '" + endpoint.endpointId() + "'", endpoint.containerId());
            });
        }
    }

    private static void requireClusterId(Context context, List<ContainerModel> containers, InstanceName instanceName,
                                         String endpoint, String id) {
        if (containers.stream().noneMatch(container -> container.getCluster().getName().equals(id)))
            context.illegal(endpoint + " in instance " + instanceName + ": '" + id +
                            "' specified in deployment.xml does not match any container cluster ID");
    }

    private static void requireUniqueInstanceIds(Context context, List<DeploymentInstanceSpec> instances) {
        Set<InstanceName> instanceNames = new HashSet<>();
        for (var instance : instances) {
            if ( ! instanceNames.add(instance.name()))
                context.illegal("Duplicate instance name '" + instance.name() + "' specified in deployment.xml.");
        }
    }

}
