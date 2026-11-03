// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.utils.internal.ReflectionUtil;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModelEvaluation;
import com.yahoo.vespa.model.container.component.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.ConfigChangeRestartAction.ConfigChange.DEFER_UNTIL_RESTART;

/**
 * Ensures that application container clusters are restarted when the TritonOnnx Runtime component
 * is added or removed, or when its config changes.
 * TritonOnnxRuntime holds state shared across reconfigurations,
 * which is only correct when its config never changes while the container serves.
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

            var previousRuntime = getTritonOnnxRuntime(previousCluster);
            var currentRuntime = getTritonOnnxRuntime(currentCluster);

            if (previousRuntime.isPresent() != currentRuntime.isPresent()) {
                var message = currentRuntime.isPresent()
                        ? "Triton ONNX runtime was enabled for cluster '%s', services require restart"
                        : "Triton ONNX runtime was disabled for cluster '%s', services require restart";
                var action = VespaRestartAction.ofCluster(currentCluster, Text.format(message, clusterId), DEFER_UNTIL_RESTART);
                context.require(action);
            } else if (currentRuntime.isPresent()) {
                // Applies to self-hosted as well: without the restart, a config change would
                // create runtimes with independent reference counts for the same Triton models.
                var previousConfig = context.previousModel().getConfig(
                        TritonConfig.class, previousRuntime.get().getConfigId());
                var currentConfig = context.model().getConfig(
                        TritonConfig.class, currentRuntime.get().getConfigId());
                var changes = ReflectionUtil.getChangesRequiringRestart(previousConfig, currentConfig);

                if (changes.needsRestart()) {
                    var message = Text.format(
                            "Triton config changed for cluster '%s', services require restart:\n%s",
                            clusterId, changes.toString(""));
                    var action = VespaRestartAction.ofCluster(currentCluster, message, DEFER_UNTIL_RESTART);
                    context.require(action);
                }
            }
        }
    }

    private static Map<ClusterSpec.Id, ApplicationContainerCluster> getContainerClusters(VespaModel model) {
        return model.getContainerClusters().values().stream()
                .collect(Collectors.toMap(ApplicationContainerCluster::id, cluster -> cluster));
    }

    private static Optional<Component<?, ?>> getTritonOnnxRuntime(ApplicationContainerCluster cluster) {
        return cluster.getAllComponents().stream()
                .filter(component ->
                        component.getClassId().getName().equals(ContainerModelEvaluation.TRITON_ONNX_RUNTIME_CLASS))
                .findFirst();
    }
}
