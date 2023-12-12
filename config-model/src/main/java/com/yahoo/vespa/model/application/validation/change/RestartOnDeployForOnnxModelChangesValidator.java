// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.DeployLogger;
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

import static com.yahoo.vespa.model.application.validation.JvmHeapSizeValidator.gbLimit;
import static com.yahoo.vespa.model.application.validation.JvmHeapSizeValidator.percentLimit;
import static java.util.logging.Level.FINE;
import static com.yahoo.config.model.api.OnnxModelCost.ModelInfo;
import static java.util.logging.Level.INFO;

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
        for (var cluster : nextModel.getContainerClusters().values()) {
            var clusterInCurrentModel = currentModel.getContainerClusters().get(cluster.getName());
            if (clusterInCurrentModel == null) continue;

            var currentModels = clusterInCurrentModel.onnxModelCostCalculator().models();
            var nextModels = cluster.onnxModelCostCalculator().models();

            if (enoughMemoryToAvoidRestart(clusterInCurrentModel, cluster, deployState.getDeployLogger()))
                continue;

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

            modelChanged(nextModelInfo, currentModels.get(nextModelInfo.modelId())).ifPresent(change -> {
                String message = "Onnx model '%s' has changed (%s), need to restart services in %s"
                        .formatted(nextModelInfo.modelId(), change, cluster);
                setRestartOnDeployAndAddRestartAction(actions, cluster, message);
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
            setRestartOnDeployAndAddRestartAction(actions, cluster, message);
        }
        return actions;
    }

    private Optional<String> modelChanged(OnnxModelCost.ModelInfo a, OnnxModelCost.ModelInfo b) {
        log.log(FINE, "Checking if model has changed (%s) -> (%s)".formatted(a, b));
        if (a.estimatedCost() != b.estimatedCost()) return Optional.of("estimated cost");
        if (a.hash() != b.hash()) return Optional.of("model hash");
        if (a.onnxModelOptions().isPresent() && b.onnxModelOptions().isEmpty()) return Optional.of("model option(s)");
        if (a.onnxModelOptions().isEmpty() && b.onnxModelOptions().isPresent()) return Optional.of("model option(s)");
        if (a.onnxModelOptions().isPresent() && ! a.onnxModelOptions().get().equals(b.onnxModelOptions().get()))
            return Optional.of("model option(s)");
        return Optional.empty();
    }

    private static void setRestartOnDeployAndAddRestartAction(List<ConfigChangeAction> actions, ApplicationContainerCluster cluster, String message) {
        log.log(INFO, message);
        cluster.onnxModelCostCalculator().setRestartOnDeploy();
        actions.add(new VespaRestartAction(cluster.id(), message));
    }

    private static boolean enoughMemoryToAvoidRestart(ApplicationContainerCluster clusterInCurrentModel,
                                                      ApplicationContainerCluster cluster,
                                                      DeployLogger deployLogger) {
        double currentModelCostInGb = onnxModelCostInGb(clusterInCurrentModel);
        double nextModelCostInGb = onnxModelCostInGb(cluster);

        double totalMemory = cluster.getContainers().get(0).getHostResource().realResources().memoryGb();
        double availableMemory = Math.max(0, totalMemory - Host.memoryOverheadGb - currentModelCostInGb - currentModelCostInGb);
        if (availableMemory <= 0.0)
            return false;

        var availableMemoryPercentage = cluster.availableMemoryPercentage();
        int memoryPercentage = (int) (availableMemory / totalMemory * availableMemoryPercentage);

        if (memoryPercentage < percentLimit || availableMemory < gbLimit) {
            deployLogger.log(INFO, "Validating %s, not enough memory (%s) to avoid restart (models require %s), consider a flavor with more memory to avoid this"
                    .formatted(cluster, availableMemory, currentModelCostInGb + nextModelCostInGb));
            return false;
        }

        log.log(FINE, "Validating " + cluster + ", enough memory (%s) to avoid restart (models require %s)"
                .formatted(availableMemory, currentModelCostInGb + nextModelCostInGb));
        return true;
    }

    private static double onnxModelCostInGb(ApplicationContainerCluster clusterInCurrentModel) {
        return (double) clusterInCurrentModel.onnxModelCostCalculator().aggregatedModelCostInBytes() / 1024 / 1024 / 1024;
    }

}
