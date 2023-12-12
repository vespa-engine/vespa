// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static com.yahoo.config.model.api.OnnxModelCost.ModelInfo;

/**
 * If Onnx models change in a way that requires restart of containers in
 * a container cluster this validator will make sure that restartOnDeploy is set for
 * configs for this cluster.
 *
 * @author hmusum
 */
public class RestartOnDeployForOnnxModelChangesValidator implements ChangeValidator {

    private static final Logger log = Logger.getLogger(RestartOnDeployForOnnxModelChangesValidator.class.getName());

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, DeployState deployState) {
        if ( ! deployState.featureFlags().restartOnDeployWhenOnnxModelChanges()) return List.of();
        List<ConfigChangeAction> actions = new ArrayList<>();

        // Compare onnx models used by each cluster and set restart on deploy for cluster if estimated cost,
        // model hash or model options have changed
        // TODO: Skip if container has enough memory to handle reload of onnx model (2 models in memory at the same time)
        for (var cluster : nextModel.getContainerClusters().values()) {
            var clusterInCurrentModel = currentModel.getContainerClusters().get(cluster.getName());
            if (clusterInCurrentModel == null) continue;

            var currentModels = clusterInCurrentModel.onnxModelCostCalculator().models();
            var nextModels = cluster.onnxModelCostCalculator().models();

            log.log(FINE, "Validating " + cluster + ", current models=" + currentModels + ", next models=" + nextModels);
            actions.addAll(validateModelChanges(cluster, currentModels, nextModels));
            actions.addAll(validateSetOfModels(cluster, currentModels, nextModels));
        }
        return actions;
    }

    private List<ConfigChangeAction> validateModelChanges(ApplicationContainerCluster cluster,
                                                          Map<String, ModelInfo> currentModels,
                                                          Map<String, ModelInfo> nextModels) {
        List<ConfigChangeAction> actions = new ArrayList<>();
        for (var nextModelInfo : nextModels.values()) {
            if (! currentModels.containsKey(nextModelInfo.modelId())) continue;

            log.log(FINE, "Checking if " + nextModelInfo + " has changed");
            modelChanged(nextModelInfo, currentModels.get(nextModelInfo.modelId())).ifPresent(change -> {
                String message = "Onnx model '%s' has changed (%s), need to restart services in %s"
                        .formatted(nextModelInfo.modelId(), change, cluster);
                cluster.onnxModelCostCalculator().setRestartOnDeploy();
                addRestartAction(actions, cluster, message);
            });
        }
        return actions;
    }

    private List<ConfigChangeAction> validateSetOfModels(ApplicationContainerCluster cluster,
                                                         Map<String, ModelInfo> currentModels,
                                                         Map<String, ModelInfo> nextModels) {
        List<ConfigChangeAction> actions = new ArrayList<>();
        Set<String> currentModelIds = currentModels.keySet();
        Set<String> nextModelIds = nextModels.keySet();
        log.log(FINE, "Checking if model set has changed (%s) -> (%s)".formatted(currentModelIds, nextModelIds));
        if (! currentModelIds.equals(nextModelIds)) {
            String message = "Onnx model set has changed from %s to %s, need to restart services in %s"
                    .formatted(currentModelIds, nextModelIds, cluster);
            addRestartAction(actions, cluster, message);
        }
        return actions;
    }

    private Optional<String> modelChanged(OnnxModelCost.ModelInfo a, OnnxModelCost.ModelInfo b) {
        if (a.estimatedCost() != b.estimatedCost()) return Optional.of("estimated cost");
        if (a.hash() != b.hash()) return Optional.of("model hash");
        if (a.onnxModelOptions().isPresent() && b.onnxModelOptions().isEmpty()) return Optional.of("model option(s)");
        if (a.onnxModelOptions().isEmpty() && b.onnxModelOptions().isPresent()) return Optional.of("model option(s)");
        if (a.onnxModelOptions().isPresent() && ! a.onnxModelOptions().get().equals(b.onnxModelOptions().get()))
            return Optional.of("model option(s)");
        return Optional.empty();
    }

    private static void addRestartAction(List<ConfigChangeAction> actions, ApplicationContainerCluster cluster, String message) {
        actions.add(new VespaRestartAction(cluster.id(), message));
    }

    private static boolean enoughMemoryToAvoidRestart(ApplicationContainerCluster cluster) {
        // Node memory is known so convert available memory percentage to node memory percentage
        double totalMemory = cluster.getContainers().get(0).getHostResource().realResources().memoryGb();
        double availableMemory = Math.max(0, totalMemory - Host.memoryOverheadGb);
        double costInGb = (double) cluster.onnxModelCostCalculator().aggregatedModelCostInBytes() / 1024 / 1024 / 1024;
        return ( 2 * costInGb < availableMemory);
    }

}
