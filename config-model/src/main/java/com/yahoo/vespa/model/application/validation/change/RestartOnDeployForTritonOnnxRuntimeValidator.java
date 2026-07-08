// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeRestartAction;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModelEvaluation;

import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.ConfigChangeRestartAction.ConfigChange.DEFER_UNTIL_RESTART;

/**
 * Ensures that application container clusters are restarted when Triton ONNX Runtime component is added or removed.
 *
 * @author glebashnik
 */
public class RestartOnDeployForTritonOnnxRuntimeValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        var previousClusters = getContainerClusters(context.previousModel());
        var currentClusters = getContainerClusters(context.model());

        for (var entry : currentClusters.entrySet()) {
            var clusterId = entry.getKey();
            var currentCluster = entry.getValue();

            var previousCluster = previousClusters.get(clusterId);
            if (previousCluster == null) {
                continue;
            }

            var hadTritonRuntime = hasTritonOnnxRuntime(previousCluster);
            var hasTritonRuntime = hasTritonOnnxRuntime(currentCluster);

            if (hadTritonRuntime != hasTritonRuntime) {
                var message = hasTritonRuntime
                        ? "Triton ONNX runtime was enabled for cluster '%s', services require restart"
                        : "Triton ONNX runtime was disabled for cluster '%s', services require restart";
                var action = createRestartAction(currentCluster, Text.format(message, clusterId), DEFER_UNTIL_RESTART);
                context.require(action);
            }
        }
    }

    private static Map<ClusterSpec.Id, ApplicationContainerCluster> getContainerClusters(VespaModel model) {
        return model.getContainerClusters().values().stream()
                .collect(Collectors.toMap(ApplicationContainerCluster::id, cluster -> cluster));
    }

    private static boolean hasTritonOnnxRuntime(ApplicationContainerCluster cluster) {
        return cluster.getAllComponents().stream()
                .anyMatch(component ->
                        component.getClassId().getName().equals(ContainerModelEvaluation.TRITON_ONNX_RUNTIME_CLASS));
    }

    private static VespaRestartAction createRestartAction(
            ApplicationContainerCluster cluster, String message, ConfigChangeRestartAction.ConfigChange configChange) {
        var services = cluster.getContainers().stream()
                .map(AbstractService::getServiceInfo)
                .toList();
        return new VespaRestartAction(cluster.id(), message, services, configChange);
    }
}
