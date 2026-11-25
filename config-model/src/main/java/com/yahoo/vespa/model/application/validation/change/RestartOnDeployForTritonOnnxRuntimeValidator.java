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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.ConfigChangeRestartAction.ConfigChange.DEFER_UNTIL_RESTART;

/**
 * Ensures that application container clusters are restarted when a Triton runtime component (TritonOnnxRuntime or
 * TritonLlamacppRuntime) is added or removed, or when their config changes.
 * The runtimes hold state shared across reconfigurations, which is only correct when their config never changes
 * while the container serves. Adding or removing one also changes the capabilities the cluster asks of the Triton
 * sidecar, e.g. a Triton image with llama.cpp, which the sidecar only gets when the nodes restart.
 *
 * @author glebashnik
 */
public class RestartOnDeployForTritonOnnxRuntimeValidator implements ChangeValidator {

    // The Triton runtime components, by class name, with the name used in restart messages.
    private static final Map<String, String> RUNTIMES = new LinkedHashMap<>();
    static {
        RUNTIMES.put(ContainerModelEvaluation.TRITON_ONNX_RUNTIME_CLASS, "ONNX");
        RUNTIMES.put(ContainerModelEvaluation.TRITON_LLAMACPP_RUNTIME_CLASS, "llama.cpp");
    }

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

            boolean runtimeAddedOrRemoved = false;
            for (var runtime : RUNTIMES.entrySet()) {
                var wasPresent = getComponent(previousCluster, runtime.getKey()).isPresent();
                var isPresent = getComponent(currentCluster, runtime.getKey()).isPresent();
                if (wasPresent == isPresent) continue;

                var message = isPresent
                        ? "Triton %s runtime was enabled for cluster '%s', services require restart"
                        : "Triton %s runtime was disabled for cluster '%s', services require restart";
                var action = VespaRestartAction.ofCluster(currentCluster, Text.format(message, runtime.getValue(), clusterId), DEFER_UNTIL_RESTART);
                context.require(action);
                runtimeAddedOrRemoved = true;
            }
            // Both runtimes get the cluster's Triton config, so comparing that of one remaining runtime covers both.
            var previousRuntime = getAnyTritonRuntime(previousCluster);
            var currentRuntime = getAnyTritonRuntime(currentCluster);
            if ( ! runtimeAddedOrRemoved && previousRuntime.isPresent() && currentRuntime.isPresent()) {
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

    private static Optional<Component<?, ?>> getComponent(ApplicationContainerCluster cluster, String className) {
        return cluster.getAllComponents().stream()
                .filter(component -> component.getClassId().getName().equals(className))
                .findFirst();
    }

    private static Optional<Component<?, ?>> getAnyTritonRuntime(ApplicationContainerCluster cluster) {
        return RUNTIMES.keySet().stream()
                .map(className -> getComponent(cluster, className))
                .flatMap(Optional::stream)
                .findFirst();
    }
}
